import crypto from 'crypto';
import { providers } from '../providers/providerRegistry';

/**
 * A rate limit that holds across instances and deploys. The in-memory limiters reset on every
 * deploy and each instance counts on its own, so with N instances an attacker got N times the
 * budget. Here each request takes one of [max] slots for the current time window with a
 * conditional put (the same nonce primitive OTP sessions use); when every slot is taken the
 * request is refused. Only used in AWS mode - local development and tests keep the in-memory ones.
 *
 * Fails open: if the data store cannot be reached, the in-memory limiter in front of this still
 * applies, and refusing every sign-in because DynamoDB hiccuped would be worse.
 */
export const takeDurableSlot = async (scope: string, key: string, windowMs: number, max: number): Promise<boolean> => {
  if (providers().mode !== 'aws') return true;
  const window = Math.floor(Date.now() / windowMs);
  const expires = (window + 1) * windowMs + 60_000;
  try {
    for (let slot = 1; slot <= max; slot++) {
      const nonce = crypto.createHash('sha256').update(`${key}:${window}:${slot}`).digest('hex');
      if (await providers().dataStore.consumeNonce(`rl-${scope}`, nonce, expires)) return true;
    }
    return false;
  } catch (error) {
    console.error(JSON.stringify({ level: 'error', event: 'durable_rate_limit_unavailable', scope, message: (error as Error).message }));
    return true;
  }
};

export const usesDurableRateLimit = (): boolean => providers().mode === 'aws';
