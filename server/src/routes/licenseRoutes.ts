import { Router } from 'express';
import { AppError } from '../core/errors';
import { AuthenticatedRequest, requireAuth } from '../middleware/authMiddleware';
import { providers } from '../providers/providerRegistry';
import { sendRouteError } from './http';

const router = Router();

router.get('/current', requireAuth, async (req: AuthenticatedRequest, res) => {
  try {
    const license = await providers().dataStore.getLicense(req.user!.companyId);
    if (!license) throw new AppError(404, 'LICENSE_NOT_FOUND', 'Company license was not found');
    return res.json({ success: true, license });
  } catch (error) { return sendRouteError(res, req, error); }
});

export default router;

