import { Router } from 'express';
import { requireActiveLicense, requireAuth } from '../middleware/authMiddleware';
import { limitSyncRequests } from '../middleware/rateLimitMiddleware';
import { pushSync, pullSync } from '../controllers/syncController';
import { AuthenticatedRequest } from '../middleware/authMiddleware';
import { providers } from '../providers/providerRegistry';
import { AppError } from '../core/errors';
import { sendRouteError } from './http';
import { audit } from '../core/audit';
import { emitToCompany } from '../core/realtime';

const router = Router();

router.post('/push', limitSyncRequests, requireAuth, requireActiveLicense, pushSync);
router.get('/pull', limitSyncRequests, requireAuth, requireActiveLicense, pullSync);
router.post('/purge', limitSyncRequests, requireAuth, requireActiveLicense, async (req: AuthenticatedRequest, res) => {
  try {
    if (req.user!.role !== 'ADMIN' && req.user!.role !== 'SUPER_ADMIN' && !req.user!.permissions?.includes('SETTINGS_EDIT')) {
      throw new AppError(403, 'SYNC_PURGE_FORBIDDEN', 'Administrator settings permission is required');
    }
    const companyId = req.user!.companyId;
    // Moved on before the purge so no device can push old data into the half-emptied store, and
    // again after it so a device that reset while the purge was still running resets once more
    // rather than keeping records the purge deleted after it had pulled them.
    await providers().dataStore.bumpSyncEpoch(companyId);
    const deletedRecords = await providers().dataStore.purgeCompanyRecords(companyId);
    const epoch = await providers().dataStore.bumpSyncEpoch(companyId);
    await audit(req, req.user!, 'SYNC_PURGED', undefined, { deletedRecords, epoch });
    emitToCompany(req.app.get('io'), companyId, 'data_purged', { epoch, sourceSessionId: req.user!.sessionId });
    return res.json({ success: true, deletedRecords, epoch });
  } catch (error) { return sendRouteError(res, req, error); }
});

export default router;
