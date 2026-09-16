import { Request, Response } from 'express';
import crypto, { randomUUID } from 'crypto';
import { AppError } from '../core/errors';
import { providers } from '../providers/providerRegistry';
import { sendRouteError } from '../routes/http';

/**
 * Stateless OTP: the server never stores the code. `requestId` is an HMAC-signed envelope that
 * carries the phone, a random one-time session id, an expiry, and a commitment `hmac(phone:code)`.
 * Verification recomputes the commitment from the submitted code and burns the *session id* (not
 * the commitment) via a single-use nonce — so two OTP sends for the same phone never collide even
 * when NODE_ENV=development pins the code to LOCAL_DEV_OTP_CODE for every send.
 */
const OTP_TTL_MS = 10 * 60 * 1000;
const RESET_TOKEN_TTL_MS = 10 * 60 * 1000;

export const otpSessions = {
  async consume(sessionId: string, phone: string): Promise<boolean> {
    const nonce = crypto.createHash('sha256').update(`${phone}:${sessionId}`).digest('hex');
    return providers().dataStore.consumeNonce('otp-session', nonce, Date.now() + 86_400_000);
  }
};

const resetSecret = (): string => {
  const secret = process.env.RESET_SECRET;
  if (!secret || secret.length < 32) throw new Error('RESET_SECRET must contain at least 32 characters');
  return secret;
};

export const createOtpSession = (phone: string, sessionId: string, commitment: string): string => {
  const payload = Buffer.from(JSON.stringify({ phone, sessionId, commitment, expires: Date.now() + OTP_TTL_MS })).toString('base64url');
  const signature = crypto.createHmac('sha256', resetSecret()).update(`otp-session:${payload}`).digest('hex');
  return `${payload}.${signature}`;
};

interface OtpSession { sessionId: string; commitment: string; }

export const readOtpSession = (phone: string, token: unknown): OtpSession | null => {
  try {
    if (typeof token !== 'string' || token.length > 1024) return null;
    const parts = token.split('.');
    if (parts.length !== 2 || !/^[a-f0-9]{64}$/.test(parts[1])) return null;
    const expected = crypto.createHmac('sha256', resetSecret()).update(`otp-session:${parts[0]}`).digest();
    if (!crypto.timingSafeEqual(expected, Buffer.from(parts[1], 'hex'))) return null;
    const session = JSON.parse(Buffer.from(parts[0], 'base64url').toString());
    if (session.phone !== phone || session.expires < Date.now() || typeof session.sessionId !== 'string' || typeof session.commitment !== 'string') return null;
    return { sessionId: session.sessionId, commitment: session.commitment };
  } catch { return null; }
};

export const normalizePhone = (phone: string): string => {
  const digits = String(phone || '').replace(/[^0-9]/g, '');
  return digits.length >= 10 ? digits.slice(-10) : digits;
};

const isValidIndianMobile = (phone: string): boolean => /^[6-9]\d{9}$/.test(phone);
const isValidOtp = (otp: string): boolean => /^\d{4,6}$/.test(otp);

const codeCommitment = (phone: string, code: string): string =>
  crypto.createHmac('sha256', resetSecret()).update(`${phone}:${code}`).digest('hex');

/** In development a fixed LOCAL_DEV_OTP_CODE keeps the emulator flow reproducible; production always randomizes. */
const generateCode = (): string => {
  const fixed = process.env.LOCAL_DEV_OTP_CODE || '';
  if (process.env.NODE_ENV !== 'production' && /^\d{4,6}$/.test(fixed)) return fixed;
  return String(crypto.randomInt(100000, 1000000));
};

export const sendOtp = async (req: Request, res: Response) => {
  try {
    resetSecret();
    const { mobileNumber } = req.body || {};
    if (!mobileNumber || typeof mobileNumber !== 'string') throw new AppError(400, 'OTP_MOBILE_REQUIRED', 'Mobile number is required');
    const cleanPhone = normalizePhone(mobileNumber);
    if (!isValidIndianMobile(cleanPhone)) throw new AppError(400, 'OTP_MOBILE_INVALID', 'Please provide a valid 10-digit mobile number');

    const code = generateCode();
    await providers().smsSender.sendOtp(cleanPhone, code);
    const requestId = createOtpSession(cleanPhone, randomUUID(), codeCommitment(cleanPhone, code));
    return res.status(200).json({ success: true, requestId, message: 'OTP sent successfully', expiresInSeconds: OTP_TTL_MS / 1000 });
  } catch (error) { return sendRouteError(res, req, error); }
};

export const verifyOtp = async (req: Request, res: Response) => {
  try {
    const { mobileNumber, otp, requestId } = req.body || {};
    if (!mobileNumber || !otp) throw new AppError(400, 'OTP_INPUT_REQUIRED', 'Mobile number and OTP are required');
    const cleanPhone = normalizePhone(String(mobileNumber));
    if (!isValidIndianMobile(cleanPhone)) throw new AppError(400, 'OTP_MOBILE_INVALID', 'Invalid 10-digit mobile number');
    const cleanOtp = String(otp).trim();
    if (!isValidOtp(cleanOtp)) throw new AppError(400, 'OTP_FORMAT_INVALID', 'Invalid OTP format. Must be 4-6 digits.');

    const session = readOtpSession(cleanPhone, requestId);
    if (!session) throw new AppError(400, 'OTP_SESSION_INVALID', 'Request a new OTP before verification.');

    const expected = Buffer.from(codeCommitment(cleanPhone, cleanOtp), 'hex');
    const actual = Buffer.from(session.commitment, 'hex');
    if (expected.length !== actual.length || !crypto.timingSafeEqual(expected, actual)) {
      throw new AppError(400, 'OTP_CODE_INVALID', 'Invalid or expired OTP code');
    }
    if (!await otpSessions.consume(session.sessionId, cleanPhone)) throw new AppError(409, 'OTP_SESSION_USED', 'OTP session already used. Request a fresh OTP.');
    return res.status(200).json({ success: true, message: 'OTP verified successfully', resetToken: createResetToken(cleanPhone) });
  } catch (error) { return sendRouteError(res, req, error); }
};

export const retryOtp = async (req: Request, res: Response) => sendOtp(req, res);

export const createResetToken = (phone: string, timestamp: number = Date.now()): string => {
  const cleanPhone = normalizePhone(phone);
  const payload = `${cleanPhone}:${timestamp}`;
  const signature = crypto.createHmac('sha256', resetSecret()).update(payload).digest('hex');
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
    if (tokenTs > Date.now() || Date.now() - tokenTs > RESET_TOKEN_TTL_MS) return false;
    const expectedSig = crypto.createHmac('sha256', resetSecret()).update(`${tokenPhone}:${tokenTsStr}`).digest('hex');
    const bufA = Buffer.from(tokenSig, 'hex');
    const bufB = Buffer.from(expectedSig, 'hex');
    if (bufA.length !== bufB.length) return false;
    return crypto.timingSafeEqual(bufA, bufB);
  } catch {
    return false;
  }
};
