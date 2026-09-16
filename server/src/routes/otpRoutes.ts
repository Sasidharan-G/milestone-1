import { Router } from 'express';
import { sendOtp, verifyOtp, retryOtp } from '../controllers/otpController';
import { limitOtpSend, limitOtpVerify, limitOtpRetry } from '../middleware/rateLimitMiddleware';

const router = Router();

router.post('/send', limitOtpSend, sendOtp);
router.post('/verify', limitOtpVerify, verifyOtp);
router.post('/retry', limitOtpSend, sendOtp);

export default router;
