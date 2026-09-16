import { Router } from 'express';
import { AppError } from '../core/errors';
import { audit } from '../core/audit';
import { applyLicenseAction, parseLicenseAction, presentLicense } from '../core/license';
import { emitToCompany, emitToUser } from '../core/realtime';
import { AuthenticatedRequest, PLATFORM_COMPANY_ID, requireAuth, requireSuperAdmin } from '../middleware/authMiddleware';
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
    return res.json({ success: true, license: presentLicense(license) });
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
      await providers().identityProvider.deleteUser(user.userId);
    }
    await providers().dataStore.deleteCompany(companyId);
    await audit(req, req.user!, 'COMPANY_DELETED', companyId, { users: companyUsers.map(user => user.userId), backups: backups.length });
    return res.status(204).send();
  } catch (error) { return sendRouteError(res, req, error); }
});

router.patch('/staff/:userId', async (req: AuthenticatedRequest, res) => {
  try {
    const overview = await providers().dataStore.adminOverview();
    const user = overview.users.find(item => item.userId === req.params.userId && item.role === 'CASHIER');
    if (!user) throw new AppError(404, 'STAFF_NOT_FOUND', 'Staff account was not found');
    const status = String(req.body?.status || user.status);
    if (!['ACTIVE', 'INACTIVE', 'PENDING_APPROVAL', 'REJECTED'].includes(status)) throw new AppError(400, 'STAFF_STATUS_INVALID', 'Staff status is invalid');
    const updated = await providers().dataStore.updateStaff(user.companyId, user.userId, { status: status as any, permissions: Array.isArray(req.body?.permissions) ? req.body.permissions.map(String) : user.permissions });
    if (status !== 'ACTIVE') {
      await providers().identityProvider.revokeUser(user.userId);
      const revoked = await providers().sessionStore.revokeAllSessions(user.companyId, user.userId);
      for (const session of revoked) emitToUser(req.app.get('io'), user.userId, 'session_revoked', { sessionId: session.sessionId, reason: 'ACCOUNT_DISABLED' });
    }
    await audit(req, req.user!, 'STAFF_STATUS_SET_BY_MASTER', user.userId, { status }, user.companyId);
    emitToUser(req.app.get('io'), user.userId, 'account_changed', { status: updated.status, permissions: updated.permissions });
    emitToCompany(req.app.get('io'), user.companyId, 'staff_changed', { userId: user.userId });
    return res.json({ success: true, user: updated });
  } catch (error) { return sendRouteError(res, req, error); }
});

router.delete('/staff/:userId', async (req: AuthenticatedRequest, res) => {
  try {
    const overview = await providers().dataStore.adminOverview();
    const user = overview.users.find(item => item.userId === req.params.userId && item.role === 'CASHIER');
    if (!user) throw new AppError(404, 'STAFF_NOT_FOUND', 'Staff account was not found');
    await providers().dataStore.updateStaff(user.companyId, user.userId, { status: 'INACTIVE', permissions: [] });
    await providers().identityProvider.revokeUser(user.userId);
    const revoked = await providers().sessionStore.revokeAllSessions(user.companyId, user.userId);
    for (const session of revoked) emitToUser(req.app.get('io'), user.userId, 'session_revoked', { sessionId: session.sessionId, reason: 'ACCOUNT_DISABLED' });
    await audit(req, req.user!, 'STAFF_DISABLED_BY_MASTER', user.userId, undefined, user.companyId);
    emitToCompany(req.app.get('io'), user.companyId, 'staff_changed', { userId: user.userId });
    return res.status(204).send();
  } catch (error) { return sendRouteError(res, req, error); }
});

export default router;
