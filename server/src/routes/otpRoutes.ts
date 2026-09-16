import { Router } from 'express';
import { sendOtp, verifyOtp } from '../controllers/otpController';
import { limitOtpSend, limitOtpVerify } from '../middleware/rateLimitMiddleware';

const router = Router();

router.post('/send', limitOtpSend, sendOtp);
router.post('/verify', limitOtpVerify, verifyOtp);
router.post('/retry', limitOtpSend, sendOtp);

export default router;
