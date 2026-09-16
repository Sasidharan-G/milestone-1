import { Router } from 'express';
import crypto from 'node:crypto';
import { AppError } from '../core/errors';
import { normalizePhone, verifyResetToken } from '../controllers/otpController';
import { requireAuth, AuthenticatedRequest } from '../middleware/authMiddleware';
import { limitOtpVerify } from '../middleware/rateLimitMiddleware';
import { providers } from '../providers/providerRegistry';
import { activePermissions } from '../providers/local/localDataStore';
import { sendRouteError } from './http';

const router = Router();

export { activePermissions };

export function validAccountInput(phone: unknown, name: unknown, password: unknown): boolean {
  return typeof phone === 'string' && /^[6-9][0-9]{9}$/.test(normalizePhone(phone)) &&
    typeof name === 'string' && name.trim().length >= 1 && name.length <= 120 &&
    typeof password === 'string' && password.length >= 6 && password.length <= 256;
}

const resetProofValid = (phone: string, resetToken: unknown): boolean => {
  if (process.env.NODE_ENV === 'development' && process.env.LOCAL_DEV_OTP_BYPASS === 'true' && resetToken === 'local-dev-verified') return true;
  return typeof resetToken === 'string' && verifyResetToken(phone, resetToken);
};

router.post('/auth/register', limitOtpVerify, async (req, res) => {
  try {
    const { mobileNumber, ownerName, businessName, password, resetToken } = req.body || {};
    if (!validAccountInput(mobileNumber, ownerName, password) || typeof businessName !== 'string' || !businessName.trim() || businessName.length > 160) {
      throw new AppError(400, 'ACCOUNT_INPUT_INVALID', 'Enter a valid mobile, owner, business, and password');
    }
    const phone = normalizePhone(mobileNumber);
    if (!resetProofValid(phone, resetToken)) throw new AppError(401, 'OTP_PROOF_INVALID', 'Verify a fresh OTP before registration');
    const proof = crypto.createHash('sha256').update(String(resetToken)).digest('hex');
    if (!await providers().dataStore.consumeNonce('registration', `${phone}:${proof}`, Date.now() + 86_400_000)) {
      throw new AppError(409, 'OTP_PROOF_USED', 'OTP proof has already been used');
    }
    const result = await providers().dataStore.createAccount({ phone, displayName: ownerName.trim(), businessName: businessName.trim(), password });
    try { await providers().identityProvider.setPassword(result.user.userId, password); }
    catch (error) {
      await providers().identityProvider.deleteUser(result.user.userId).catch(() => undefined);
      await providers().dataStore.deleteCompany(result.user.companyId).catch(() => undefined);
      throw error;
    }
    return res.status(201).json({ success: true, companyId: result.user.companyId, user: result.user, license: result.license });
  } catch (error) { return sendRouteError(res, req, error); }
});

router.post('/auth/login', async (req, res) => {
  try {
    const { username, password } = req.body || {};
    if (!username || !password) throw new AppError(400, 'AUTH_INPUT_REQUIRED', 'Username/mobile and password are required');
    const phone = normalizePhone(username);
    const result = await providers().identityProvider.authenticate(phone, password);
    const license = await providers().dataStore.getLicense(result.user.companyId);
    return res.json({
      success: true,
      user: result.user,
      tokens: result.tokens,
      license
    });
  } catch (error) { return sendRouteError(res, req, error); }
});

export const validMasterLoginInput = (_mobileNumber: unknown, pin: unknown): boolean =>
  typeof pin === 'string' && /^\d{6,12}$/.test(pin);

router.post('/auth/master/login', async (req, res) => {
  try {
    const { pin } = req.body || {};
    if (!validMasterLoginInput(undefined, pin)) {
      throw new AppError(400, 'MASTER_LOGIN_INPUT_INVALID', 'A 6-12 digit PIN is required');
    }
    const mobileNumber = (await providers().dataStore.getMasterConfig()).mobile;
    const result = await providers().identityProvider.authenticatePlatform(mobileNumber, pin);
    return res.json({ success: true, mobile: result.mobile, tokens: result.tokens });
  } catch (error) { return sendRouteError(res, req, error); }
});

router.post('/auth/refresh', async (req, res) => {
  try {
    const { refreshToken } = req.body || {};
    if (!refreshToken) throw new AppError(400, 'AUTH_TOKEN_REQUIRED', 'Refresh token is required');
    const result = await providers().identityProvider.refresh(refreshToken);
    return res.json({
      success: true,
      user: result.user,
      tokens: result.tokens
    });
  } catch (error) { return sendRouteError(res, req, error); }
});

router.post('/auth/password/reset', limitOtpVerify, async (req, res) => {
  try {
    const { mobileNumber, password, resetToken } = req.body || {};
    const phone = normalizePhone(mobileNumber);
    if (!/^[6-9][0-9]{9}$/.test(phone) || typeof password !== 'string' || password.length < 8 || password.length > 256) {
      throw new AppError(400, 'AUTH_RESET_INPUT_INVALID', 'A valid mobile number and password are required');
    }
    if (!resetProofValid(phone, resetToken)) throw new AppError(401, 'OTP_PROOF_INVALID', 'Verify a fresh OTP before resetting the password');
    const proof = crypto.createHash('sha256').update(String(resetToken)).digest('hex');
    if (!await providers().dataStore.consumeNonce('password-reset', `${phone}:${proof}`, Date.now() + 86_400_000)) {
      throw new AppError(409, 'OTP_PROOF_USED', 'OTP proof has already been used');
    }
    const user = await providers().dataStore.findUserByPhone(phone);
    if (!user || user.status !== 'ACTIVE') throw new AppError(404, 'ACCOUNT_NOT_FOUND', 'Active account was not found');
    await providers().identityProvider.setPassword(user.userId, password);
    return res.json({ success: true });
  } catch (error) { return sendRouteError(res, req, error); }
});

// The platform master is not a tenant user. Its PIN is stored in Secrets Manager
// by the AWS data provider and can only be changed after an OTP proof for the
// currently configured master mobile number.
router.post('/auth/master/pin', limitOtpVerify, async (req, res) => {
  try {
    const { mobileNumber, pin, resetToken } = req.body || {};
    const mobile = normalizePhone(mobileNumber);
    if (!/^[6-9][0-9]{9}$/.test(mobile) || typeof pin !== 'string' || !/^\d{6,12}$/.test(pin)) {
      throw new AppError(400, 'MASTER_PIN_INPUT_INVALID', 'A valid mobile number and 6-12 digit PIN are required');
    }
    const master = await providers().dataStore.getMasterConfig();
    if (mobile !== master.mobile || !resetProofValid(mobile, resetToken)) {
      throw new AppError(401, 'OTP_PROOF_INVALID', 'Verify an OTP for the current master mobile number');
    }
    const proof = crypto.createHash('sha256').update(String(resetToken)).digest('hex');
    if (!await providers().dataStore.consumeNonce('master-pin-reset', `${mobile}:${proof}`, Date.now() + 86_400_000)) {
      throw new AppError(409, 'OTP_PROOF_USED', 'OTP proof has already been used');
    }
    await providers().dataStore.updateMasterConfig({ mobile, pin });
    return res.json({ success: true });
  } catch (error) { return sendRouteError(res, req, error); }
});

router.get('/account/me', requireAuth, async (req: AuthenticatedRequest, res) => {
  try {
    const user = await providers().dataStore.findUserById(req.user!.userId);
    return res.json({ success: true, user });
  } catch (error) { return sendRouteError(res, req, error); }
});

router.get('/staff', requireAuth, async (req: AuthenticatedRequest, res) => {
  try {
    return res.json({ success: true, staff: await providers().dataStore.listStaff(req.user!.companyId) });
  } catch (error) { return sendRouteError(res, req, error); }
});

router.post('/staff', requireAuth, async (req: AuthenticatedRequest, res) => {
  try {
    const actor = req.user!;
    if (actor.role !== 'ADMIN' || !actor.permissions.includes('USER_MANAGE')) throw new AppError(403, 'STAFF_ADMIN_REQUIRED', 'Shop administrator permission is required');
    const { mobileNumber, displayName, password, permissions } = req.body || {};
    if (!validAccountInput(mobileNumber, displayName, password) || !Array.isArray(permissions)) throw new AppError(400, 'STAFF_INPUT_INVALID', 'Valid staff details are required');
    const user = await providers().dataStore.createStaff({ companyId: actor.companyId, phone: normalizePhone(mobileNumber), displayName: displayName.trim(), password, permissions });
    try { await providers().identityProvider.setPassword(user.userId, password); }
    catch (error) {
      await providers().identityProvider.deleteUser(user.userId).catch(() => undefined);
      await providers().dataStore.deleteStaff(actor.companyId, user.userId).catch(() => undefined);
      throw error;
    }
    return res.status(201).json({ success: true, user });
  } catch (error) { return sendRouteError(res, req, error); }
});

router.patch('/staff/:userId', requireAuth, async (req: AuthenticatedRequest, res) => {
  try {
    if (req.user!.role !== 'ADMIN' || !req.user!.permissions.includes('USER_MANAGE')) throw new AppError(403, 'STAFF_ADMIN_REQUIRED', 'Shop administrator permission is required');
    const changes: any = {};
    if (typeof req.body?.displayName === 'string') changes.displayName = req.body.displayName.trim();
    if (Array.isArray(req.body?.permissions)) changes.permissions = req.body.permissions;
    const user = await providers().dataStore.updateStaff(req.user!.companyId, req.params.userId, changes);
    if (typeof req.body?.password === 'string' && req.body.password.length >= 6) await providers().identityProvider.setPassword(user.userId, req.body.password);
    return res.json({ success: true, user });
  } catch (error) { return sendRouteError(res, req, error); }
});

router.delete('/staff/:userId', requireAuth, async (req: AuthenticatedRequest, res) => {
  try {
    if (req.user!.role !== 'ADMIN' || !req.user!.permissions.includes('USER_MANAGE')) throw new AppError(403, 'STAFF_ADMIN_REQUIRED', 'Shop administrator permission is required');
    const user = await providers().dataStore.updateStaff(req.user!.companyId, req.params.userId, { status: 'INACTIVE', permissions: [] });
    await providers().identityProvider.revokeUser(user.userId);
    return res.json({ success: true });
  } catch (error) { return sendRouteError(res, req, error); }
});

router.post('/staff/:userId/approve', requireAuth, async (req: AuthenticatedRequest, res) => {
  try {
    if (req.user!.role !== 'ADMIN' || !req.user!.permissions.includes('USER_MANAGE')) throw new AppError(403, 'STAFF_ADMIN_REQUIRED', 'Shop administrator permission is required');
    const user = await providers().dataStore.updateStaff(req.user!.companyId, req.params.userId, { status: 'ACTIVE' });
    return res.json({ success: true, user });
  } catch (error) { return sendRouteError(res, req, error); }
});

export default router;
