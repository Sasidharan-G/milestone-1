import { Request, Response, NextFunction } from 'express';
import { normalizePhone } from '../controllers/otpController';
import { AppError, errorBody } from '../core/errors';

interface RateLimitRecord {
  count: number;
  firstRequestTime: number;
  lastRequestTime: number;
}

class InMemoryRateLimiter {
  private store: Map<string, RateLimitRecord> = new Map();
  private windowMs: number;
  private maxRequests: number;
  private cooldownMs: number;

  constructor(windowMs: number, maxRequests: number, cooldownMs: number = 0) {
    this.windowMs = windowMs;
    this.maxRequests = maxRequests;
    this.cooldownMs = cooldownMs;

    // Periodically clean up expired entries every 5 minutes
    setInterval(() => this.cleanup(), 5 * 60 * 1000).unref();
  }

  private cleanup() {
    const now = Date.now();
    for (const [key, record] of this.store.entries()) {
      if (now - record.firstRequestTime > this.windowMs) {
        this.store.delete(key);
      }
    }
  }

  public check(key: string): { allowed: boolean; retryAfterSeconds?: number; reason?: string } {
    const now = Date.now();
    const record = this.store.get(key);

    if (!record) {
      this.store.set(key, {
        count: 1,
        firstRequestTime: now,
        lastRequestTime: now
      });
      return { allowed: true };
    }

    // Check if the current window has expired
    if (now - record.firstRequestTime > this.windowMs) {
      this.store.set(key, {
        count: 1,
        firstRequestTime: now,
        lastRequestTime: now
      });
      return { allowed: true };
    }

    // Check cooldown between successive requests
    if (this.cooldownMs > 0 && (now - record.lastRequestTime < this.cooldownMs)) {
      const waitTime = Math.ceil((this.cooldownMs - (now - record.lastRequestTime)) / 1000);
      return {
        allowed: false,
        retryAfterSeconds: waitTime,
        reason: `Please wait ${waitTime} seconds before requesting another code.`
      };
    }

    // Check request count limit
    if (record.count >= this.maxRequests) {
      const remainingWindowSec = Math.ceil((this.windowMs - (now - record.firstRequestTime)) / 1000);
      return {
        allowed: false,
        retryAfterSeconds: remainingWindowSec,
        reason: `Too many attempts. Please try again in ${Math.ceil(remainingWindowSec / 60)} minutes.`
      };
    }

    record.count++;
    record.lastRequestTime = now;
    return { allowed: true };
  }

  public reset(key: string) {
    this.store.delete(key);
  }
}

// 1. OTP Send Limiter: Max 5 sends per 10 minutes, 20 seconds cooldown between sends
const otpSendLimiter = new InMemoryRateLimiter(10 * 60 * 1000, 5, 20 * 1000);

// 2. OTP Verify Limiter: Max 10 verification attempts per 10 minutes to prevent brute-forcing
const otpVerifyLimiter = new InMemoryRateLimiter(10 * 60 * 1000, 10, 0);

// 3. OTP Retry Limiter: Max 5 retries per 10 minutes, 20 seconds cooldown

// 4. Sync API Limiter: Max 120 requests per minute
const syncLimiter = new InMemoryRateLimiter(60 * 1000, 120, 0);

// 5. Login limiter: 10 attempts per 10 minutes per phone (or IP when no phone is given)
const loginLimiter = new InMemoryRateLimiter(10 * 60 * 1000, 10, 0);

const tooMany = (res: Response, req: Request, message: string, retryAfterSeconds?: number) => {
  if (retryAfterSeconds) res.setHeader('Retry-After', String(retryAfterSeconds));
  return res.status(429).json(errorBody(new AppError(429, 'RATE_LIMITED', message, true, { retryAfterSeconds }), (req as any).id || 'unknown'));
};

const getClientIp = (req: Request): string => {
  return req.ip || req.socket.remoteAddress || 'unknown';
};

export const limitOtpSend = (req: Request, res: Response, next: NextFunction) => {
  const phone = normalizePhone(req.body?.mobileNumber || '');
  const ip = getClientIp(req);
  const key = phone.length >= 10 ? `otp_send_${phone}` : `otp_send_ip_${ip}`;

  const result = otpSendLimiter.check(key);
  if (!result.allowed) return tooMany(res, req, result.reason || 'Too many OTP requests. Please wait before retrying.', result.retryAfterSeconds);
  next();
};

export const limitOtpVerify = (req: Request, res: Response, next: NextFunction) => {
  const phone = normalizePhone(req.body?.mobileNumber || '');
  const ip = getClientIp(req);
  const key = phone.length >= 10 ? `otp_verify_${phone}` : `otp_verify_ip_${ip}`;

  const result = otpVerifyLimiter.check(key);
  if (!result.allowed) return tooMany(res, req, result.reason || 'Too many invalid verification attempts. Please try again later.', result.retryAfterSeconds);
  next();
};

export const limitSyncRequests = (req: Request, res: Response, next: NextFunction) => {
  const ip = getClientIp(req);
  const authHeader = req.headers.authorization || '';
  const key = authHeader.length > 20 ? `sync_${authHeader.slice(-20)}` : `sync_ip_${ip}`;

  const result = syncLimiter.check(key);
  if (!result.allowed) return tooMany(res, req, 'Too many sync requests. Please reduce sync frequency.', result.retryAfterSeconds);
  next();
};

export const limitLogin = (req: Request, res: Response, next: NextFunction) => {
  const phone = normalizePhone(req.body?.username || req.body?.mobileNumber || '');
  const key = phone.length >= 10 ? `login_${phone}` : `login_ip_${getClientIp(req)}`;
  const result = loginLimiter.check(key);
  if (!result.allowed) return tooMany(res, req, 'Too many sign-in attempts. Please try again later.', result.retryAfterSeconds);
  next();
};
