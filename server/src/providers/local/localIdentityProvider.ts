import crypto from 'node:crypto';
import { promisify } from 'node:util';
import { AppError } from '../../core/errors';
import { generateAuthToken } from '../../utils/tokenUtils';
import { DataStore, IdentityProvider, IdentityTokens, UserAccount, VerifiedIdentity } from '../contracts';
import { AtomicJsonStore } from './atomicJsonStore';
import { verifyAuthToken } from '../../utils/tokenUtils';

const derive = promisify(crypto.pbkdf2);
const iterations = 210_000;
const keyBytes = 32;
const refreshLifetimeMs = 30 * 24 * 60 * 60 * 1000;
const tokenHash = (token: string) => crypto.createHash('sha256').update(token).digest('hex');

export class LocalIdentityProvider implements IdentityProvider {
  constructor(private readonly store: AtomicJsonStore, private readonly dataStore: DataStore) {}

  async setPassword(userId: string, password: string): Promise<void> {
    if (password.length < 6 || password.length > 256) throw new AppError(400, 'AUTH_PASSWORD_INVALID', 'Password must contain 6 to 256 characters');
    const salt = crypto.randomBytes(16);
    const verifier = await derive(password, salt, iterations, keyBytes, 'sha256');
    await this.store.write(state => {
      if (!state.users[userId]) throw new AppError(404, 'ACCOUNT_NOT_FOUND', 'Account was not found');
      state.credentials[userId] = { saltBase64: salt.toString('base64'), verifierBase64: verifier.toString('base64'), updatedAtEpochMs: Date.now() };
    });
  }

  async authenticate(phone: string, password: string): Promise<{ user: UserAccount; tokens: IdentityTokens }> {
    const user = await this.dataStore.findUserByPhone(phone);
    if (!user) throw new AppError(401, 'AUTH_INVALID_CREDENTIALS', 'Invalid mobile number or password');
    const credential = await this.store.read(state => state.credentials[user.userId] || null);
    if (!credential) throw new AppError(401, 'AUTH_INVALID_CREDENTIALS', 'Invalid mobile number or password');
    const verifier = await derive(password, Buffer.from(credential.saltBase64, 'base64'), iterations, keyBytes, 'sha256');
    const expected = Buffer.from(credential.verifierBase64, 'base64');
    if (verifier.length !== expected.length || !crypto.timingSafeEqual(verifier, expected)) throw new AppError(401, 'AUTH_INVALID_CREDENTIALS', 'Invalid mobile number or password');
    if (user.status !== 'ACTIVE') throw new AppError(403, 'AUTH_ACCOUNT_INACTIVE', 'Account is not active');
    return { user, tokens: await this.issueTokens(user) };
  }

  async refresh(refreshToken: string): Promise<{ user: UserAccount; tokens: IdentityTokens }> {
    const hash = tokenHash(refreshToken);
    const stored = await this.store.read(state => state.refreshTokens[hash] || null);
    if (!stored || stored.revoked || stored.expiresAtEpochMs <= Date.now()) throw new AppError(401, 'AUTH_REFRESH_INVALID', 'Refresh session is invalid or expired');
    const user = await this.dataStore.findUserById(stored.userId);
    if (!user || user.status !== 'ACTIVE') throw new AppError(401, 'AUTH_REFRESH_INVALID', 'Refresh session is invalid or expired');
    await this.store.write(state => { if (state.refreshTokens[hash]) state.refreshTokens[hash].revoked = true; });
    return { user, tokens: await this.issueTokens(user) };
  }

  async revokeUser(userId: string): Promise<void> {
    await this.store.write(state => {
      Object.values(state.refreshTokens).forEach(token => { if (token.userId === userId) token.revoked = true; });
    });
  }

  async deleteUser(userId: string): Promise<void> {
    await this.store.write(state => {
      delete state.credentials[userId];
      Object.entries(state.refreshTokens).forEach(([key, token]) => {
        if (token.userId === userId) delete state.refreshTokens[key];
      });
    });
  }

  async verifyAccessToken(token: string): Promise<VerifiedIdentity> {
    const decoded = await verifyAuthToken(token);
    return {
      ...decoded,
      userId: decoded.userId,
      companyId: decoded.companyId || decoded.company_id || '',
      role: decoded.role as UserAccount['role'],
      permissions: Array.isArray(decoded.permissions) ? decoded.permissions : []
    };
  }

  async authenticatePlatform(phone: string, pin: string): Promise<{ tokens: IdentityTokens; mobile: string }> {
    const config = await this.dataStore.getMasterConfig();
    const supplied = Buffer.from(`${phone}:${pin}`);
    const expected = Buffer.from(`${config.mobile}:${config.pin}`);
    if (supplied.length !== expected.length || !crypto.timingSafeEqual(supplied, expected)) throw new AppError(401, 'MASTER_PIN_INVALID', 'Master credentials are invalid');
    return {
      mobile: config.mobile,
      tokens: {
        accessToken: generateAuthToken({ userId: 'master-admin', companyId: 'platform', role: 'SUPER_ADMIN', super_admin: true, permissions: ['PLATFORM_ADMIN'] }, '30m'),
        refreshToken: '', expiresInSeconds: 1800
      }
    };
  }

  private async issueTokens(user: UserAccount): Promise<IdentityTokens> {
    const refreshToken = crypto.randomBytes(48).toString('base64url');
    const hash = tokenHash(refreshToken);
    await this.store.write(state => {
      state.refreshTokens[hash] = { tokenHash: hash, userId: user.userId, expiresAtEpochMs: Date.now() + refreshLifetimeMs, revoked: false };
    });
    return {
      accessToken: generateAuthToken({ userId: user.userId, companyId: user.companyId, role: user.role, permissions: user.permissions, phone_number: user.phone }, '15m'),
      refreshToken,
      expiresInSeconds: 900
    };
  }
}
