import { Request, Response, NextFunction } from 'express';
import { providers } from '../providers/providerRegistry';

export interface AuthenticatedUser {
  uid: string;
  userId: string;
  companyId: string;
  company_id: string;
  role: string;
  super_admin: boolean;
  phone_number?: string;
  [key: string]: any;
}

export interface AuthenticatedRequest extends Request {
  user?: AuthenticatedUser;
}

export const requireAuth = async (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
  const authHeader = req.headers.authorization;

  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'Unauthorized: Missing or invalid token' });
  }

  const token = authHeader.split('Bearer ')[1];

  try {
    const decoded = await providers().identityProvider.verifyAccessToken(token);
    const uid = String(decoded.userId || '');
    const companyId = String(decoded.companyId || '');
    const role = String(decoded.role || 'CASHIER');
    const super_admin = Boolean(decoded.super_admin || role === 'SUPER_ADMIN');

    req.user = {
      ...decoded,
      uid,
      userId: uid,
      companyId,
      company_id: companyId,
      role,
      super_admin,
      phone_number: decoded.phone_number
    };
    next();
  } catch (error) {
    console.error('Error verifying auth token:', error instanceof Error ? error.message : error);
    return res.status(401).json({ error: 'Unauthorized: Token verification failed' });
  }
};
