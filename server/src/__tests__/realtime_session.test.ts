import test from 'node:test';
import assert from 'node:assert/strict';
import { revokeSessionSockets, sessionRoom } from '../core/realtime';

test('revoking a device session emits only to its private room and force-disconnects it', () => {
  const emitted: Array<{ room: string; event: string; payload: any }> = [];
  const disconnected: Array<{ room: string; close: boolean }> = [];
  const io = {
    to: (room: string) => ({ emit: (event: string, payload: any) => emitted.push({ room, event, payload }) }),
    in: (room: string) => ({ disconnectSockets: (close: boolean) => disconnected.push({ room, close }) })
  };
  revokeSessionSockets(io as any, 'user-a', [{ sessionId: 'session-12345678', companyId: 'company-a', userId: 'user-a', deviceId: 'device-a', revoked: true, lastSeenAtEpochMs: 1, expiresAtEpochMs: 2 }], 'SIGNED_IN_ELSEWHERE');
  assert.equal(emitted.length, 1);
  assert.equal(emitted[0].room, sessionRoom('session-12345678'));
  assert.equal(emitted[0].event, 'session_revoked');
  assert.equal(disconnected.length, 1);
  assert.deepEqual(disconnected[0], { room: sessionRoom('session-12345678'), close: true });
});
