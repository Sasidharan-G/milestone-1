import { Router } from 'express';
import { randomUUID } from 'node:crypto';
import { AppError } from '../core/errors';
import { AuthenticatedRequest, requireAuth } from '../middleware/authMiddleware';
import { providers } from '../providers/providerRegistry';
import { sendRouteError } from './http';

const router = Router();

router.post('/register', requireAuth, async (req: AuthenticatedRequest, res) => {
  try {
    const deviceId = String(req.body?.deviceId || '');
    if (!/^[a-zA-Z0-9_\-+:.]{8,128}$/.test(deviceId)) throw new AppError(400, 'SESSION_DEVICE_INVALID', 'A valid device identifier is required');
    const session = await providers().sessionStore.register({
      sessionId: randomUUID(), companyId: req.user!.companyId, userId: req.user!.userId, deviceId,
      expiresAtEpochMs: Date.now() + 30 * 24 * 60 * 60 * 1000
    });
    return res.status(201).json({ success: true, session });
  } catch (error) { return sendRouteError(res, req, error); }
});

router.post('/heartbeat', requireAuth, async (req: AuthenticatedRequest, res) => {
  try {
    if (typeof req.body?.sessionId !== 'string') throw new AppError(400, 'SESSION_ID_REQUIRED', 'Session identifier is required');
    const session = await providers().sessionStore.heartbeat(req.user!.companyId, req.user!.userId, req.body.sessionId);
    return res.json({ success: true, session });
  } catch (error) { return sendRouteError(res, req, error); }
});

router.delete('/current', requireAuth, async (req: AuthenticatedRequest, res) => {
  try {
    if (typeof req.body?.sessionId !== 'string') throw new AppError(400, 'SESSION_ID_REQUIRED', 'Session identifier is required');
    await providers().sessionStore.revoke(req.user!.companyId, req.user!.userId, req.body.sessionId);
    return res.json({ success: true });
  } catch (error) { return sendRouteError(res, req, error); }
});

router.delete('/:sessionId', requireAuth, async (req: AuthenticatedRequest, res) => {
  try {
    if (req.user!.role !== 'ADMIN' && req.params.sessionId !== req.body?.sessionId) throw new AppError(403, 'SESSION_REVOKE_FORBIDDEN', 'Administrator permission is required');
    await providers().sessionStore.revoke(req.user!.companyId, req.user!.userId, req.params.sessionId);
    return res.json({ success: true });
  } catch (error) { return sendRouteError(res, req, error); }
});

export default router;

