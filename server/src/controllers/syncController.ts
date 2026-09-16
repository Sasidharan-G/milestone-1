import { Response } from 'express';
import { AppError } from '../core/errors';
import { AuthenticatedRequest } from '../middleware/authMiddleware';
import { providers } from '../providers/providerRegistry';
import { sendRouteError } from '../routes/http';

/** Sync is provider-backed only. The authenticated tenant is the sole authority. */
export const pushSync = async (req: AuthenticatedRequest, res: Response) => {
  try {
    const operations = req.body?.operations;
    if (!Array.isArray(operations) || operations.length < 1 || operations.length > 50) {
      throw new AppError(400, 'SYNC_BATCH_INVALID', 'Send between 1 and 50 sync operations');
    }
    const companyId = req.user!.companyId;
    if (req.body?.companyId && req.body.companyId !== companyId) {
      throw new AppError(403, 'TENANT_MISMATCH', 'Requested tenant does not match the authenticated tenant');
    }
    const results = await providers().dataStore.applySyncBatch(companyId, operations.map((operation: any) => ({ ...operation, companyId })));
    const io = req.app.get('io');
    if (io) io.to(companyId).emit('data_changed', { companyId, timestamp: Date.now() });
    return res.json({ success: true, results });
  } catch (error) { return sendRouteError(res, req, error); }
};

export const pullSync = async (req: AuthenticatedRequest, res: Response) => {
  try {
    const companyId = req.user!.companyId;
    if (req.query.companyId && req.query.companyId !== companyId) {
      throw new AppError(403, 'TENANT_MISMATCH', 'Requested tenant does not match the authenticated tenant');
    }
    const cursor = typeof req.query.cursor === 'string' ? req.query.cursor : '';
    const requestedLimit = Number(req.query.limit || 200);
    const limit = Number.isSafeInteger(requestedLimit) ? Math.min(200, Math.max(1, requestedLimit)) : 200;
    const page = await providers().dataStore.pullSync(companyId, cursor, limit);
    return res.json({ success: true, ...page });
  } catch (error) { return sendRouteError(res, req, error); }
};
