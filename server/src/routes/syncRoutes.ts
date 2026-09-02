import { Router } from 'express';
import { requireAuth } from '../middleware/authMiddleware';
import { pushSync, pullSync } from '../controllers/syncController';

const router = Router();

router.post('/push', requireAuth, pushSync);
router.get('/pull', requireAuth, pullSync);

export default router;
