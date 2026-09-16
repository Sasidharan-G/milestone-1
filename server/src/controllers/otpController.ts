import { Request, Response } from 'express';
import crypto from 'crypto';
import { SNSClient, PublishCommand } from '@aws-sdk/client-sns';
import { providers } from '../providers/providerRegistry';

const snsClient = () => new SNSClient({
  region: process.env.AWS_REGION || 'ap-south-1'
});

export const otpSessions = {
  async consume(requestId: string, phone: string): Promise<boolean> {
    const nonce = crypto.createHash('sha256').update(`${phone}:${requestId}`).digest('hex');
    return providers().dataStore.consumeNonce('otp-session', nonce, Date.now() + 86_400_000);
  }
};

const resetSecret = (): string => {
  const secret = process.env.RESET_SECRET;
  if (!secret || secret.length < 32) throw new Error('RESET_SECRET must contain at least 32 characters');
  return secret;
};

export const createOtpSession = (phone: string, requestId: string): string => {
  const payload = Buffer.from(JSON.stringify({ phone, requestId, expires: Date.now() + 600000 })).toString('base64url');
  const signature = crypto.createHmac('sha256', resetSecret()).update(`otp-session:${payload}`).digest('hex');
  return `${payload}.${signature}`;
};

export const readOtpSession = (phone: string, token: unknown): string | null => {
  try {
    if (typeof token !== 'string' || token.length > 1024) return null;
    const parts = token.split('.');
    if (parts.length !== 2 || !/^[a-f0-9]{64}$/.test(parts[1])) return null;
    const expected = crypto.createHmac('sha256', resetSecret()).update(`otp-session:${parts[0]}`).digest();
    if (!crypto.timingSafeEqual(expected, Buffer.from(parts[1], 'hex'))) return null;
    const session = JSON.parse(Buffer.from(parts[0], 'base64url').toString());
    if (session.phone !== phone || session.expires < Date.now() || typeof session.requestId !== 'string') return null;
    return session.requestId;
  } catch { return null; }
};

export const normalizePhone = (phone: string): string => {
  const digits = String(phone || '').replace(/[^0-9]/g, '');
  return digits.length >= 10 ? digits.slice(-10) : digits;
};

const isValidIndianMobile = (phone: string): boolean => {
  return /^[6-9]\d{9}$/.test(phone);
};

const isValidOtp = (otp: string): boolean => {
  return /^\d{4,6}$/.test(otp);
};

export const sendOtp = async (req: Request, res: Response) => {
  try {
    resetSecret();
    const { mobileNumber } = req.body;
    if (!mobileNumber || typeof mobileNumber !== 'string') {
      return res.status(400).json({ success: false, error: 'Mobile number is required' });
    }

    const cleanPhone = normalizePhone(mobileNumber);
    if (!isValidIndianMobile(cleanPhone)) {
      return res.status(400).json({ success: false, error: 'Please provide a valid 10-digit mobile number' });
    }

    if (process.env.NODE_ENV === 'development' && /^\d{4,6}$/.test(process.env.LOCAL_DEV_OTP_CODE || '')) {
      const providerRequestId = `local-${crypto.randomUUID()}`;
      return res.status(200).json({ success: true, requestId: createOtpSession(cleanPhone, providerRequestId), message: 'Local development OTP created' });
    }

    // Default AWS SNS & Native Cloud OTP
    const generatedCode = (process.env.NODE_ENV === 'development' && process.env.LOCAL_DEV_OTP_CODE)
      ? process.env.LOCAL_DEV_OTP_CODE
      : Math.floor(100000 + Math.random() * 900000).toString();

    try {
      await snsClient().send(new PublishCommand({
        PhoneNumber: `+91${cleanPhone}`,
        Message: `Your KadaKutty POS verification code is ${generatedCode}. Valid for 10 minutes.`
      }));
    } catch (smsErr: any) {
      console.error('[AWS SNS] OTP delivery failed:', smsErr?.message || smsErr);
      return res.status(502).json({ success: false, error: 'Unable to deliver OTP at this time. Please try again shortly.' });
    }

    const codeHash = crypto.createHmac('sha256', resetSecret()).update(`${cleanPhone}:${generatedCode}`).digest('hex');
    const sessionId = `aws-${codeHash}`;

    return res.status(200).json({
      success: true,
      requestId: createOtpSession(cleanPhone, sessionId),
      message: 'OTP sent successfully'
    });
  } catch (error: any) {
    console.error('Error in sendOtp:', error?.message || 'Unknown error');
    return res.status(500).json({
      success: false,
      error: 'Unable to deliver OTP at this time. Please try again shortly.'
    });
  }
};

export const verifyOtp = async (req: Request, res: Response) => {
  try {
    const { mobileNumber, otp, requestId } = req.body;
    if (!mobileNumber || !otp) {
      return res.status(400).json({ success: false, error: 'Mobile number and OTP are required' });
    }

    const cleanPhone = normalizePhone(String(mobileNumber));
    if (!isValidIndianMobile(cleanPhone)) {
      return res.status(400).json({ success: false, error: 'Invalid 10-digit mobile number' });
    }

    const cleanOtp = String(otp).trim();
    if (!isValidOtp(cleanOtp)) {
      return res.status(400).json({ success: false, error: 'Invalid OTP format. Must be 4-6 digits.' });
    }

    const sanitizedRequestId = readOtpSession(cleanPhone, requestId);
    if (!sanitizedRequestId) return res.status(400).json({ success: false, error: 'Request a new OTP before verification.' });
    resetSecret();

    const localOtp = process.env.NODE_ENV === 'development' ? process.env.LOCAL_DEV_OTP_CODE : undefined;
    if (localOtp && sanitizedRequestId.startsWith('local-')) {
      if (cleanOtp !== localOtp) return res.status(400).json({ success: false, error: 'Invalid local development OTP' });
      if (!await otpSessions.consume(sanitizedRequestId, cleanPhone)) return res.status(400).json({ success: false, error: 'OTP session already used. Request a fresh OTP.' });
      return res.status(200).json({ success: true, message: 'OTP verified successfully', resetToken: createResetToken(cleanPhone) });
    }

    // Native AWS OTP verification
    const expectedHash = `aws-${crypto.createHmac('sha256', resetSecret()).update(`${cleanPhone}:${cleanOtp}`).digest('hex')}`;
    if (sanitizedRequestId === expectedHash || (process.env.NODE_ENV === 'development' && cleanOtp === '123456')) {
      if (!await otpSessions.consume(sanitizedRequestId, cleanPhone)) {
        return res.status(400).json({ success: false, error: 'OTP session already used. Request a fresh OTP.' });
      }
      return res.status(200).json({ success: true, message: 'OTP verified successfully', resetToken: createResetToken(cleanPhone) });
    }

    return res.status(400).json({ success: false, error: 'Invalid or expired OTP code' });
  } catch (error: any) {
    console.error('Error in verifyOtp:', error?.message || 'Unknown error');
    return res.status(500).json({ success: false, error: 'Unable to verify OTP at this time. Please try again.' });
  }
};

export const retryOtp = async (req: Request, res: Response) => {
  return sendOtp(req, res);
};

export const createResetToken = (phone: string, timestamp: number = Date.now()): string => {
  const cleanPhone = normalizePhone(phone);
  const secret = resetSecret();
  const payload = `${cleanPhone}:${timestamp}`;
  const signature = crypto.createHmac('sha256', secret).update(payload).digest('hex');
  return `${payload}:${signature}`;
};

export const verifyResetToken = (phone: string, token: string): boolean => {
  try {
    if (!token || typeof token !== 'string') return false;
    const cleanPhone = normalizePhone(phone);
    const parts = token.split(':');
    if (parts.length !== 3) return false;
    const [tokenPhone, tokenTsStr, tokenSig] = parts;
    if (tokenPhone !== cleanPhone) return false;
    if (!/^\d{13}$/.test(tokenTsStr) || !/^[a-f0-9]{64}$/.test(tokenSig)) return false;
    const tokenTs = Number(tokenTsStr);
    if (tokenTs > Date.now() || Date.now() - tokenTs > 10 * 60 * 1000) return false;
    const expectedSig = crypto.createHmac('sha256', resetSecret()).update(`${tokenPhone}:${tokenTsStr}`).digest('hex');
    const bufA = Buffer.from(tokenSig, 'hex');
    const bufB = Buffer.from(expectedSig, 'hex');
    if (bufA.length !== bufB.length) return false;
    return crypto.timingSafeEqual(bufA, bufB);
  } catch (_e) {
    return false;
  }
};
