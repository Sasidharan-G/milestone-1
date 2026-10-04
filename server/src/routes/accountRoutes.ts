import express, { Response, Router } from 'express';
import crypto, { randomUUID } from 'node:crypto';
import { AppError } from '../core/errors';
import { audit } from '../core/audit';
import { detectImageType } from '../core/imageType';
import { eraseCompany } from '../core/companyDeletion';
import { presentLicense } from '../core/license';
import { emitToCompany, emitToUser, revokeSessionSockets } from '../core/realtime';
import { normalizePhone, verifyResetToken } from '../controllers/otpController';
import {
  AuthenticatedRequest, MASTER_USER_ID, PLATFORM_COMPANY_ID, requireActiveLicense, requireAuth, requireShopAdmin
} from '../middleware/authMiddleware';
import { limitLogin, limitMasterLogin, limitOtpVerify } from '../middleware/rateLimitMiddleware';
import { providers } from '../providers/providerRegistry';
import { activePermissions } from '../providers/local/localDataStore';
import { sendRouteError } from './http';

const router = Router();

export { activePermissions };

export const PASSWORD_MIN = 6;
export const PASSWORD_MAX = 6;
const validPhone = (phone: unknown): phone is string => typeof phone === 'string' && /^[6-9][0-9]{9}$/.test(normalizePhone(phone));
const validPassword = (password: unknown): password is string => typeof password === 'string' && /^\d{6}$/.test(password);
const validName = (name: unknown): name is string => typeof name === 'string' && name.trim().length >= 1 && name.length <= 120;
const profileText = (value: unknown, field: string, max: number, required = false): string | undefined => {
  if (value === undefined) return undefined;
  if (typeof value !== 'string') throw new AppError(400, 'SHOP_PROFILE_INVALID', `${field} must be text`);
  const trimmed = value.trim();
  if ((required && !trimmed) || trimmed.length > max) throw new AppError(400, 'SHOP_PROFILE_INVALID', `${field} is required and must be at most ${max} characters`);
  return trimmed;
};
const profileEmail = (value: unknown): string | undefined => {
  const email = profileText(value, 'Email', 160);
  if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) throw new AppError(400, 'SHOP_PROFILE_INVALID', 'Enter a valid email address');
  return email;
};
const profilePhone = (value: unknown): string | undefined => {
  const phone = profileText(value, 'Shop phone number', 20);
  if (phone && !/^[+0-9()\-\s]{7,20}$/.test(phone)) throw new AppError(400, 'SHOP_PROFILE_INVALID', 'Enter a valid shop phone number');
  return phone;
};
export function validAccountInput(phone: unknown, name: unknown, password: unknown): boolean {
  return validPhone(phone) && validName(name) && validPassword(password);
}

const resetProofValid = (phone: string, resetToken: unknown): boolean => {
  if (process.env.NODE_ENV === 'development' && process.env.LOCAL_DEV_OTP_BYPASS === 'true' && resetToken === 'local-dev-verified') return true;
  return typeof resetToken === 'string' && verifyResetToken(phone, resetToken);
};

const burnProof = async (scope: string, phone: string, resetToken: unknown): Promise<void> => {
  const proof = crypto.createHash('sha256').update(String(resetToken)).digest('hex');
  if (!await providers().dataStore.consumeNonce(scope, `${phone}:${proof}`, Date.now() + 86_400_000)) {
    throw new AppError(409, 'OTP_PROOF_USED', 'OTP proof has already been used');
  }
};

router.post('/auth/register', limitOtpVerify, async (req, res) => {
  try {
    const { mobileNumber, ownerName, businessName, password, resetToken } = req.body || {};
    if (!validAccountInput(mobileNumber, ownerName, password) || typeof businessName !== 'string' || !businessName.trim() || businessName.length > 160) {
      throw new AppError(400, 'ACCOUNT_INPUT_INVALID', `Enter a valid mobile, owner, business, and a password of at least ${PASSWORD_MIN} characters`);
    }
    const phone = normalizePhone(mobileNumber);
    if (!resetProofValid(phone, resetToken)) throw new AppError(401, 'OTP_PROOF_INVALID', 'Verify a fresh OTP before registration');
    await burnProof('registration', phone, resetToken);
    const result = await providers().dataStore.createAccount({ phone, displayName: ownerName.trim(), businessName: businessName.trim(), password });
    try { await providers().identityProvider.setPassword(result.user.userId, password); }
    catch (error) {
      await providers().identityProvider.deleteUser(result.user.userId).catch(() => undefined);
      await providers().dataStore.deleteCompany(result.user.companyId).catch(() => undefined);
      throw error;
    }
    await audit(req, { userId: result.user.userId, role: 'ADMIN', companyId: result.user.companyId }, 'ACCOUNT_REGISTERED', result.user.userId, { phone });
    // Without this a brand new shop does not appear in Master Control until the operator
    // manually refreshes.
    emitToUser(req.app.get('io'), MASTER_USER_ID, 'master_overview_changed', { companyId: result.user.companyId });
    return res.status(201).json({ success: true, companyId: result.user.companyId, user: result.user, license: presentLicense(result.license) });
  } catch (error) { return sendRouteError(res, req, error); }
});

router.post('/auth/login', limitLogin, async (req, res) => {
  try {
    const { username, password } = req.body || {};
    if (!username || !password) throw new AppError(400, 'AUTH_INPUT_REQUIRED', 'Username/mobile and password are required');
    const phone = normalizePhone(String(username));
    let result;
    try { result = await providers().identityProvider.authenticate(phone, String(password)); }
    catch (error) {
      if (error instanceof AppError && error.code === 'AUTH_ACCOUNT_INACTIVE') {
        const account = await providers().dataStore.findUserByPhone(phone);
        if (account) await audit(req, { userId: account.userId, role: account.role, companyId: account.companyId }, 'LOGIN_BLOCKED', account.userId, { status: account.status });
        throw new AppError(403, 'AUTH_ACCOUNT_INACTIVE', inactiveMessage(account?.status), false, { status: account?.status || 'INACTIVE' });
      }
      throw error;
    }
    const license = await providers().dataStore.getLicense(result.user.companyId);
    await audit(req, { userId: result.user.userId, role: result.user.role, companyId: result.user.companyId }, 'LOGIN_SUCCESS', result.user.userId);
    return res.json({ success: true, user: result.user, tokens: result.tokens, license: license ? presentLicense(license) : null });
  } catch (error) { return sendRouteError(res, req, error); }
});

const inactiveMessage = (status?: string): string => {
  if (status === 'REJECTED') return 'Your account request was rejected. Contact your shop administrator';
  return 'Your account has been deactivated. Contact your shop administrator';
};

export const validMasterLoginInput = (_mobileNumber: unknown, pin: unknown): boolean =>
  typeof pin === 'string' && /^\d{6,12}$/.test(pin);

/**
 * The master mobile can be configured as 9789418144 or +919789418144 (an EB environment property,
 * or the number typed in the app). Comparing the raw strings made a "+91" value never match the
 * 10-digit number the app sends, so the OTP flow for changing the master PIN always failed.
 */
export const sameMasterMobile = (candidate: unknown, configured: unknown): boolean => {
  const a = normalizePhone(String(candidate ?? ''));
  const b = normalizePhone(String(configured ?? ''));
  return a.length === 10 && a === b;
};

/**
 * Master login only issues tokens. Establishing a device session is the same universal
 * POST /sessions/register call every principal uses, which already enforces single-device
 * (it revokes every other live session for this companyId+userId, platform/master included).
 */
router.post('/auth/master/login', limitMasterLogin, limitLogin, async (req: AuthenticatedRequest, res) => {
  try {
    const { pin } = req.body || {};
    if (!validMasterLoginInput(undefined, pin)) throw new AppError(400, 'MASTER_LOGIN_INPUT_INVALID', 'A 6-12 digit PIN is required');
    const mobileNumber = (await providers().dataStore.getMasterConfig()).mobile;
    const result = await providers().identityProvider.authenticatePlatform(mobileNumber, pin);
    await audit(req, { userId: MASTER_USER_ID, role: 'SUPER_ADMIN', companyId: PLATFORM_COMPANY_ID }, 'MASTER_LOGIN', MASTER_USER_ID);
    return res.json({ success: true, mobile: result.mobile, tokens: result.tokens });
  } catch (error) { return sendRouteError(res, req, error); }
});

router.post('/auth/refresh', async (req, res) => {
  try {
    const { refreshToken } = req.body || {};
    if (!refreshToken) throw new AppError(400, 'AUTH_TOKEN_REQUIRED', 'Refresh token is required');
    const result = await providers().identityProvider.refresh(String(refreshToken));
    return res.json({ success: true, user: result.user, tokens: result.tokens });
  } catch (error) { return sendRouteError(res, req, error); }
});

/** Forgot password for every tier. Resetting signs the account out of all devices. */
router.post('/auth/password/reset', limitOtpVerify, async (req: AuthenticatedRequest, res) => {
  try {
    const { mobileNumber, password, resetToken } = req.body || {};
    const phone = normalizePhone(mobileNumber);
    if (!validPhone(phone) || !validPassword(password)) {
      throw new AppError(400, 'AUTH_RESET_INPUT_INVALID', `A valid mobile number and a password of at least ${PASSWORD_MIN} characters are required`);
    }
    if (!resetProofValid(phone, resetToken)) throw new AppError(401, 'OTP_PROOF_INVALID', 'Verify a fresh OTP before resetting the password');
    await burnProof('password-reset', phone, resetToken);
    const user = await providers().dataStore.findUserByPhone(phone);
    if (!user || user.status !== 'ACTIVE') throw new AppError(404, 'ACCOUNT_NOT_FOUND', 'Active account was not found');
    await providers().identityProvider.setPassword(user.userId, password);
    await providers().identityProvider.revokeUser(user.userId);
    const revoked = await providers().sessionStore.revokeAllSessions(user.companyId, user.userId);
    const io = req.app.get('io');
    for (const session of revoked) emitToUser(io, user.userId, 'session_revoked', { sessionId: session.sessionId, reason: 'PASSWORD_RESET' });
    revokeSessionSockets(io, user.userId, revoked, 'PASSWORD_RESET');
    await audit(req, { userId: user.userId, role: user.role, companyId: user.companyId }, 'PASSWORD_RESET', user.userId, { sessionsRevoked: revoked.length });
    return res.json({ success: true });
  } catch (error) { return sendRouteError(res, req, error); }
});

// The platform master is not a tenant user. Its PIN can only be changed after an OTP proof
// for the currently configured master mobile number; the new mobile takes effect at once.
router.post('/auth/master/pin', limitOtpVerify, async (req: AuthenticatedRequest, res) => {
  try {
    const { mobileNumber, pin, resetToken, newMobileNumber } = req.body || {};
    const mobile = normalizePhone(mobileNumber);
    const nextMobile = newMobileNumber === undefined ? mobile : normalizePhone(String(newMobileNumber));
    if (!validPhone(mobile) || !validPhone(nextMobile) || typeof pin !== 'string' || !/^\d{6}$/.test(pin)) {
      throw new AppError(400, 'MASTER_PIN_INPUT_INVALID', 'A valid mobile number and 6-digit numeric PIN are required');
    }
    const master = await providers().dataStore.getMasterConfig();
    if (!sameMasterMobile(mobile, master.mobile) || !resetProofValid(mobile, resetToken)) {
      throw new AppError(401, 'OTP_PROOF_INVALID', 'Verify an OTP for the current master mobile number');
    }
    await burnProof('master-pin-reset', mobile, resetToken);
    await providers().dataStore.updateMasterConfig({ mobile: nextMobile, pin });
    const revoked = await providers().sessionStore.revokeAllSessions(PLATFORM_COMPANY_ID, MASTER_USER_ID);
    const io = req.app.get('io');
    for (const session of revoked) emitToUser(io, MASTER_USER_ID, 'session_revoked', { sessionId: session.sessionId, reason: 'MASTER_PIN_CHANGED' });
    revokeSessionSockets(io, MASTER_USER_ID, revoked, 'MASTER_PIN_CHANGED');
    await audit(req, { userId: MASTER_USER_ID, role: 'SUPER_ADMIN', companyId: PLATFORM_COMPANY_ID }, 'MASTER_PIN_CHANGED', MASTER_USER_ID, { mobileChanged: nextMobile !== mobile });
    return res.json({ success: true, mobile: nextMobile });
  } catch (error) { return sendRouteError(res, req, error); }
});

router.get('/account/me', requireAuth, async (req: AuthenticatedRequest, res) => {
  try {
    if (req.user!.super_admin) return res.json({ success: true, user: { userId: MASTER_USER_ID, role: 'SUPER_ADMIN', companyId: PLATFORM_COMPANY_ID } });
    const user = await providers().dataStore.findUserById(req.user!.userId);
    if (!user || user.companyId !== req.user!.companyId) throw new AppError(404, 'ACCOUNT_NOT_FOUND', 'Account was not found');
    const license = await providers().dataStore.getLicense(user.companyId);
    const shopProfile = await providers().dataStore.getShopProfile(user.companyId);
    return res.json({ success: true, user, license: license ? presentLicense(license) : null, shopProfile });
  } catch (error) { return sendRouteError(res, req, error); }
});

router.get('/account/shop-profile', requireAuth, async (req: AuthenticatedRequest, res) => {
  try {
    const shopProfile = await providers().dataStore.getShopProfile(req.user!.companyId);
    if (!shopProfile) throw new AppError(404, 'SHOP_PROFILE_NOT_FOUND', 'Shop profile was not found');
    return res.json({ success: true, shopProfile });
  } catch (error) { return sendRouteError(res, req, error); }
});

const saveShopProfile = async (req: AuthenticatedRequest, res: Response, legacy = false) => {
  try {
    const body = req.body || {};
    const companyId = req.user!.companyId;
    const changes: Record<string, string> = {};
    const values: Array<[string, unknown, string, number, boolean]> = [
      ['shopName', legacy ? body.businessName : body.shopName, 'Shop name', 160, true],
      ['ownerName', body.ownerName, 'Owner name', 120, true],
      ['gstNumber', body.gstNumber, 'GST number', 32, false],
      ['address', body.address, 'Shop address', 500, false],
      ['phone', body.phone, 'Shop phone number', 20, false]
    ];
    for (const [key, value, label, max, required] of values) {
      const parsed = key === 'phone' ? profilePhone(value) : profileText(value, label, max, required);
      if (parsed !== undefined) changes[key] = parsed;
    }
    const email = profileEmail(body.email);
    if (email !== undefined) changes.email = email;
    if (!Object.keys(changes).length) throw new AppError(400, 'SHOP_PROFILE_INVALID', 'Provide at least one shop profile field');
    changes.updatedByUserId = req.user!.userId;
    const shopProfile = await providers().dataStore.updateShopProfile(companyId, changes);
    const updatedLicense = await providers().dataStore.getLicense(companyId);
    const io = req.app.get('io');
    if (updatedLicense) emitToCompany(io, companyId, 'license_changed', { license: presentLicense(updatedLicense) });
    emitToCompany(io, companyId, 'shop_profile_changed', { shopProfile });
    emitToCompany(io, companyId, 'account_changed', { businessName: shopProfile.shopName, ownerName: shopProfile.ownerName });
    emitToUser(io, MASTER_USER_ID, 'master_overview_changed', { companyId, businessName: shopProfile.shopName, ownerName: shopProfile.ownerName });
    await audit(req, req.user!, 'SHOP_PROFILE_UPDATED', companyId, { fields: Object.keys(changes) });
    return res.json({ success: true, shopProfile, license: updatedLicense ? presentLicense(updatedLicense) : null });
  } catch (error) { return sendRouteError(res, req, error); }
};

router.patch('/account/shop-profile', requireAuth, requireActiveLicense, requireShopAdmin, (req, res) => saveShopProfile(req, res));

// The shop logo. It lives in the cloud so it survives a reinstall and shows up on every device of the shop.
// The owner replaces it with a raw PNG/JPEG body; every device of the shop reads it back.
const SHOP_LOGO_MAX_BYTES = 2 * 1024 * 1024;

const announceLogoChange = async (req: AuthenticatedRequest, shopProfile: unknown, action: string, details: Record<string, unknown>) => {
  const companyId = req.user!.companyId;
  emitToCompany(req.app.get('io'), companyId, 'shop_profile_changed', { shopProfile });
  await audit(req, req.user!, action, companyId, details);
};

router.put('/account/shop-logo', requireAuth, requireActiveLicense, requireShopAdmin,
  express.raw({ type: ['image/png', 'image/jpeg'], limit: SHOP_LOGO_MAX_BYTES }),
  async (req: AuthenticatedRequest, res) => {
    try {
      const content = req.body;
      if (!Buffer.isBuffer(content) || content.length === 0) throw new AppError(400, 'SHOP_LOGO_REQUIRED', 'Send the logo as a PNG or JPEG picture');
      if (content.length > SHOP_LOGO_MAX_BYTES) throw new AppError(413, 'SHOP_LOGO_TOO_LARGE', 'The logo must be 2 MB or smaller');
      const contentType = detectImageType(content);
      if (!contentType) throw new AppError(400, 'SHOP_LOGO_INVALID', 'The logo must be a PNG or JPEG picture');
      const companyId = req.user!.companyId;
      const key = await providers().objectStorage.writeShopLogo(companyId, content, contentType);
      const shopProfile = await providers().dataStore.updateShopProfile(companyId, { logoObjectKey: key, logoUpdatedAtEpochMs: Date.now(), updatedByUserId: req.user!.userId });
      await announceLogoChange(req, shopProfile, 'SHOP_LOGO_UPDATED', { bytes: content.length, contentType });
      return res.json({ success: true, shopProfile });
    } catch (error) { return sendRouteError(res, req, error); }
  });

router.get('/account/shop-logo', requireAuth, requireActiveLicense, async (req: AuthenticatedRequest, res) => {
  try {
    const content = await providers().objectStorage.readShopLogo(req.user!.companyId);
    res.type(detectImageType(content) || 'application/octet-stream');
    res.set('Cache-Control', 'private, no-cache');
    return res.send(content);
  } catch (error) { return sendRouteError(res, req, error); }
});

router.delete('/account/shop-logo', requireAuth, requireActiveLicense, requireShopAdmin, async (req: AuthenticatedRequest, res) => {
  try {
    const companyId = req.user!.companyId;
    await providers().objectStorage.deleteShopLogo(companyId);
    // The version still moves, so the shop's other devices notice the logo is gone.
    // An empty key means "no logo" (DynamoDB refuses an undefined attribute, so the field is blanked, not dropped).
    const shopProfile = await providers().dataStore.updateShopProfile(companyId, { logoObjectKey: '', logoUpdatedAtEpochMs: Date.now(), updatedByUserId: req.user!.userId });
    await announceLogoChange(req, shopProfile, 'SHOP_LOGO_REMOVED', {});
    return res.json({ success: true, shopProfile });
  } catch (error) { return sendRouteError(res, req, error); }
});
// Compatibility for older Android builds. It updates the same canonical profile record.
router.patch('/account/shop-details', requireAuth, requireActiveLicense, requireShopAdmin, (req, res) => saveShopProfile(req, res, true));

// ---- Staff management: shop admin only, active subscription required ----

router.get('/staff', requireAuth, requireActiveLicense, requireShopAdmin, async (req: AuthenticatedRequest, res) => {
  try {
    return res.json({ success: true, staff: await providers().dataStore.listStaff(req.user!.companyId) });
  } catch (error) { return sendRouteError(res, req, error); }
});

router.post('/staff', requireAuth, requireActiveLicense, requireShopAdmin, async (req: AuthenticatedRequest, res) => {
  try {
    const actor = req.user!;
    const { mobileNumber, displayName, password, permissions } = req.body || {};
    if (!validPhone(mobileNumber)) {
      throw new AppError(400, 'STAFF_INPUT_INVALID', 'Please enter a valid 10-digit mobile number');
    }
    if (!validName(displayName)) {
      throw new AppError(400, 'STAFF_INPUT_INVALID', 'Display name is required (1 to 120 characters)');
    }
    if (!validPassword(password)) {
      throw new AppError(400, 'STAFF_INPUT_INVALID', 'Password / PIN must be exactly 6 numeric digits');
    }
    if (!Array.isArray(permissions)) {
      throw new AppError(400, 'STAFF_INPUT_INVALID', 'Permissions list is required');
    }
    const user = await providers().dataStore.createStaff({ companyId: actor.companyId, phone: normalizePhone(mobileNumber), displayName: displayName.trim(), password, permissions: permissions.map(String) });
    try { await providers().identityProvider.setPassword(user.userId, password); }
    catch (error) {
      await providers().identityProvider.deleteUser(user.userId).catch(() => undefined);
      await providers().dataStore.deleteStaff(actor.companyId, user.userId).catch(() => undefined);
      throw error;
    }
    await audit(req, actor, 'STAFF_CREATED', user.userId, { permissions: user.permissions });
    emitToCompany(req.app.get('io'), actor.companyId, 'staff_changed', { userId: user.userId });
    return res.status(201).json({ success: true, user });
  } catch (error) { return sendRouteError(res, req, error); }
});

router.patch('/staff/:userId', requireAuth, requireActiveLicense, requireShopAdmin, async (req: AuthenticatedRequest, res) => {
  try {
    const changes: any = {};
    if (typeof req.body?.displayName === 'string') changes.displayName = req.body.displayName.trim();
    if (Array.isArray(req.body?.permissions)) changes.permissions = req.body.permissions.map(String);
    const user = await providers().dataStore.updateStaff(req.user!.companyId, req.params.userId, changes);
    if (req.body?.password !== undefined) {
      if (!validPassword(req.body.password)) throw new AppError(400, 'AUTH_PASSWORD_INVALID', 'Password must be exactly 6 numeric digits');
      await providers().identityProvider.setPassword(user.userId, req.body.password);
      await providers().identityProvider.revokeUser(user.userId);
      const revoked = await providers().sessionStore.revokeAllSessions(user.companyId, user.userId);
      revokeSessionSockets(req.app.get('io'), user.userId, revoked, 'PASSWORD_CHANGED');
    }
    await audit(req, req.user!, 'STAFF_UPDATED', user.userId, { permissions: user.permissions, passwordChanged: req.body?.password !== undefined });
    emitToUser(req.app.get('io'), user.userId, 'account_changed', { status: user.status, permissions: user.permissions });
    return res.json({ success: true, user });
  } catch (error) { return sendRouteError(res, req, error); }
});

const setStaffStatus = async (req: AuthenticatedRequest, status: 'ACTIVE' | 'INACTIVE' | 'REJECTED', action: string) => {
  const changes = status === 'ACTIVE' ? { status } : { status, permissions: [] as string[] };
  const user = await providers().dataStore.updateStaff(req.user!.companyId, req.params.userId, changes);
  if (status !== 'ACTIVE') {
    await providers().identityProvider.revokeUser(user.userId);
    const revoked = await providers().sessionStore.revokeAllSessions(user.companyId, user.userId);
    for (const session of revoked) emitToUser(req.app.get('io'), user.userId, 'session_revoked', { sessionId: session.sessionId, reason: 'ACCOUNT_DISABLED' });
    revokeSessionSockets(req.app.get('io'), user.userId, revoked, 'ACCOUNT_DISABLED');
  }
  await audit(req, req.user!, action, user.userId);
  emitToUser(req.app.get('io'), user.userId, 'account_changed', { status: user.status, permissions: user.permissions });
  emitToCompany(req.app.get('io'), user.companyId, 'staff_changed', { userId: user.userId });
  return user;
};

router.delete('/staff/:userId', requireAuth, requireActiveLicense, requireShopAdmin, async (req: AuthenticatedRequest, res) => {
  try {
    await setStaffStatus(req, 'INACTIVE', 'STAFF_DEACTIVATED');
    return res.json({ success: true });
  } catch (error) { return sendRouteError(res, req, error); }
});


/**
 * The owner deletes their own account and all of the shop's data. Google Play requires this to be
 * possible from inside the app. It is the same erase Master Control performs, so it removes the
 * cloud copy for every device and staff member of the shop; what is on each phone is wiped by the
 * app once it is told the shop is gone.
 *
 * Guards, because it cannot be undone: only the shop administrator, the PIN is asked again (a
 * stolen unlocked phone must not be able to erase a shop), and the caller must send
 * `confirmation: "DELETE"`. The license is deliberately not required: an owner whose subscription
 * lapsed must still be able to leave.
 */
router.delete('/account', limitLogin, requireAuth, requireShopAdmin, async (req: AuthenticatedRequest, res) => {
  try {
    const { password, confirmation } = req.body || {};
    if (confirmation !== 'DELETE') throw new AppError(400, 'ACCOUNT_DELETE_CONFIRMATION_REQUIRED', 'Type DELETE to confirm');
    if (!validPassword(password)) throw new AppError(400, 'ACCOUNT_DELETE_PIN_REQUIRED', 'Enter your 6-digit PIN to delete the account');
    const owner = await providers().dataStore.findUserById(req.user!.userId);
    if (!owner) throw new AppError(404, 'ACCOUNT_NOT_FOUND', 'Account was not found');
    try { await providers().identityProvider.authenticate(owner.phone, password); }
    catch (error) {
      // 403, not 401: the app treats a 401 on a signed-in call as an expired token and retries.
      if (error instanceof AppError && error.status === 401) throw new AppError(403, 'ACCOUNT_DELETE_PIN_INVALID', 'The PIN is incorrect');
      throw error;
    }
    await eraseCompany(req, owner.companyId, req.user!, 'OWNER');
    return res.json({ success: true });
  } catch (error) { return sendRouteError(res, req, error); }
});

router.get('/audit', requireAuth, requireActiveLicense, requireShopAdmin, async (req: AuthenticatedRequest, res) => {
  try {
    const limit = Math.min(500, Math.max(1, Number(req.query.limit) || 100));
    return res.json({ success: true, entries: await providers().dataStore.listAudit(req.user!.companyId, limit) });
  } catch (error) { return sendRouteError(res, req, error); }
});

export default router;
