import express, { Router } from 'express';
import { AppError } from '../core/errors';
import { AuthenticatedRequest, requireActiveLicense, requireAuth } from '../middleware/authMiddleware';
import { providers } from '../providers/providerRegistry';
import { sendRouteError } from './http';

const router = Router();
const checksumPattern = /^[a-f0-9]{64}$/;

router.post('/upload-intent', requireAuth, requireActiveLicense, async (req: AuthenticatedRequest, res) => {
  try {
    if (req.user!.role !== 'ADMIN' && !req.user!.permissions.includes('BACKUP_CREATE')) throw new AppError(403, 'BACKUP_FORBIDDEN', 'Backup permission is required');
    const { fileName, sizeBytes, checksumSha256, schemaVersion } = req.body || {};
    if (typeof fileName !== 'string' || fileName.length < 1 || fileName.length > 180 || !Number.isSafeInteger(sizeBytes) || sizeBytes <= 0 || sizeBytes > 512 * 1024 * 1024 || !checksumPattern.test(String(checksumSha256 || '').toLowerCase()) || !Number.isSafeInteger(schemaVersion)) {
      throw new AppError(400, 'BACKUP_METADATA_INVALID', 'Backup metadata is invalid');
    }
    const intent = await providers().objectStorage.createUploadIntent({ companyId: req.user!.companyId, fileName, sizeBytes, checksumSha256: checksumSha256.toLowerCase(), schemaVersion });
    return res.status(201).json({ success: true, ...intent, uploadMethod: 'PUT', requiredHeaders: intent.requiredHeaders || { 'Content-Type': 'application/octet-stream' } });
  } catch (error) { return sendRouteError(res, req, error); }
});

router.put('/:backupId/content', requireAuth, requireActiveLicense, express.raw({ type: 'application/octet-stream', limit: '512mb' }), async (req: AuthenticatedRequest, res) => {
  try {
    if (!Buffer.isBuffer(req.body)) throw new AppError(400, 'BACKUP_CONTENT_REQUIRED', 'Binary backup content is required');
    await providers().objectStorage.writeLocalContent(req.user!.companyId, req.params.backupId, req.body);
    return res.status(204).send();
  } catch (error) { return sendRouteError(res, req, error); }
});

router.post('/:backupId/complete', requireAuth, requireActiveLicense, async (req: AuthenticatedRequest, res) => {
  try {
    return res.json({ success: true, backup: await providers().objectStorage.complete(req.user!.companyId, req.params.backupId) });
  } catch (error) { return sendRouteError(res, req, error); }
});

router.get('/', requireAuth, requireActiveLicense, async (req: AuthenticatedRequest, res) => {
  try { return res.json({ success: true, backups: await providers().objectStorage.list(req.user!.companyId) }); }
  catch (error) { return sendRouteError(res, req, error); }
});

router.post('/:backupId/download-intent', requireAuth, requireActiveLicense, async (req: AuthenticatedRequest, res) => {
  try { return res.json({ success: true, ...await providers().objectStorage.createDownloadIntent(req.user!.companyId, req.params.backupId) }); }
  catch (error) { return sendRouteError(res, req, error); }
});

router.get('/:backupId/content', requireAuth, requireActiveLicense, async (req: AuthenticatedRequest, res) => {
  try {
    const content = await providers().objectStorage.readLocalContent(req.user!.companyId, req.params.backupId);
    res.type('application/octet-stream');
    return res.send(content);
  } catch (error) { return sendRouteError(res, req, error); }
});

router.delete('/:backupId', requireAuth, requireActiveLicense, async (req: AuthenticatedRequest, res) => {
  try {
    await providers().objectStorage.delete(req.user!.companyId, req.params.backupId);
    return res.status(204).send();
  } catch (error) { return sendRouteError(res, req, error); }
});

export default router;
