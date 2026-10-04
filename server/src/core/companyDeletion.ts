import type { Request } from 'express';
import { AppError } from './errors';
import { audit } from './audit';
import { emitToUser, revokeSessionSockets } from './realtime';
import { MASTER_USER_ID, PLATFORM_COMPANY_ID } from '../middleware/authMiddleware';
import { providers } from '../providers/providerRegistry';

interface Actor { userId: string; role: string; companyId: string; }

/**
 * Permanently erases one shop: its cloud backups, every device session, every user's sign-in
 * identity, and the company's records (sync data, license, users, phone reservations).
 *
 * Shared by Master Control and by the owner's own "Delete account" (Google Play requires an in-app
 * way to delete an account together with its data). Sessions are revoked first so no device keeps
 * working against data that is about to disappear, and the phone numbers are released so they can
 * register again.
 *
 * The audit line is written against the platform, not the company: the company's own audit
 * partition is deleted here and writing into it would recreate an orphan record for a shop that no
 * longer exists.
 */
export async function eraseCompany(req: Request, companyId: string, actor: Actor, via: 'MASTER' | 'OWNER'): Promise<{ users: number; backups: number }> {
  // Point lookups: adminOverview() scans the whole table, every tenant's sync records included.
  const [companyUsers, license] = await Promise.all([providers().dataStore.listCompanyUsers(companyId), providers().dataStore.getLicense(companyId)]);
  if (!companyUsers.length && !license) throw new AppError(404, 'COMPANY_NOT_FOUND', 'Company was not found');
  const io = req.app.get('io');
  const backups = await providers().objectStorage.list(companyId);
  await Promise.all(backups.map(backup => providers().objectStorage.delete(companyId, backup.backupId)));
  await providers().objectStorage.deleteShopLogo(companyId);
  for (const user of companyUsers) {
    const revoked = await providers().sessionStore.revokeAllSessions(companyId, user.userId);
    for (const session of revoked) emitToUser(io, user.userId, 'session_revoked', { sessionId: session.sessionId, reason: 'ACCOUNT_DELETED' });
    revokeSessionSockets(io, user.userId, revoked, 'ACCOUNT_DELETED');
    await providers().identityProvider.deleteUser(user.userId);
  }
  await providers().dataStore.deleteCompany(companyId);
  await audit(req, actor, 'COMPANY_DELETED', companyId, { via, users: companyUsers.map(user => user.userId), backups: backups.length }, PLATFORM_COMPANY_ID);
  emitToUser(io, MASTER_USER_ID, 'master_overview_changed', { companyId });
  return { users: companyUsers.length, backups: backups.length };
}
