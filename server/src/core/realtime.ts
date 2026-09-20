import type { Server } from 'socket.io';
import type { SessionRecord } from '../providers/contracts';

/** Socket rooms: one per tenant (companyId) and one per user (user:<userId>). */
export const userRoom = (userId: string) => `user:${userId}`;
/** A private room for every authenticated device session. */
export const sessionRoom = (sessionId: string) => `session:${sessionId}`;

export const emitToCompany = (io: Server | undefined, companyId: string, event: string, payload: Record<string, unknown>): void => {
  io?.to(companyId).emit(event, { companyId, timestamp: Date.now(), ...payload });
};

export const emitToUser = (io: Server | undefined, userId: string, event: string, payload: Record<string, unknown>): void => {
  io?.to(userRoom(userId)).emit(event, { userId, timestamp: Date.now(), ...payload });
};

/**
 * A revoked REST session must also lose its realtime transport immediately. Joining a private
 * session room at socket authentication lets this work across every revoke path without trusting
 * a client to honour a `session_revoked` notification.
 */
export const revokeSessionSockets = (io: Server | undefined, userId: string, sessions: SessionRecord[], reason: string, deviceName?: string): void => {
  for (const session of sessions) {
    const room = sessionRoom(session.sessionId);
    io?.to(room).emit('session_revoked', { userId, sessionId: session.sessionId, reason, deviceName, timestamp: Date.now() });
    io?.in(room).disconnectSockets(true);
  }
};
