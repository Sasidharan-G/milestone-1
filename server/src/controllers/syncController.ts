import { Response } from 'express';
import { AppError } from '../core/errors';
import { AuthenticatedRequest } from '../middleware/authMiddleware';
import { providers } from '../providers/providerRegistry';
import { sendRouteError } from '../routes/http';
import { emitToCompany } from '../core/realtime';

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
    // A device still holding data from before a purge must not push it back. Any client that
    // sends no epoch is treated as epoch 0, so an old build is refused after the first purge too.
    const epoch = await providers().dataStore.getSyncEpoch(companyId);
    const clientEpoch = Number(req.body?.epoch ?? 0);
    if (clientEpoch !== epoch) {
      throw new AppError(409, 'SYNC_EPOCH_STALE', 'The cloud data for this shop was cleared. This device must reset before it can sync.', false, { epoch });
    }
    for (const operation of operations) {
      const payloadTenant = operation?.payload?.companyId;
      if (payloadTenant !== undefined && payloadTenant !== companyId) {
        throw new AppError(403, 'TENANT_MISMATCH', 'Record tenant does not match the authenticated tenant', false, { operationId: operation?.operationId });
      }
    }
    const results = await providers().dataStore.applySyncBatch(companyId, operations.map((operation: any) => ({ ...operation, companyId })),
      { role: req.user!.role, permissions: req.user!.permissions });
    // Only a real write is news to the other devices; a batch of no-ops, replays or conflicts
    // used to make every device in the shop pull again for nothing.
    if (results.some(result => result.status === 'APPLIED')) {
      emitToCompany(req.app.get('io'), companyId, 'data_changed', { sourceSessionId: req.user!.sessionId });
    }
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
    const epoch = await providers().dataStore.getSyncEpoch(companyId);
    const page = await providers().dataStore.pullSync(companyId, cursor, limit);
    return res.json({ success: true, epoch, ...page });
  } catch (error) { return sendRouteError(res, req, error); }
};
