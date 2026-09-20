import { Router } from 'express';
import { AppError } from '../core/errors';
import { audit } from '../core/audit';
import { applyLicenseAction, parseLicenseAction, presentLicense } from '../core/license';
import { emitToCompany, emitToUser, revokeSessionSockets } from '../core/realtime';
import { AuthenticatedRequest, MASTER_USER_ID, PLATFORM_COMPANY_ID, requireAuth, requireSuperAdmin } from '../middleware/authMiddleware';
import { providers } from '../providers/providerRegistry';
import { sendRouteError } from './http';

/** Super Master Control. Every route requires a platform (SUPER_ADMIN) token and its device session. */
const router = Router();
router.use(requireAuth, requireSuperAdmin);

router.get('/overview', async (req, res) => {
  try {
    const overview = await providers().dataStore.adminOverview();
    return res.json({
      success: true,
      licenses: overview.licenses.map(license => presentLicense(license)),
      users: overview.users,
      masterConfig: { mobile: overview.masterConfig.mobile, updatedAtEpochMs: overview.masterConfig.updatedAtEpochMs, pinConfigured: Boolean(overview.masterConfig.pin) }
    });
  } catch (error) { return sendRouteError(res, req, error); }
});

router.get('/audit', async (req: AuthenticatedRequest, res) => {
  try {
    const companyId = typeof req.query.companyId === 'string' && req.query.companyId ? req.query.companyId : PLATFORM_COMPANY_ID;
    const limit = Math.min(500, Math.max(1, Number(req.query.limit) || 100));
    return res.json({ success: true, entries: await providers().dataStore.listAudit(companyId, limit) });
  } catch (error) { return sendRouteError(res, req, error); }
});

/**
 * Subscription control: TRIAL (2 days), GRANT_DAYS, GRANT_YEARS, EXTEND_DAYS, REVOKE.
 * The affected tenant is told over its socket room so the lock screen reacts without waiting for a poll.
 */
router.patch('/licenses/:companyId', async (req: AuthenticatedRequest, res) => {
  try {
    const action = parseLicenseAction(req.body);
    if (!action) throw new AppError(400, 'LICENSE_ACTION_INVALID', 'Send action TRIAL | GRANT_DAYS {days} | GRANT_YEARS {years} | EXTEND_DAYS {days} | REVOKE');
    const current = await providers().dataStore.getLicense(req.params.companyId);
    if (!current) throw new AppError(404, 'LICENSE_NOT_FOUND', 'Company license was not found');
    const next = applyLicenseAction(current, action);
    const license = await providers().dataStore.updateLicense(req.params.companyId, next);
    await audit(req, req.user!, `LICENSE_${action.action}`, req.params.companyId, { ...action, validUntilEpochMs: license.validUntilEpochMs }, req.params.companyId);
    emitToCompany(req.app.get('io'), req.params.companyId, 'license_changed', { license: presentLicense(license) });
    emitToUser(req.app.get('io'), MASTER_USER_ID, 'master_overview_changed', { companyId: req.params.companyId });
    return res.json({ success: true, license: presentLicense(license) });
  } catch (error) { return sendRouteError(res, req, error); }
});

// Separate from PATCH /staff/:userId, which only ever targets CASHIER accounts — this is the
// only way to change the shop OWNER's own cloud tier/expiry (an ADMIN account is not "staff").
router.patch('/companies/:companyId/cloud-access', async (req: AuthenticatedRequest, res) => {
  try {
    const admin = await providers().dataStore.findAdminByCompany(req.params.companyId);
    if (!admin) throw new AppError(404, 'ACCOUNT_NOT_FOUND', 'Shop owner account was not found');
    if (req.body?.isCloudTier !== undefined && typeof req.body.isCloudTier !== 'boolean') throw new AppError(400, 'STAFF_INPUT_INVALID', 'isCloudTier must be a boolean');
    if (req.body?.cloudAccessGrantedUntilEpochMs !== undefined && req.body.cloudAccessGrantedUntilEpochMs !== null && typeof req.body.cloudAccessGrantedUntilEpochMs !== 'number') {
      throw new AppError(400, 'STAFF_INPUT_INVALID', 'cloudAccessGrantedUntilEpochMs must be a number or null');
    }
    const updated = await providers().dataStore.updateAccountCloudAccess(req.params.companyId, admin.userId, {
      ...(req.body?.isCloudTier === undefined ? {} : { isCloudTier: req.body.isCloudTier }),
      ...(req.body?.cloudAccessGrantedUntilEpochMs === undefined ? {} : { cloudAccessGrantedUntilEpochMs: req.body.cloudAccessGrantedUntilEpochMs })
    });
    await audit(req, req.user!, 'OWNER_CLOUD_ACCESS_SET_BY_MASTER', admin.userId, { isCloudTier: updated.isCloudTier, cloudAccessGrantedUntilEpochMs: updated.cloudAccessGrantedUntilEpochMs }, req.params.companyId);
    emitToUser(req.app.get('io'), admin.userId, 'account_changed', { status: updated.status, permissions: updated.permissions });
    emitToUser(req.app.get('io'), MASTER_USER_ID, 'master_overview_changed', { companyId: req.params.companyId });
    return res.json({ success: true, user: updated });
  } catch (error) { return sendRouteError(res, req, error); }
});

router.delete('/companies/:companyId', async (req: AuthenticatedRequest, res) => {
  try {
    const companyId = req.params.companyId;
    const overview = await providers().dataStore.adminOverview();
    const companyUsers = overview.users.filter(user => user.companyId === companyId);
    if (!companyUsers.length && !overview.licenses.some(license => license.companyId === companyId)) throw new AppError(404, 'COMPANY_NOT_FOUND', 'Company was not found');
    const backups = await providers().objectStorage.list(companyId);
    await Promise.all(backups.map(backup => providers().objectStorage.delete(companyId, backup.backupId)));
    for (const user of companyUsers) {
      const revoked = await providers().sessionStore.revokeAllSessions(companyId, user.userId);
      for (const session of revoked) emitToUser(req.app.get('io'), user.userId, 'session_revoked', { sessionId: session.sessionId, reason: 'ACCOUNT_DELETED' });
      revokeSessionSockets(req.app.get('io'), user.userId, revoked, 'ACCOUNT_DELETED');
      await providers().identityProvider.deleteUser(user.userId);
    }
    await providers().dataStore.deleteCompany(companyId);
    await audit(req, req.user!, 'COMPANY_DELETED', companyId, { users: companyUsers.map(user => user.userId), backups: backups.length });
    emitToUser(req.app.get('io'), MASTER_USER_ID, 'master_overview_changed', { companyId });
    return res.status(204).send();
  } catch (error) { return sendRouteError(res, req, error); }
});

router.patch('/staff/:userId', async (req: AuthenticatedRequest, res) => {
  try {
    // findUserById is a point read; adminOverview() was a full table scan to reach one row.
    const found = await providers().dataStore.findUserById(req.params.userId);
    const user = found && found.role === 'CASHIER' ? found : null;
    if (!user) throw new AppError(404, 'STAFF_NOT_FOUND', 'Staff account was not found');
    const status = String(req.body?.status || user.status);
    if (!['ACTIVE', 'INACTIVE', 'REJECTED'].includes(status)) throw new AppError(400, 'STAFF_STATUS_INVALID', 'Staff status is invalid');
    if (req.body?.isCloudTier !== undefined && typeof req.body.isCloudTier !== 'boolean') throw new AppError(400, 'STAFF_INPUT_INVALID', 'isCloudTier must be a boolean');
    if (req.body?.cloudAccessGrantedUntilEpochMs !== undefined && req.body.cloudAccessGrantedUntilEpochMs !== null && typeof req.body.cloudAccessGrantedUntilEpochMs !== 'number') {
      throw new AppError(400, 'STAFF_INPUT_INVALID', 'cloudAccessGrantedUntilEpochMs must be a number or null');
    }
    const updated = await providers().dataStore.updateStaff(user.companyId, user.userId, {
      status: status as any,
      permissions: Array.isArray(req.body?.permissions) ? req.body.permissions.map(String) : user.permissions,
      ...(req.body?.isCloudTier === undefined ? {} : { isCloudTier: req.body.isCloudTier }),
      ...(req.body?.cloudAccessGrantedUntilEpochMs === undefined ? {} : { cloudAccessGrantedUntilEpochMs: req.body.cloudAccessGrantedUntilEpochMs })
    });
    if (status !== 'ACTIVE') {
      await providers().identityProvider.revokeUser(user.userId);
      const revoked = await providers().sessionStore.revokeAllSessions(user.companyId, user.userId);
      for (const session of revoked) emitToUser(req.app.get('io'), user.userId, 'session_revoked', { sessionId: session.sessionId, reason: 'ACCOUNT_DISABLED' });
      revokeSessionSockets(req.app.get('io'), user.userId, revoked, 'ACCOUNT_DISABLED');
    }
    await audit(req, req.user!, 'STAFF_STATUS_SET_BY_MASTER', user.userId, { status }, user.companyId);
    emitToUser(req.app.get('io'), user.userId, 'account_changed', { status: updated.status, permissions: updated.permissions });
    emitToCompany(req.app.get('io'), user.companyId, 'staff_changed', { userId: user.userId });
    emitToUser(req.app.get('io'), MASTER_USER_ID, 'master_overview_changed', { companyId: user.companyId });
    return res.json({ success: true, user: updated });
  } catch (error) { return sendRouteError(res, req, error); }
});

router.delete('/staff/:userId', async (req: AuthenticatedRequest, res) => {
  try {
    const found = await providers().dataStore.findUserById(req.params.userId);
    const user = found && found.role === 'CASHIER' ? found : null;
    if (!user) throw new AppError(404, 'STAFF_NOT_FOUND', 'Staff account was not found');
    // Master Control presents this as a permanent erase, so it has to be one: kick the device
    // first, then drop the identity user, then the record and its phone reservation - otherwise
    // the number stays claimed and can never be reused.
    const revoked = await providers().sessionStore.revokeAllSessions(user.companyId, user.userId);
    for (const session of revoked) emitToUser(req.app.get('io'), user.userId, 'session_revoked', { sessionId: session.sessionId, reason: 'ACCOUNT_DELETED' });
    revokeSessionSockets(req.app.get('io'), user.userId, revoked, 'ACCOUNT_DELETED');
    await providers().identityProvider.deleteUser(user.userId);
    await providers().dataStore.deleteStaff(user.companyId, user.userId);
    await audit(req, req.user!, 'STAFF_DELETED_BY_MASTER', user.userId, { phone: user.phone }, user.companyId);
    emitToCompany(req.app.get('io'), user.companyId, 'staff_changed', { userId: user.userId });
    emitToUser(req.app.get('io'), MASTER_USER_ID, 'master_overview_changed', { companyId: user.companyId });
    return res.status(204).send();
  } catch (error) { return sendRouteError(res, req, error); }
});

export default router;
