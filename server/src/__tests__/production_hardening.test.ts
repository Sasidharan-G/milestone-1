import test from 'node:test';
import assert from 'node:assert/strict';
import { promises as fs } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { AwsDataStore } from '../providers/aws/awsDataStore';
import { AtomicJsonStore } from '../providers/local/atomicJsonStore';
import { LocalDataStore } from '../providers/local/localDataStore';
import { encodeTailCursor, parseCursor } from '../providers/sync/syncCursor';
import { CHANGE_GAP_SETTLE_MS, rejectionReason } from '../providers/sync/syncRules';
import { SyncOperation, UserAccount } from '../providers/contracts';
import { requireAuth } from '../middleware/authMiddleware';
import { setProviderRegistryForTests } from '../providers/providerRegistry';

/** Just enough of the DynamoDB document client for the sync paths under test. */
class FakeDocumentClient {
  readonly items = new Map<string, any>();
  async send(command: any): Promise<any> {
    const input = command.input;
    const key = (value: { pk: string; sk: string }) => `${value.pk}|${value.sk}`;
    switch (command.constructor.name) {
      case 'GetCommand': return { Item: this.items.get(key(input.Key)) };
      case 'PutCommand': this.items.set(key(input.Item), input.Item); return {};
      case 'UpdateCommand': {
        const k = key(input.Key);
        const sequence = (this.items.get(k)?.sequence || 0) + 1;
        const item = { pk: input.Key.pk, sk: 'SYNC_COUNTER', itemType: 'SYNC_COUNTER', sequence };
        this.items.set(k, item);
        return { Attributes: item };
      }
      case 'TransactWriteCommand':
        for (const operation of input.TransactItems) if (operation.Put) this.items.set(key(operation.Put.Item), operation.Put.Item);
        return {};
      case 'BatchWriteCommand':
        for (const request of Object.values(input.RequestItems)[0] as any[]) this.items.delete(key(request.DeleteRequest.Key));
        return {};
      case 'QueryCommand': {
        const pk = input.ExpressionAttributeValues[':pk'];
        const prefix = input.ExpressionAttributeValues[':prefix'];
        const from = input.ExpressionAttributeValues[':from'];
        const to = input.ExpressionAttributeValues[':to'];
        let matches = [...this.items.values()]
          .filter(item => item.pk === pk && (!prefix || item.sk.startsWith(prefix)) && (!from || (item.sk >= from && item.sk <= to)))
          .sort((a, b) => a.sk.localeCompare(b.sk));
        if (input.ExclusiveStartKey) matches = matches.filter(item => item.sk > input.ExclusiveStartKey.sk);
        let LastEvaluatedKey;
        if (typeof input.Limit === 'number' && matches.length > input.Limit) {
          matches = matches.slice(0, input.Limit);
          LastEvaluatedKey = { pk, sk: matches[matches.length - 1].sk };
        }
        return { Items: matches, LastEvaluatedKey };
      }
      default: throw new Error(`Unsupported fake command ${command.constructor.name}`);
    }
  }
}

const insert = (companyId: string, entityId: string, overrides: Partial<SyncOperation> = {}): SyncOperation => ({
  operationId: `op-${entityId}`, companyId, entityType: 'Product', entityId, operation: 'INSERT', schemaVersion: 1, payload: { name: entityId }, ...overrides
});

test('one bad operation is rejected on its own; the rest of the batch still applies (AWS)', async () => {
  const store = new AwsDataStore(new FakeDocumentClient() as any, 'table');
  const results = await store.applySyncBatch('c', [
    insert('c', 'p1'),
    insert('c', 'p2', { entityType: 'Unknown' }),
    insert('c', 'p3', { operationId: undefined as any }),
    insert('c', 'p4')
  ]);
  assert.deepEqual(results.map(result => result.status), ['APPLIED', 'REJECTED', 'REJECTED', 'APPLIED']);
  assert.equal(results[1].error?.code, 'SYNC_ENTITY_UNSUPPORTED');
  assert.equal(results[2].error?.code, 'SYNC_OPERATION_ID_INVALID');
});

test('one bad operation is rejected on its own; the rest of the batch still applies (local)', async t => {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'kadaikutty-hardening-'));
  t.after(() => fs.rm(directory, { recursive: true, force: true }));
  const store = new LocalDataStore(new AtomicJsonStore(path.join(directory, 'state.json')));
  const results = await store.applySyncBatch('c', [insert('c', 'p1'), insert('c', 'p2', { payload: [] as any }), insert('c', 'p3')]);
  assert.deepEqual(results.map(result => result.status), ['APPLIED', 'REJECTED', 'APPLIED']);
});

test('the cross-tenant check still fails the whole request', async () => {
  const store = new AwsDataStore(new FakeDocumentClient() as any, 'table');
  await assert.rejects(() => store.applySyncBatch('c', [insert('other', 'p1')]), /tenant/i);
});

test('rejectionReason covers every malformed shape', () => {
  assert.equal(rejectionReason(insert('c', 'p')), null);
  assert.equal(rejectionReason(insert('c', '   ', { operationId: 'op-blank' }))?.code, 'SYNC_ENTITY_ID_INVALID');
  assert.equal(rejectionReason(insert('c', 'p', { operation: 'MERGE' as any }))?.code, 'SYNC_OPERATION_INVALID');
  assert.equal(rejectionReason(insert('c', 'p', { baseVersion: -1 }))?.code, 'SYNC_BASE_VERSION_INVALID');
  assert.equal(rejectionReason(insert('c', 'p', { baseVersion: 1.5 }))?.code, 'SYNC_BASE_VERSION_INVALID');
  assert.equal(rejectionReason(insert('c', 'p', { payload: { blob: 'x'.repeat(400_000) } }))?.code, 'SYNC_RECORD_TOO_LARGE');
});

test('a tail pull stops in front of a write still in flight and delivers it once it lands', async () => {
  const client = new FakeDocumentClient();
  const store = new AwsDataStore(client as any, 'table');
  for (const id of ['p1', 'p2', 'p3']) await store.applySyncBatch('c', [insert('c', id)]);
  // Sequence 2 took its number but has not committed yet.
  const inFlight = client.items.get('COMPANY#c|CHANGE#00000000000000000002');
  client.items.delete('COMPANY#c|CHANGE#00000000000000000002');

  const held = await store.pullSync('c', '1', 10);
  assert.deepEqual(held.records, []);
  assert.equal(held.nextCursor, '1', 'the cursor must not step over the hole');
  assert.equal(held.hasMore, false);

  client.items.set('COMPANY#c|CHANGE#00000000000000000002', inFlight);
  const landed = await store.pullSync('c', held.nextCursor, 10);
  assert.deepEqual(landed.records.map(record => record.entityId), ['p2', 'p3']);
  assert.equal(landed.nextCursor, '3');
});

test('an old hole (a write that failed) is stepped over instead of blocking the pull forever', async () => {
  const client = new FakeDocumentClient();
  const store = new AwsDataStore(client as any, 'table');
  for (const id of ['p1', 'p2', 'p3']) await store.applySyncBatch('c', [insert('c', id)]);
  client.items.delete('COMPANY#c|CHANGE#00000000000000000002');
  const after = client.items.get('COMPANY#c|CHANGE#00000000000000000003');
  after.data.record.updatedAtEpochMs = Date.now() - CHANGE_GAP_SETTLE_MS - 1_000;
  const page = await store.pullSync('c', '1', 10);
  assert.deepEqual(page.records.map(record => record.entityId), ['p3']);
  assert.equal(page.nextCursor, '3');
});

test('a snapshot hands over to the change log early, and never as a bare "0"', async () => {
  const client = new FakeDocumentClient();
  const store = new AwsDataStore(client as any, 'table');
  for (const id of ['p1', 'p2']) await store.applySyncBatch('c', [insert('c', id)]);
  const snapshot = await store.pullSync('c', '0', 10);
  assert.equal(snapshot.records.length, 2);
  assert.equal(snapshot.nextCursor, 'T:0');
  assert.deepEqual(parseCursor(snapshot.nextCursor), { mode: 'tail', afterSequence: 0 });
  // The overlap replays the changes the snapshot may have raced with; the client applies them again.
  const tail = await store.pullSync('c', snapshot.nextCursor, 10);
  assert.deepEqual(tail.records.map(record => record.entityId), ['p1', 'p2']);
  assert.equal(tail.nextCursor, '2');
  assert.equal(encodeTailCursor(5), '5');
});

test('purge removes records, change log and idempotency rows', async () => {
  const client = new FakeDocumentClient();
  const store = new AwsDataStore(client as any, 'table');
  for (const id of ['p1', 'p2']) await store.applySyncBatch('c', [insert('c', id)]);
  assert.equal(await store.purgeCompanyRecords('c'), 2);
  const left = [...client.items.keys()].filter(key => /RECORD#|CHANGE#|IDEMPOTENCY#/.test(key));
  assert.deepEqual(left, []);
});

test('requireAuth takes role and permissions from the account, not from the token', async t => {
  const account: UserAccount = { userId: 'u1', companyId: 'c', phone: '9876543210', displayName: 'Staff', role: 'CASHIER', permissions: ['SALE_CREATE'], status: 'ACTIVE', createdAtEpochMs: 1, updatedAtEpochMs: 1 };
  const registry: any = {
    identityProvider: { verifyAccessToken: async () => ({ userId: 'u1', companyId: 'c', role: 'CASHIER', permissions: ['SALE_CREATE', 'BACKUP_CREATE', 'SETTINGS_EDIT'] }) },
    sessionStore: { validate: async () => true },
    dataStore: { findUserById: async () => account }
  };
  setProviderRegistryForTests(registry);
  t.after(() => setProviderRegistryForTests(null));

  const run = async () => {
    const req: any = { headers: { authorization: 'Bearer token', 'x-session-id': 'session-123456' } };
    const res: any = { statusCode: 200, body: null, status(code: number) { this.statusCode = code; return this; }, json(body: any) { this.body = body; return this; } };
    let passed = false;
    await requireAuth(req, res, () => { passed = true; });
    return { req, res, passed };
  };

  const allowed = await run();
  assert.equal(allowed.passed, true);
  assert.deepEqual(allowed.req.user.permissions, ['SALE_CREATE'], 'permissions removed from the account must not survive in the token');

  account.status = 'INACTIVE';
  const blocked = await run();
  assert.equal(blocked.passed, false);
  assert.equal(blocked.res.statusCode, 403);
  assert.equal(blocked.res.body.error.code, 'AUTH_ACCOUNT_INACTIVE');
});
