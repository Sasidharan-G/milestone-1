import { Router } from 'express';
import { requireAuth } from '../middleware/authMiddleware';
import { limitSyncRequests } from '../middleware/rateLimitMiddleware';
import { pushSync, pullSync } from '../controllers/syncController';
import { AuthenticatedRequest } from '../middleware/authMiddleware';
import { providers } from '../providers/providerRegistry';
import { AppError } from '../core/errors';
import { sendRouteError } from './http';

const router = Router();

router.post('/push', limitSyncRequests, requireAuth, pushSync);
router.get('/pull', limitSyncRequests, requireAuth, pullSync);
router.post('/purge', limitSyncRequests, requireAuth, async (req: AuthenticatedRequest, res) => {
  try {
    if (req.user!.role !== 'ADMIN' || !req.user!.permissions.includes('SETTINGS_EDIT')) throw new AppError(403, 'SYNC_PURGE_FORBIDDEN', 'Administrator settings permission is required');
    const deletedRecords = await providers().dataStore.purgeCompanyRecords(req.user!.companyId);
    return res.json({ success: true, deletedRecords });
  } catch (error) { return sendRouteError(res, req, error); }
});

export default router;
