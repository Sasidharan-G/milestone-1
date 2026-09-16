import { AppError } from '../../core/errors';
import { SessionRecord, SessionStore } from '../contracts';
import { AtomicJsonStore } from './atomicJsonStore';

export class LocalSessionStore implements SessionStore {
  constructor(private readonly store: AtomicJsonStore) {}

  register(input: Omit<SessionRecord, 'revoked' | 'lastSeenAtEpochMs'>): Promise<SessionRecord> {
    return this.store.write(state => {
      const session: SessionRecord = { ...input, revoked: false, lastSeenAtEpochMs: Date.now() };
      state.sessions[input.sessionId] = session;
      return session;
    });
  }

  heartbeat(companyId: string, userId: string, sessionId: string): Promise<SessionRecord> {
    return this.store.write(state => {
      const session = state.sessions[sessionId];
      if (!session || session.companyId !== companyId || session.userId !== userId || session.revoked || session.expiresAtEpochMs <= Date.now()) {
        throw new AppError(401, 'SESSION_INVALID', 'Session is invalid, expired, or revoked');
      }
      session.lastSeenAtEpochMs = Date.now();
      return session;
    });
  }

  revoke(companyId: string, actorUserId: string, sessionId: string): Promise<void> {
    return this.store.write(state => {
      const session = state.sessions[sessionId];
      if (!session || session.companyId !== companyId) throw new AppError(404, 'SESSION_NOT_FOUND', 'Session was not found');
      session.revoked = true;
    });
  }

  revokeOtherSessions(companyId: string, userId: string, keepSessionId: string): Promise<SessionRecord[]> {
    return this.store.write(state => Object.values(state.sessions).filter(session => {
      if (session.companyId !== companyId || session.userId !== userId || session.sessionId === keepSessionId || session.revoked) return false;
      session.revoked = true;
      return true;
    }));
  }

  revokeAllSessions(companyId: string, userId: string): Promise<SessionRecord[]> {
    return this.revokeOtherSessions(companyId, userId, '');
  }

  validate(companyId: string, userId: string, sessionId: string): Promise<boolean> {
    return this.store.read(state => {
      const session = state.sessions[sessionId];
      return Boolean(session && session.companyId === companyId && session.userId === userId && !session.revoked && session.expiresAtEpochMs > Date.now());
    });
  }
}

