import { Router } from 'express';
import { AppError } from '../core/errors';
import { presentLicense, RENEWAL_WARNING_DAYS, DAY_MS } from '../core/license';
import { AuthenticatedRequest, requireAuth } from '../middleware/authMiddleware';
import { providers } from '../providers/providerRegistry';
import { sendRouteError } from './http';

const router = Router();

/** Reachable even when the subscription has lapsed so the app can render the lock screen and renewal warning. */
router.get('/current', requireAuth, async (req: AuthenticatedRequest, res) => {
  try {
    const stored = await providers().dataStore.getLicense(req.user!.companyId);
    if (!stored) throw new AppError(404, 'LICENSE_NOT_FOUND', 'Company license was not found');
    const license = presentLicense(stored);
    const remainingMs = license.validUntilEpochMs - Date.now();
    const active = license.status === 'TRIAL' || license.status === 'ACTIVE_PAID';
    const shopProfile = await providers().dataStore.getShopProfile(req.user!.companyId);
    return res.json({
      success: true,
      license,
      shopProfile,
      access: {
        active,
        daysRemaining: active ? Math.max(0, Math.ceil(remainingMs / DAY_MS)) : 0,
        renewalWarning: active && remainingMs <= RENEWAL_WARNING_DAYS * DAY_MS,
        serverTimeEpochMs: Date.now()
      }
    });
  } catch (error) { return sendRouteError(res, req, error); }
});

export default router;
