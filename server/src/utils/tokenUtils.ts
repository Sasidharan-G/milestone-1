import jwt from 'jsonwebtoken';

const resolveJwtSecret = (): string => {
  const configured = process.env.JWT_SECRET?.trim();
  if (configured && configured.length >= 32) return configured;
  if (process.env.NODE_ENV === 'production') {
    throw new Error('JWT_SECRET must be configured with at least 32 characters in production');
  }
  // Local development fallback only; production refuses to start without a real secret.
  return 'local-development-only-jwt-secret-do-not-use-in-production';
};

const JWT_SECRET = resolveJwtSecret();

export interface TokenClaims {
  userId: string;
  companyId?: string;
  company_id?: string;
  role: string;
  permissions?: string[];
  phone_number?: string;
  super_admin?: boolean;
  [key: string]: any;
}

export function generateAuthToken(claims: TokenClaims, expiresIn: string | number = '15m'): string {
  return jwt.sign(claims, JWT_SECRET, { expiresIn: expiresIn as any });
}

export async function verifyAuthToken(token: string): Promise<any> {
  return new Promise((resolve, reject) => {
    jwt.verify(token, JWT_SECRET, (err, decoded) => {
      if (err || !decoded) {
        return reject(new Error('Invalid or expired token'));
      }
      resolve(decoded);
    });
  });
}
