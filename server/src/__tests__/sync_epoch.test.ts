import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { pullSync, pushSync } from '../controllers/syncController';
import { AtomicJsonStore } from '../providers/local/atomicJsonStore';
import { LocalDataStore } from '../providers/local/localDataStore';
import { setProviderRegistryForTests } from '../providers/providerRegistry';

const store = new LocalDataStore(new AtomicJsonStore(path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'sync-epoch-')), 'state.json')));
setProviderRegistryForTests({ dataStore: store } as any);

const user = { companyId: 'c', role: 'ADMIN', permissions: [], sessionId: 's1' };
const invoke = async (handler: any, req: object) => {
  let code = 200; let result: any;
  await handler({ user, app: { get: () => undefined }, query: {}, ...req } as any, {
    status(value: number) { code = value; return this; },
    json(value: any) { result = value; return this; }
  } as any);
  return { code, result };
};
const push = (epoch: number | undefined, id: string) => invoke(pushSync, { body: {
  ...(epoch === undefined ? {} : { epoch }),
  operations: [{ operationId: `op-${id}`, entityType: 'Product', entityId: id, operation: 'INSERT', baseVersion: 0, schemaVersion: 1, payload: { name: id } }]
} });

test('before any purge, a client that sends no epoch syncs as before', async () => {
  assert.equal((await push(undefined, 'p1')).code, 200);
  assert.equal((await invoke(pullSync, {})).result.epoch, 0);
});

test('after a purge, a device holding older data is refused until it resets', async () => {
  await store.bumpSyncEpoch('c');
  await store.purgeCompanyRecords('c');
  const epoch = await store.bumpSyncEpoch('c');
  const stale = await push(0, 'p2');
  assert.equal(stale.code, 409);
  assert.equal(stale.result.error.code, 'SYNC_EPOCH_STALE');
  assert.equal(stale.result.error.details.epoch, epoch);
  assert.equal((await push(undefined, 'p3')).code, 409);

  const pulled = await invoke(pullSync, {});
  assert.equal(pulled.result.epoch, epoch);
  assert.equal(pulled.result.records.length, 0);
  assert.equal((await push(epoch, 'p4')).code, 200);
});

test('a heartbeat keeps a device in use signed in past the original expiry', async () => {
  const { LocalSessionStore } = await import('../providers/local/localSessionStore');
  const sessions = new LocalSessionStore(new AtomicJsonStore(path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'session-slide-')), 'state.json')));
  const soon = Date.now() + 60_000;
  await sessions.register({ sessionId: 'session-0001', companyId: 'c', userId: 'u', deviceId: 'device-0001', expiresAtEpochMs: soon });
  const later = Date.now() + 30 * 86_400_000;
  const extended = await sessions.heartbeat('c', 'u', 'session-0001', later);
  assert.equal(extended.expiresAtEpochMs, later);
});
