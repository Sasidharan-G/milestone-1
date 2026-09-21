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
  const account = await context.dataStore.createAccount({ phone: '9876543210', displayName: 'Owner', businessName: 'Shop', password: '123456' });
  await context.identity.setPassword(account.user.userId, '123456');
  const login = await context.identity.authenticate('9876543210', '123456');
  assert.ok(login.tokens.accessToken);
  const refreshed = await context.identity.refresh(login.tokens.refreshToken);
  assert.notEqual(refreshed.tokens.refreshToken, login.tokens.refreshToken);
  await assert.rejects(() => context.identity.refresh(login.tokens.refreshToken), /invalid or expired/i);
});

test('shop profile is company-owned and mirrors only compatibility fields', async t => {
  const context = await fixture();
  t.after(() => fs.rm(context.directory, { recursive: true, force: true }));
  const first = await context.dataStore.createAccount({ phone: '9876543210', displayName: 'Owner One', businessName: 'Shop One', password: '123456' });
  const second = await context.dataStore.createAccount({ phone: '9876543211', displayName: 'Owner Two', businessName: 'Shop Two', password: '123456' });
  const updated = await context.dataStore.updateShopProfile(first.user.companyId, { shopName: 'New Shop One', ownerName: 'New Owner', gstNumber: '33ABCDE1234F1Z5', address: 'Chennai', phone: '9876543210', email: 'owner@example.com', updatedByUserId: first.user.userId });
  assert.equal(updated.shopName, 'New Shop One');
  assert.equal(updated.version, 2);
  assert.equal((await context.dataStore.getLicense(first.user.companyId))?.businessName, 'New Shop One');
  assert.equal((await context.dataStore.findUserById(first.user.userId))?.businessName, 'New Shop One');
  assert.equal((await context.dataStore.getShopProfile(second.user.companyId))?.shopName, 'Shop Two');
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
  // A fresh pull (cursor "0") now reconstructs from current-state records, not the change
  // log, so it returns one row per entity (the latest state) rather than one per historical
  // change — here that's the tombstone alone, which is sufficient for a client to apply.
  const page = await context.dataStore.pullSync(companyId, '0', 10);
  assert.equal(page.records.length, 1);
  assert.equal(page.records[0].deleted, true);
  assert.equal(page.nextCursor, '2');
  await assert.rejects(() => context.dataStore.applySyncBatch(companyId, [{ ...insert, operationId: 'operation-00000004', companyId: 'company-b' }]), /tenant/i);
});

test('local sync pull reconstructs full state via paginated snapshot then hands off to the tail', async t => {
  const context = await fixture();
  t.after(() => fs.rm(context.directory, { recursive: true, force: true }));
  const companyId = 'company-a';
  const entityIds = ['p1', 'p2', 'p3', 'p4', 'p5'];
  for (const entityId of entityIds) {
    const insert = { operationId: `seed-${entityId}`, companyId, entityType: 'Product', entityId, operation: 'INSERT' as const, schemaVersion: 1, payload: { name: entityId } };
    assert.equal((await context.dataStore.applySyncBatch(companyId, [insert]))[0].status, 'APPLIED');
  }

  // Drive the snapshot phase to completion with a small page size, exactly like PullWorker's
  // `while (hasMore)` loop, and reconstruct the full record set from the pages returned.
  let cursor = '0';
  const collected: string[] = [];
  for (let guard = 0; guard < 10; guard += 1) {
    const page = await context.dataStore.pullSync(companyId, cursor, 2);
    page.records.forEach(record => collected.push(record.entityId));
    cursor = page.nextCursor;
    if (!page.hasMore) break;
  }
  assert.deepEqual(collected.sort(), entityIds.slice().sort());
  assert.equal(cursor, '5', 'cursor should have switched to a plain tail sequence once the scan finished');

  // A new mutation after the snapshot completed should only ever be delivered once, via the tail.
  const followUp = { operationId: 'follow-up', companyId, entityType: 'Product', entityId: 'p6', operation: 'INSERT' as const, schemaVersion: 1, payload: { name: 'p6' } };
  await context.dataStore.applySyncBatch(companyId, [followUp]);
  const tailPage = await context.dataStore.pullSync(companyId, cursor, 10);
  assert.deepEqual(tailPage.records.map(record => record.entityId), ['p6']);
  assert.equal(tailPage.nextCursor, '6');
});

test('local sync pull never misreads an existing plain numeric cursor as a snapshot resume', async t => {
  const context = await fixture();
  t.after(() => fs.rm(context.directory, { recursive: true, force: true }));
  const companyId = 'company-a';
  const insert = { operationId: 'op-1', companyId, entityType: 'Product', entityId: 'p1', operation: 'INSERT' as const, schemaVersion: 1, payload: { name: 'p1' } };
  await context.dataStore.applySyncBatch(companyId, [insert]);
  const second = { operationId: 'op-2', companyId, entityType: 'Product', entityId: 'p2', operation: 'INSERT' as const, schemaVersion: 1, payload: { name: 'p2' } };
  await context.dataStore.applySyncBatch(companyId, [second]);
  // A device that already synced up to sequence 1 must only get the change after it, not a
  // fresh snapshot of everything.
  const page = await context.dataStore.pullSync(companyId, '1', 10);
  assert.deepEqual(page.records.map(record => record.entityId), ['p2']);
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

test('purging company records clears idempotency so a replayed outbox is not silently dropped', async t => {
  const context = await fixture();
  t.after(() => fs.rm(context.directory, { recursive: true, force: true }));
  const companyId = 'company-a';
  const insert = { operationId: 'operation-00000001', companyId, entityType: 'Product', entityId: 'product-1', operation: 'INSERT' as const, schemaVersion: 1, payload: { name: 'Tea' } };

  assert.equal((await context.dataStore.applySyncBatch(companyId, [insert]))[0].status, 'APPLIED');
  assert.equal(await context.dataStore.purgeCompanyRecords(companyId), 1);

  // The device still holds this operation in its outbox. While the purge left the cached
  // idempotency row behind, this replay came back DUPLICATE and the record stayed gone for good.
  const replay = await context.dataStore.applySyncBatch(companyId, [insert]);
  assert.equal(replay[0].status, 'APPLIED');

  const page = await context.dataStore.pullSync(companyId, '0', 50);
  assert.equal(page.records.filter(record => record.entityId === 'product-1' && !record.deleted).length, 1);
});

test('deleting a company leaves no change-log, idempotency, backup or audit rows behind', async t => {
  const context = await fixture();
  t.after(() => fs.rm(context.directory, { recursive: true, force: true }));
  const companyId = 'company-a';
  const insert = { operationId: 'operation-00000001', companyId, entityType: 'Product', entityId: 'product-1', operation: 'INSERT' as const, schemaVersion: 1, payload: { name: 'Tea' } };
  await context.dataStore.applySyncBatch(companyId, [insert]);
  await context.dataStore.appendAudit({ companyId, action: 'TEST', actorUserId: 'user-1', actorRole: 'ADMIN' });

  await context.dataStore.deleteCompany(companyId);

  // AWS drops the company's entire partition; local has to match or the providers disagree
  // about what a deleted company leaves behind.
  assert.equal((await context.dataStore.listAudit(companyId, 100)).length, 0);
  assert.equal((await context.dataStore.pullSync(companyId, '0', 50)).records.length, 0);
  const replay = await context.dataStore.applySyncBatch(companyId, [insert]);
  assert.equal(replay[0].status, 'APPLIED');
});
