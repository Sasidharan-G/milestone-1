import express, { Router } from 'express';
import { AppError } from '../core/errors';
import { AuthenticatedRequest, requireActiveLicense, requireAuth } from '../middleware/authMiddleware';
import { providers } from '../providers/providerRegistry';
import { sendRouteError } from './http';

const router = Router();
const ALLOWED_CONTENT_TYPES = new Set(['image/jpeg', 'image/png']);
const MAX_IMAGE_BYTES = 8 * 1024 * 1024;

const requireProductWriteAccess = (req: AuthenticatedRequest) => {
  const { role, permissions } = req.user!;
  const allowed = role === 'ADMIN' || role === 'SUPER_ADMIN' || permissions?.includes('PRODUCT_CREATE') || permissions?.includes('PRODUCT_EDIT');
  if (!allowed) throw new AppError(403, 'PRODUCT_IMAGE_FORBIDDEN', 'Product edit permission is required');
};

router.post('/:productId/image-upload-url', requireAuth, requireActiveLicense, async (req: AuthenticatedRequest, res) => {
  try {
    requireProductWriteAccess(req);
    const contentType = String(req.body?.contentType || '');
    if (!ALLOWED_CONTENT_TYPES.has(contentType)) {
      throw new AppError(400, 'PRODUCT_IMAGE_TYPE_INVALID', 'Only image/jpeg or image/png photos are supported');
    }
    const intent = await providers().objectStorage.createProductImageUploadUrl(req.user!.companyId, req.params.productId, contentType);
    return res.status(201).json({ success: true, ...intent, uploadMethod: 'PUT', requiredHeaders: intent.requiredHeaders || { 'Content-Type': contentType } });
  } catch (error) { return sendRouteError(res, req, error); }
});

// Used by the local (non-AWS) provider only: AWS mode uploads straight to the presigned S3 URL
// above and never calls this. Kept behind the same auth as every other tenant write.
router.put('/:productId/image-content', requireAuth, requireActiveLicense, express.raw({ type: ['image/jpeg', 'image/png'], limit: '8mb' }), async (req: AuthenticatedRequest, res) => {
  try {
    requireProductWriteAccess(req);
    if (!Buffer.isBuffer(req.body) || req.body.length === 0) throw new AppError(400, 'PRODUCT_IMAGE_CONTENT_REQUIRED', 'Image content is required');
    if (req.body.length > MAX_IMAGE_BYTES) throw new AppError(413, 'PRODUCT_IMAGE_TOO_LARGE', 'Product photo exceeds the maximum supported size');
    await providers().objectStorage.writeProductImageContent(req.user!.companyId, req.params.productId, req.body);
    return res.status(204).send();
  } catch (error) { return sendRouteError(res, req, error); }
});

router.get('/:productId/image', requireAuth, requireActiveLicense, async (req: AuthenticatedRequest, res) => {
  try {
    const content = await providers().objectStorage.readProductImageContent(req.user!.companyId, req.params.productId);
    res.type('image/jpeg');
    return res.send(content);
  } catch (error) { return sendRouteError(res, req, error); }
});

export default router;
