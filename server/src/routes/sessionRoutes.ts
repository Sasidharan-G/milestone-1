import { Router } from 'express';
import { randomUUID } from 'node:crypto';
import { AppError } from '../core/errors';
import { audit } from '../core/audit';
import { emitToUser } from '../core/realtime';
import { AuthenticatedRequest, requireAuth, requireAuthWithoutSession } from '../middleware/authMiddleware';
import { providers } from '../providers/providerRegistry';
import { sendRouteError } from './http';

const router = Router();
const SESSION_LIFETIME_MS = 30 * 24 * 60 * 60 * 1000;

/** Registers this device and signs every other device of the user out (single active device). */
router.post('/register', requireAuthWithoutSession, async (req: AuthenticatedRequest, res) => {
  try {
    const deviceId = String(req.body?.deviceId || '');
    const deviceName = typeof req.body?.deviceName === 'string' ? req.body.deviceName.slice(0, 80) : undefined;
    if (!/^[a-zA-Z0-9_\-+:.]{8,128}$/.test(deviceId)) throw new AppError(400, 'SESSION_DEVICE_INVALID', 'A valid device identifier is required');
    const session = await providers().sessionStore.register({
      sessionId: randomUUID(), companyId: req.user!.companyId, userId: req.user!.userId, deviceId, deviceName,
      expiresAtEpochMs: Date.now() + SESSION_LIFETIME_MS
    });
    const revoked = await providers().sessionStore.revokeOtherSessions(req.user!.companyId, req.user!.userId, session.sessionId);
    for (const old of revoked) {
      emitToUser(req.app.get('io'), req.user!.userId, 'session_revoked', { sessionId: old.sessionId, reason: 'SIGNED_IN_ELSEWHERE', deviceName: deviceName || 'another device' });
    }
    await audit(req, req.user!, 'SESSION_REGISTERED', session.sessionId, { deviceId, deviceName, revokedSessions: revoked.map(item => item.sessionId) });
    return res.status(201).json({ success: true, session, revokedSessions: revoked.length });
  } catch (error) { return sendRouteError(res, req, error); }
});

router.post('/heartbeat', requireAuth, async (req: AuthenticatedRequest, res) => {
  try {
    const sessionId = typeof req.body?.sessionId === 'string' ? req.body.sessionId : req.user!.sessionId!;
    if (sessionId !== req.user!.sessionId) throw new AppError(401, 'SESSION_REVOKED', 'This device session was signed out. Please sign in again.');
    const session = await providers().sessionStore.heartbeat(req.user!.companyId, req.user!.userId, sessionId);
    return res.json({ success: true, session });
  } catch (error) { return sendRouteError(res, req, error); }
});

/** Sign out of this device. */
router.delete('/current', requireAuth, async (req: AuthenticatedRequest, res) => {
  try {
    await providers().sessionStore.revoke(req.user!.companyId, req.user!.userId, req.user!.sessionId!);
    await audit(req, req.user!, 'SESSION_SIGNED_OUT', req.user!.sessionId);
    return res.json({ success: true });
  } catch (error) { return sendRouteError(res, req, error); }
});

router.delete('/:sessionId', requireAuth, async (req: AuthenticatedRequest, res) => {
  try {
    if (req.user!.role !== 'ADMIN' && req.params.sessionId !== req.user!.sessionId) throw new AppError(403, 'SESSION_REVOKE_FORBIDDEN', 'Administrator permission is required');
    await providers().sessionStore.revoke(req.user!.companyId, req.user!.userId, req.params.sessionId);
    await audit(req, req.user!, 'SESSION_REVOKED_BY_ADMIN', req.params.sessionId);
    return res.json({ success: true });
  } catch (error) { return sendRouteError(res, req, error); }
});

export default router;
