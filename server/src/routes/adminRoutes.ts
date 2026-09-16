import { Router } from 'express';
import { AppError } from '../core/errors';
import { AuthenticatedRequest, requireAuth } from '../middleware/authMiddleware';
import { providers } from '../providers/providerRegistry';
import { sendRouteError } from './http';

const router = Router();
router.use(requireAuth);
router.use((req: AuthenticatedRequest, _res, next) => req.user?.role === 'SUPER_ADMIN' ? next() : next(new AppError(403, 'PLATFORM_ADMIN_REQUIRED', 'Platform administrator access is required')));

router.get('/overview', async (req, res) => {
  try {
    const overview = await providers().dataStore.adminOverview();
    return res.json({ success: true, ...overview, masterConfig: { mobile: overview.masterConfig.mobile, updatedAtEpochMs: overview.masterConfig.updatedAtEpochMs, pinConfigured: Boolean(overview.masterConfig.pin) } });
  }
  catch (error) { return sendRouteError(res, req, error); }
});

router.patch('/config', async (req, res) => {
  try {
    const mobile = String(req.body?.mobile || '').replace(/\D/g, '').slice(-10);
    const pin = String(req.body?.pin || '');
    if (!/^[6-9]\d{9}$/.test(mobile) || !/^\d{6,12}$/.test(pin)) throw new AppError(400, 'MASTER_CONFIG_INVALID', 'Valid master mobile and 6-12 digit PIN are required');
    const config = await providers().dataStore.updateMasterConfig({ mobile, pin });
    return res.json({ success: true, masterConfig: { mobile: config.mobile, updatedAtEpochMs: config.updatedAtEpochMs, pinConfigured: true } });
  } catch (error) { return sendRouteError(res, req, error); }
});

router.patch('/licenses/:companyId', async (req, res) => {
  try {
    const status = String(req.body?.status || '');
    if (!['TRIAL', 'ACTIVE', 'EXPIRED', 'SUSPENDED'].includes(status)) throw new AppError(400, 'LICENSE_STATUS_INVALID', 'License status is invalid');
    const validUntilEpochMs = Number(req.body?.validUntilEpochMs || 0);
    return res.json({ success: true, license: await providers().dataStore.updateLicense(req.params.companyId, { status: status as any, validUntilEpochMs }) });
  } catch (error) { return sendRouteError(res, req, error); }
});

router.delete('/companies/:companyId', async (req, res) => {
  try {
    const overview = await providers().dataStore.adminOverview();
    const companyUsers = overview.users.filter(user => user.companyId === req.params.companyId);
    const backups = await providers().objectStorage.list(req.params.companyId);
    await Promise.all(backups.map(backup => providers().objectStorage.delete(req.params.companyId, backup.backupId)));
    await Promise.all(companyUsers.map(user => providers().identityProvider.deleteUser(user.userId)));
    await providers().dataStore.deleteCompany(req.params.companyId);
    return res.status(204).send();
  }
  catch (error) { return sendRouteError(res, req, error); }
});

router.patch('/staff/:userId', async (req, res) => {
  try {
    const overview = await providers().dataStore.adminOverview();
    const user = overview.users.find(item => item.userId === req.params.userId && item.role === 'CASHIER');
    if (!user) throw new AppError(404, 'STAFF_NOT_FOUND', 'Staff account was not found');
    const status = String(req.body?.status || user.status);
    if (!['ACTIVE', 'INACTIVE', 'PENDING_APPROVAL'].includes(status)) throw new AppError(400, 'STAFF_STATUS_INVALID', 'Staff status is invalid');
    const updated = await providers().dataStore.updateStaff(user.companyId, user.userId, { status: status as any, permissions: Array.isArray(req.body?.permissions) ? req.body.permissions : user.permissions });
    if (status !== 'ACTIVE') await providers().identityProvider.revokeUser(user.userId);
    return res.json({ success: true, user: updated });
  } catch (error) { return sendRouteError(res, req, error); }
});

router.delete('/staff/:userId', async (req, res) => {
  try {
    const overview = await providers().dataStore.adminOverview();
    const user = overview.users.find(item => item.userId === req.params.userId && item.role === 'CASHIER');
    if (!user) throw new AppError(404, 'STAFF_NOT_FOUND', 'Staff account was not found');
    await providers().dataStore.updateStaff(user.companyId, user.userId, { status: 'INACTIVE', permissions: [] });
    await providers().identityProvider.revokeUser(user.userId);
    return res.status(204).send();
  } catch (error) { return sendRouteError(res, req, error); }
});

export default router;
