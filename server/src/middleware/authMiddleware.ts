import { Request, Response, NextFunction } from 'express';
import { AppError } from '../core/errors';
import { isLicenseActive, presentLicense } from '../core/license';
import { providers } from '../providers/providerRegistry';
import { sendRouteError } from '../routes/http';

export const PLATFORM_COMPANY_ID = 'platform';
export const MASTER_USER_ID = 'master-admin';

export interface AuthenticatedUser {
  uid: string;
  userId: string;
  companyId: string;
  company_id: string;
  role: string;
  super_admin: boolean;
  permissions: string[];
  sessionId?: string;
  phone_number?: string;
  [key: string]: any;
}

export interface AuthenticatedRequest extends Request {
  user?: AuthenticatedUser;
}

const bearerToken = (req: Request): string | null => {
  const header = req.headers.authorization;
  if (!header || !header.startsWith('Bearer ')) return null;
  const token = header.slice('Bearer '.length).trim();
  return token.length ? token : null;
};

const sessionHeader = (req: Request): string | null => {
  const raw = req.headers['x-session-id'];
  const value = Array.isArray(raw) ? raw[0] : raw;
  return typeof value === 'string' && /^[A-Za-z0-9\-]{8,64}$/.test(value) ? value : null;
};

const authenticate = async (req: AuthenticatedRequest): Promise<AuthenticatedUser> => {
  const token = bearerToken(req);
  if (!token) throw new AppError(401, 'AUTH_TOKEN_REQUIRED', 'Missing or invalid bearer token');
  let decoded;
  try { decoded = await providers().identityProvider.verifyAccessToken(token); }
  catch { throw new AppError(401, 'AUTH_TOKEN_INVALID', 'Token verification failed'); }
  const uid = String(decoded.userId || '');
  const companyId = String(decoded.companyId || '');
  const role = String(decoded.role || 'CASHIER');
  const super_admin = Boolean(decoded.super_admin || role === 'SUPER_ADMIN');
  if (!uid || !companyId) throw new AppError(401, 'AUTH_TOKEN_INVALID', 'Token verification failed');
  return {
    ...decoded, uid, userId: uid, companyId, company_id: companyId, role, super_admin,
    permissions: Array.isArray(decoded.permissions) ? decoded.permissions.map(String) : [],
    phone_number: decoded.phone_number
  };
};

/**
 * Bearer token + device session. Every authenticated call must carry the X-Session-Id issued by
 * /sessions/register (or master login) so a device that was signed out remotely is cut off on its
 * very next request, not when its 15-minute access token expires.
 */
export const requireAuth = async (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
  try {
    const user = await authenticate(req);
    const sessionId = sessionHeader(req);
    if (!sessionId) throw new AppError(401, 'SESSION_REQUIRED', 'Register a device session before calling this endpoint');
    const valid = await providers().sessionStore.validate(user.companyId, user.userId, sessionId);
    if (!valid) throw new AppError(401, 'SESSION_REVOKED', 'This device session was signed out. Please sign in again.');
    req.user = { ...await withCurrentAccount(user), sessionId };
    return next();
  } catch (error) { return sendRouteError(res, req, error); }
};

/**
 * Role and permissions as the account holds them now, not as the token remembers them. The
 * Cognito token carries the permissions it was issued with and is only re-issued from Cognito's
 * own copy, which a staff edit never updated, so a permission taken away from a cashier kept
 * working until the next password change. The account record is the authority.
 */
const withCurrentAccount = async (user: AuthenticatedUser): Promise<AuthenticatedUser> => {
  if (user.super_admin) return user;
  const account = await providers().dataStore.findUserById(user.userId);
  if (!account || account.companyId !== user.companyId) throw new AppError(401, 'ACCOUNT_NOT_FOUND', 'This account no longer exists. Please sign in again.');
  if (account.status !== 'ACTIVE') throw new AppError(403, 'AUTH_ACCOUNT_INACTIVE', 'Your account has been deactivated. Contact your shop administrator');
  return { ...user, role: account.role, permissions: [...account.permissions] };
};

/** Token only — used by /sessions/register, which is how a device obtains its session in the first place. */
export const requireAuthWithoutSession = async (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
  try {
    req.user = await authenticate(req);
    return next();
  } catch (error) { return sendRouteError(res, req, error); }
};

export const requireSuperAdmin = (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
  if (req.user?.role === 'SUPER_ADMIN' && req.user.super_admin) return next();
  return sendRouteError(res, req, new AppError(403, 'PLATFORM_ADMIN_REQUIRED', 'Platform administrator access is required'));
};

export const requireShopAdmin = (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
  if (req.user?.role === 'ADMIN' && req.user.permissions.includes('USER_MANAGE')) return next();
  return sendRouteError(res, req, new AppError(403, 'STAFF_ADMIN_REQUIRED', 'Shop administrator permission is required'));
};

/**
 * Subscription gate. Login, /account/me and /license/current stay reachable so the app can show
 * the lock screen; everything that touches tenant data is refused the instant the license lapses.
 */
export const requireActiveLicense = async (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
  try {
    if (req.user?.super_admin) return next();
    const license = await providers().dataStore.getLicense(req.user!.companyId);
    if (!isLicenseActive(license)) {
      const shown = license ? presentLicense(license) : null;
      throw new AppError(403, 'LICENSE_INACTIVE', 'Subscription is not active. Contact support to renew.', false,
        { status: shown?.status || 'MISSING', validUntilEpochMs: shown?.validUntilEpochMs || 0 });
    }
    return next();
  } catch (error) { return sendRouteError(res, req, error); }
};
