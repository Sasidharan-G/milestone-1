import type { Server } from 'socket.io';

/** Socket rooms: one per tenant (companyId) and one per user (user:<userId>). */
export const userRoom = (userId: string) => `user:${userId}`;

export const emitToCompany = (io: Server | undefined, companyId: string, event: string, payload: Record<string, unknown>): void => {
  io?.to(companyId).emit(event, { companyId, timestamp: Date.now(), ...payload });
};

export const emitToUser = (io: Server | undefined, userId: string, event: string, payload: Record<string, unknown>): void => {
  io?.to(userRoom(userId)).emit(event, { userId, timestamp: Date.now(), ...payload });
};
