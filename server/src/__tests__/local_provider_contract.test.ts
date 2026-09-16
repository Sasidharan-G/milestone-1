import test from 'node:test';
import assert from 'node:assert/strict';
import { promises as fs } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import { AtomicJsonStore } from '../providers/local/atomicJsonStore';
import { LocalDataStore } from '../providers/local/localDataStore';
import { LocalIdentityProvider } from '../providers/local/localIdentityProvider';
import { LocalObjectStorage } from '../providers/local/localObjectStorage';

const fixture = async () => {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'kadakutty-provider-'));
  const atomic = new AtomicJsonStore(path.join(directory, 'state.json'));
  const dataStore = new LocalDataStore(atomic);
  return { directory, dataStore, identity: new LocalIdentityProvider(atomic, dataStore), objects: new LocalObjectStorage(atomic, path.join(directory, 'objects')) };
};

test('local identity persists credentials and rotates refresh tokens', async t => {
  const context = await fixture();
  t.after(() => fs.rm(context.directory, { recursive: true, force: true }));
  const account = await context.dataStore.createAccount({ phone: '9876543210', displayName: 'Owner', businessName: 'Shop', password: 'secure123' });
  await context.identity.setPassword(account.user.userId, 'secure123');
  const login = await context.identity.authenticate('9876543210', 'secure123');
  assert.ok(login.tokens.accessToken);
  const refreshed = await context.identity.refresh(login.tokens.refreshToken);
  assert.notEqual(refreshed.tokens.refreshToken, login.tokens.refreshToken);
  await assert.rejects(() => context.identity.refresh(login.tokens.refreshToken), /invalid or expired/i);
});

test('sync is tenant-scoped, idempotent, versioned, cursor-based, and tombstone-aware', async t => {
  const context = await fixture();
  t.after(() => fs.rm(context.directory, { recursive: true, force: true }));
  const companyId = 'company-a';
  const insert = { operationId: 'operation-00000001', companyId, entityType: 'Product', entityId: 'product-1', operation: 'INSERT' as const, schemaVersion: 1, payload: { name: 'Tea' } };
  const first = await context.dataStore.applySyncBatch(companyId, [insert]);
  assert.equal(first[0].status, 'APPLIED');
  assert.equal(first[0].version, 1);
  const duplicate = await context.dataStore.applySyncBatch(companyId, [insert]);
  assert.equal(duplicate[0].status, 'DUPLICATE');
  const conflict = await context.dataStore.applySyncBatch(companyId, [{ ...insert, operationId: 'operation-00000002', operation: 'UPDATE', baseVersion: 99 }]);
  assert.equal(conflict[0].status, 'CONFLICT');
  const deletion = await context.dataStore.applySyncBatch(companyId, [{ ...insert, operationId: 'operation-00000003', operation: 'DELETE', baseVersion: 1 }]);
  assert.equal(deletion[0].version, 2);
  const page = await context.dataStore.pullSync(companyId, '0', 10);
  assert.equal(page.records.length, 2);
  assert.equal(page.records[1].deleted, true);
  assert.equal(page.nextCursor, '2');
  await assert.rejects(() => context.dataStore.applySyncBatch(companyId, [{ ...insert, operationId: 'operation-00000004', companyId: 'company-b' }]), /tenant/i);
});

test('local object storage verifies tenant, size, and checksum', async t => {
  const context = await fixture();
  t.after(() => fs.rm(context.directory, { recursive: true, force: true }));
  const content = Buffer.from('encrypted-room-backup');
  const checksumSha256 = crypto.createHash('sha256').update(content).digest('hex');
  const intent = await context.objects.createUploadIntent({ companyId: 'company-a', fileName: 'backup.zip', sizeBytes: content.length, checksumSha256, schemaVersion: 22 });
  await context.objects.writeLocalContent('company-a', intent.backupId, content);
  const ready = await context.objects.complete('company-a', intent.backupId);
  assert.equal(ready.status, 'READY');
  assert.deepEqual(await context.objects.readLocalContent('company-a', intent.backupId), content);
  await assert.rejects(() => context.objects.readLocalContent('company-b', intent.backupId), /not found/i);
});

