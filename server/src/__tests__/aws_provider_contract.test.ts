import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import { AwsDataStore } from '../providers/aws/awsDataStore';
import { AwsIdentityProvider } from '../providers/aws/awsIdentityProvider';
import { AwsObjectStorage } from '../providers/aws/awsObjectStorage';
import { AwsSessionStore } from '../providers/aws/awsSessionStore';
import { AwsProviderConfig } from '../providers/aws/awsConfig';
import { UserAccount } from '../providers/contracts';
import { createProviderRegistry } from '../providers/providerRegistry';
import { Msg91SmsSender } from '../providers/msg91/msg91SmsSender';
import { AwsSmsSender } from '../providers/aws/awsSmsSender';
import { AppError } from '../core/errors';

class FakeDocumentClient {
  readonly items = new Map<string, any>();

  async send(command: any): Promise<any> {
    const input = command.input;
    switch (command.constructor.name) {
      case 'GetCommand': return { Item: this.items.get(this.key(input.Key)) };
      case 'PutCommand': this.items.set(this.key(input.Item), input.Item); return {};
      case 'DeleteCommand': this.items.delete(this.key(input.Key)); return {};
      case 'UpdateCommand': {
        // Real DynamoDB persists the SYNC_COUNTER item per-partition-key; simulate that
        // (rather than a single shared counter) so a plain GetCommand can peek its value.
        if (input.Key.sk === 'SYNC_COUNTER') {
          const key = this.key(input.Key);
          const sequence = (this.items.get(key)?.sequence || 0) + 1;
          const item = { pk: input.Key.pk, sk: 'SYNC_COUNTER', itemType: 'SYNC_COUNTER', sequence };
          this.items.set(key, item);
          return { Attributes: item };
        }
        const item = this.items.get(this.key(input.Key));
        if (item && input.ExpressionAttributeValues?.[':now']) item.data.lastSeenAtEpochMs = input.ExpressionAttributeValues[':now'];
        if (item && input.ExpressionAttributeValues?.[':true']) item.data.revoked = true;
        return { Attributes: item };
      }
      case 'TransactWriteCommand':
        for (const operation of input.TransactItems) {
          if (operation.Put) this.items.set(this.key(operation.Put.Item), operation.Put.Item);
          if (operation.Delete) this.items.delete(this.key(operation.Delete.Key));
        }
        return {};
      case 'QueryCommand': {
        const company = input.ExpressionAttributeValues[':pk'];
        const prefix = input.ExpressionAttributeValues[':prefix'];
        const from = input.ExpressionAttributeValues[':from'];
        const to = input.ExpressionAttributeValues[':to'];
        const sortKeyOf = (item: any) => (input.IndexName ? item.gsi1sk : item.sk);
        let matches = [...this.items.values()].filter(item => {
          const partitionMatches = input.IndexName ? item.gsi1pk === company : item.pk === company;
          const sortKey = sortKeyOf(item);
          return partitionMatches && (!prefix || sortKey.startsWith(prefix)) && (!from || (sortKey >= from && sortKey <= to));
        }).sort((left, right) => sortKeyOf(left).localeCompare(sortKeyOf(right)));
        if (input.ExclusiveStartKey) {
          const startSortKey = sortKeyOf(input.ExclusiveStartKey);
          matches = matches.filter(item => sortKeyOf(item) > startSortKey);
        }
        let LastEvaluatedKey: { pk: string; sk: string } | undefined;
        if (typeof input.Limit === 'number' && matches.length > input.Limit) {
          matches = matches.slice(0, input.Limit);
          const last = matches[matches.length - 1];
          LastEvaluatedKey = { pk: last.pk, sk: last.sk };
        }
        return { Items: matches, LastEvaluatedKey };
      }
      case 'ScanCommand': return { Items: [...this.items.values()] };
      case 'BatchWriteCommand': return {};
      default: throw new Error(`Unsupported fake command ${command.constructor.name}`);
    }
  }

  private key(value: { pk: string; sk: string }) { return `${value.pk}|${value.sk}`; }
}

const config: AwsProviderConfig = {
  region: 'ap-south-1', userPoolId: 'ap-south-1_test', userPoolClientId: 'client', tableName: 'table',
  backupBucket: 'bucket', phoneCountryCode: '+91', presignedUrlSeconds: 300, masterPinSecretArn: 'master-pin-secret'
};

test('AWS DynamoDB sync preserves tenant, idempotency, version, and cursor contracts', async () => {
  const client = new FakeDocumentClient();
  const store = new AwsDataStore(client as any, config.tableName);
  const insert = { operationId: 'operation-1', companyId: 'company-a', entityType: 'Product', entityId: 'product-1', operation: 'INSERT' as const, schemaVersion: 1, payload: { name: 'Tea' } };
  const first = await store.applySyncBatch('company-a', [insert]);
  assert.deepEqual(first[0], { operationId: 'operation-1', status: 'APPLIED', version: 1 });
  assert.equal((await store.applySyncBatch('company-a', [insert]))[0].status, 'DUPLICATE');
  const conflict = await store.applySyncBatch('company-a', [{ ...insert, operationId: 'operation-2', operation: 'UPDATE', baseVersion: 99 }]);
  assert.equal(conflict[0].status, 'CONFLICT');
  const pull = await store.pullSync('company-a', '0', 10);
  assert.equal(pull.records.length, 1);
  assert.equal(pull.nextCursor, '1');
  await assert.rejects(() => store.applySyncBatch('company-a', [{ ...insert, operationId: 'operation-3', companyId: 'company-b' }]), /tenant/i);
});

test('AWS sync pull reconstructs full state from durable records even after change-log TTL expiry', async () => {
  const client = new FakeDocumentClient();
  const store = new AwsDataStore(client as any, config.tableName);
  const companyId = 'company-a';
  const entityIds = ['p1', 'p2', 'p3', 'p4', 'p5'];
  for (const entityId of entityIds) {
    const insert = { operationId: `seed-${entityId}`, companyId, entityType: 'Product', entityId, operation: 'INSERT' as const, schemaVersion: 1, payload: { name: entityId } };
    assert.equal((await store.applySyncBatch(companyId, [insert]))[0].status, 'APPLIED');
  }

  // Simulate the change-log TTL expiring: delete every CHANGE# row directly, leaving only the
  // durable RECORD# rows behind — a plain tail pull from "0" would now see nothing at all.
  for (const [key, item] of [...client.items.entries()]) {
    if (item.sk.startsWith('CHANGE#')) client.items.delete(key);
  }

  // Drive the snapshot phase to completion with a small page size, like PullWorker's
  // `while (hasMore)` loop, and confirm it still reconstructs everything from RECORD# rows.
  let cursor = '0';
  const collected: string[] = [];
  for (let guard = 0; guard < 10; guard += 1) {
    const page = await store.pullSync(companyId, cursor, 2);
    page.records.forEach(record => collected.push(record.entityId));
    cursor = page.nextCursor;
    if (!page.hasMore) break;
  }
  assert.deepEqual(collected.sort(), entityIds.slice().sort());
  assert.match(cursor, /^\d+$/, 'cursor should have switched to a plain numeric tail sequence once the scan finished');

  // A change made after the snapshot completed is delivered exactly once, via the tail.
  const followUp = { operationId: 'follow-up', companyId, entityType: 'Product', entityId: 'p6', operation: 'INSERT' as const, schemaVersion: 1, payload: { name: 'p6' } };
  await store.applySyncBatch(companyId, [followUp]);
  const tailPage = await store.pullSync(companyId, cursor, 10);
  assert.deepEqual(tailPage.records.map(record => record.entityId), ['p6']);
});

test('AWS sync pull rejects a snapshot cursor whose page token points at another tenant', async () => {
  const client = new FakeDocumentClient();
  const store = new AwsDataStore(client as any, config.tableName);
  for (const companyId of ['company-a', 'company-b']) {
    for (const entityId of ['p1', 'p2', 'p3']) {
      const insert = { operationId: `${companyId}-${entityId}`, companyId, entityType: 'Product', entityId, operation: 'INSERT' as const, schemaVersion: 1, payload: { name: entityId } };
      assert.equal((await store.applySyncBatch(companyId, [insert]))[0].status, 'APPLIED');
    }
  }

  // Company B pulls one page and keeps the resume cursor it was handed.
  const bPage = await store.pullSync('company-b', '0', 1);
  assert.equal(bPage.hasMore, true);
  const bCursor = bPage.nextCursor;

  // Company A replays it. The page token inside carries company B's partition key, so it must
  // be refused outright rather than used as a bookmark into the query.
  await assert.rejects(
    () => store.pullSync('company-a', bCursor, 10),
    (error: any) => {
      assert(error instanceof AppError);
      assert.equal(error.code, 'SYNC_CURSOR_INVALID');
      return true;
    }
  );

  // A token that is not even valid base64url JSON is refused the same way.
  await assert.rejects(
    () => store.pullSync('company-a', 'S1:not-a-real-token', 10),
    (error: any) => error instanceof AppError && error.code === 'SYNC_CURSOR_INVALID'
  );

  // Company A's own cursor still works, so the check is not simply rejecting everything.
  const aPage = await store.pullSync('company-a', '0', 1);
  const aResume = await store.pullSync('company-a', aPage.nextCursor, 10);
  assert.equal(aResume.records.every(record => record.companyId === 'company-a'), true);
});

test('AWS sync pull never misreads an existing plain numeric cursor as a snapshot resume', async () => {
  const client = new FakeDocumentClient();
  const store = new AwsDataStore(client as any, config.tableName);
  const companyId = 'company-a';
  await store.applySyncBatch(companyId, [{ operationId: 'op-1', companyId, entityType: 'Product', entityId: 'p1', operation: 'INSERT' as const, schemaVersion: 1, payload: { name: 'p1' } }]);
  await store.applySyncBatch(companyId, [{ operationId: 'op-2', companyId, entityType: 'Product', entityId: 'p2', operation: 'INSERT' as const, schemaVersion: 1, payload: { name: 'p2' } }]);
  const page = await store.pullSync(companyId, '1', 10);
  assert.deepEqual(page.records.map(record => record.entityId), ['p2']);
});

test('AWS shop profile is tenant-partitioned and atomically mirrors shop summary', async () => {
  const client = new FakeDocumentClient();
  const store = new AwsDataStore(client as any, config.tableName);
  const account = await store.createAccount({ phone: '9876543210', displayName: 'Owner', businessName: 'Initial Shop', password: '123456' });
  const updated = await store.updateShopProfile(account.user.companyId, { shopName: 'Cloud Shop', ownerName: 'Cloud Owner', address: 'Madurai', updatedByUserId: account.user.userId });
  assert.equal(updated.shopName, 'Cloud Shop');
  assert.equal(updated.version, 2);
  assert.equal((await store.getLicense(account.user.companyId))?.businessName, 'Cloud Shop');
  assert.equal((await store.findUserById(account.user.userId))?.businessName, 'Cloud Shop');
  assert.equal(await store.getShopProfile('other-company'), null);
});

test('AWS Cognito provider provisions backend-owned users and returns ID tokens', async () => {
  const user: UserAccount = { userId: 'user-1', companyId: 'company-a', phone: '9876543210', displayName: 'Owner', role: 'ADMIN', permissions: ['USER_MANAGE'], status: 'ACTIVE', createdAtEpochMs: 1, updatedAtEpochMs: 1, isCloudTier: true };
  const commands: string[] = [];
  const client = { send: async (command: any) => {
    commands.push(command.constructor.name);
    if (command.constructor.name === 'AdminGetUserCommand') throw Object.assign(new Error('missing'), { name: 'UserNotFoundException' });
    if (command.constructor.name === 'AdminInitiateAuthCommand') return { AuthenticationResult: { IdToken: 'id-token', RefreshToken: 'refresh-token', ExpiresIn: 900 } };
    return {};
  } };
  const dataStore = { findUserById: async () => user, findUserByPhone: async () => user };
  const identity = new AwsIdentityProvider(client as any, dataStore as any, config);
  await identity.setPassword(user.userId, '123456');
  assert.ok(commands.includes('AdminCreateUserCommand'));
  assert.ok(commands.includes('AdminSetUserPasswordCommand'));
  const login = await identity.authenticate(user.phone, '123456');
  assert.equal(login.tokens.accessToken, 'id-token');
  assert.equal(login.tokens.refreshToken, 'refresh-token');
});

test('AWS staff cloud-tier defaults to true and Master Control can grant/revoke a cloud-access end date', async () => {
  const client = new FakeDocumentClient();
  const store = new AwsDataStore(client as any, config.tableName);
  const owner = await store.createAccount({ phone: '9876543210', displayName: 'Owner', businessName: 'Shop', password: '123456' });

  const onlineStaff = await store.createStaff({ companyId: owner.user.companyId, phone: '9876500001', displayName: 'Online Cashier', password: '111111', permissions: ['SALE_CREATE'] });
  assert.equal(onlineStaff.isCloudTier, true);
  assert.equal(onlineStaff.cloudAccessGrantedUntilEpochMs, undefined);

  const offlineStaff = await store.createStaff({ companyId: owner.user.companyId, phone: '9876500002', displayName: 'Offline Cashier', password: '222222', permissions: ['SALE_CREATE'], isCloudTier: false });
  assert.equal(offlineStaff.isCloudTier, false);

  const granted = await store.updateStaff(owner.user.companyId, onlineStaff.userId, { cloudAccessGrantedUntilEpochMs: 1_700_000_000_000 });
  assert.equal(granted.cloudAccessGrantedUntilEpochMs, 1_700_000_000_000);
  assert.equal(granted.isCloudTier, true, 'unrelated field updates must not disturb isCloudTier');

  const revoked = await store.updateStaff(owner.user.companyId, onlineStaff.userId, { isCloudTier: false });
  assert.equal(revoked.isCloudTier, false);
  assert.equal(revoked.cloudAccessGrantedUntilEpochMs, 1_700_000_000_000, 'unrelated field updates must not disturb the granted date');
});

test('AWS Master Control can set the shop OWNER\'s own cloud access, separate from staff', async () => {
  const client = new FakeDocumentClient();
  const store = new AwsDataStore(client as any, config.tableName);
  const owner = await store.createAccount({ phone: '9876543210', displayName: 'Owner', businessName: 'Shop', password: '123456', isCloudTier: false });

  const found = await store.findAdminByCompany(owner.user.companyId);
  assert.equal(found?.userId, owner.user.userId);
  assert.equal(found?.isCloudTier, false);

  const upgraded = await store.updateAccountCloudAccess(owner.user.companyId, owner.user.userId, { isCloudTier: true, cloudAccessGrantedUntilEpochMs: 1_800_000_000_000 });
  assert.equal(upgraded.isCloudTier, true);
  assert.equal(upgraded.cloudAccessGrantedUntilEpochMs, 1_800_000_000_000);

  await assert.rejects(() => store.updateAccountCloudAccess('someone-elses-company', owner.user.userId, { isCloudTier: false }));
});

test('AWS S3 provider signs tenant-scoped uploads and verifies completion metadata', async () => {
  const dynamo = new FakeDocumentClient();
  const content = Buffer.from('encrypted-backup');
  const checksum = crypto.createHash('sha256').update(content).digest('hex');
  const checksumBase64 = Buffer.from(checksum, 'hex').toString('base64');
  const s3 = { send: async (command: any) => {
    if (command.constructor.name === 'HeadObjectCommand') return { ContentLength: content.length, ChecksumSHA256: checksumBase64, Metadata: { companyid: 'company-a', checksumsha256: checksum } };
    return {};
  } };
  const signer = async (_client: any, command: any) => `https://signed.example/${command.constructor.name}`;
  const storage = new AwsObjectStorage(s3 as any, dynamo as any, config, signer as any);
  const intent = await storage.createUploadIntent({ companyId: 'company-a', fileName: 'backup.zip', sizeBytes: content.length, checksumSha256: checksum, schemaVersion: 22 });
  assert.match(intent.objectKey, /^tenants\/company-a\/backups\//);
  assert.equal(intent.requiredHeaders['x-amz-checksum-sha256'], checksumBase64);
  assert.equal((await storage.complete('company-a', intent.backupId)).status, 'READY');
  const download = await storage.createDownloadIntent('company-a', intent.backupId);
  assert.match(download.downloadUrl, /GetObjectCommand$/);
  await assert.rejects(() => storage.createDownloadIntent('company-b', intent.backupId), /not found/i);
});

test('AWS session store isolates registered device sessions by tenant and user', async () => {
  const client = new FakeDocumentClient();
  const sessions = new AwsSessionStore(client as any, config.tableName);
  await sessions.register({ sessionId: 'session-1', companyId: 'company-a', userId: 'user-1', deviceId: 'device-1234', expiresAtEpochMs: Date.now() + 60_000 });
  assert.equal(await sessions.validate('company-a', 'user-1', 'session-1'), true);
  assert.equal(await sessions.validate('company-b', 'user-1', 'session-1'), false);
  assert.equal(await sessions.validate('company-a', 'user-2', 'session-1'), false);
  await sessions.revoke('company-a', 'user-1', 'session-1');
  assert.equal(await sessions.validate('company-a', 'user-1', 'session-1'), false);
});

test('AWS provider registry fails fast on missing MSG91 configuration without fallback keys', () => {
  const originalEnv = { ...process.env };
  try {
    process.env.PROVIDER_MODE = 'aws';
    process.env.AWS_REGION = 'ap-southeast-2';
    process.env.AWS_COGNITO_USER_POOL_ID = 'ap-southeast-2_testPoolId';
    process.env.AWS_COGNITO_CLIENT_ID = 'client-1';
    process.env.AWS_DYNAMODB_TABLE = 'table-1';
    process.env.AWS_S3_BACKUP_BUCKET = 'bucket-1';
    process.env.AWS_MASTER_PIN_SECRET_ARN = 'arn:aws:secretsmanager:ap-southeast-2:123456789012:secret:master-pin-1';
    delete process.env.SMS_PROVIDER;
    delete process.env.MSG91_WIDGET_ID;
    delete process.env.MSG91_TOKEN_AUTH;
    delete process.env.MSG91_AUTH_KEY;

    assert.throws(
      () => createProviderRegistry(),
      (err: any) => {
        assert(err instanceof AppError);
        assert.equal(err.code, 'MSG91_CONFIG_MISSING');
        assert(err.message.includes('MSG91_WIDGET_ID'));
        assert(err.message.includes('MSG91_TOKEN_AUTH'));
        assert(err.message.includes('MSG91_AUTH_KEY'));
        return true;
      }
    );

    // Partial configuration still fails fast
    process.env.MSG91_WIDGET_ID = 'valid_widget';
    assert.throws(
      () => createProviderRegistry(),
      (err: any) => {
        assert(err instanceof AppError);
        assert.equal(err.code, 'MSG91_CONFIG_MISSING');
        assert(!err.message.includes('MSG91_WIDGET_ID'));
        assert(err.message.includes('MSG91_TOKEN_AUTH'));
        assert(err.message.includes('MSG91_AUTH_KEY'));
        return true;
      }
    );

    // Full configuration initializes Msg91SmsSender successfully
    process.env.MSG91_TOKEN_AUTH = 'valid_token';
    process.env.MSG91_AUTH_KEY = 'valid_key';
    const registry = createProviderRegistry();
    assert.equal(registry.mode, 'aws');
    assert(registry.smsSender instanceof Msg91SmsSender);

    // SMS_PROVIDER=sns initializes AwsSmsSender
    process.env.SMS_PROVIDER = 'sns';
    delete process.env.MSG91_WIDGET_ID;
    delete process.env.MSG91_TOKEN_AUTH;
    delete process.env.MSG91_AUTH_KEY;
    const snsRegistry = createProviderRegistry();
    assert(snsRegistry.smsSender instanceof AwsSmsSender);
  } finally {
    process.env = originalEnv;
  }
});

/**
 * Pins the divergence documented on IdentityProvider.refresh: Cognito does not issue a new refresh
 * token on REFRESH_TOKEN_AUTH, so the AWS provider hands the caller's own token straight back and
 * that token keeps working, where the local provider rotates it and rejects the old one. The JWT
 * verifier is stubbed rather than fed a signed token - what is under test is which refresh token
 * comes back out, not Cognito's signature checking.
 */
test('AWS refresh reuses the caller refresh token, where the local provider would have rotated it', async () => {
  const user: UserAccount = { userId: 'user-1', companyId: 'company-a', phone: '9876543210', displayName: 'Owner', role: 'ADMIN', permissions: ['USER_MANAGE'], status: 'ACTIVE', createdAtEpochMs: 1, updatedAtEpochMs: 1, isCloudTier: true };
  const seen: string[] = [];
  const client = { send: async (command: any) => {
    seen.push(command.input?.AuthParameters?.REFRESH_TOKEN ?? '');
    // Cognito omits RefreshToken from a REFRESH_TOKEN_AUTH result.
    return { AuthenticationResult: { IdToken: 'fresh-id-token', ExpiresIn: 900 } };
  } };
  const dataStore = { findUserById: async (id: string) => (id === user.userId ? user : null) };
  const identity = new AwsIdentityProvider(client as any, dataStore as any, config);
  (identity as any).verifier = { verify: async () => ({ 'custom:user_id': 'user-1', 'custom:company_id': 'company-a', 'custom:role': 'ADMIN', 'custom:permissions': '["USER_MANAGE"]' }) };

  const first = await identity.refresh('caller-refresh-token');
  assert.equal(first.tokens.accessToken, 'fresh-id-token');
  assert.equal(first.tokens.refreshToken, 'caller-refresh-token');

  // The same token is still good on the next call - that is the whole difference from local.
  const second = await identity.refresh(first.tokens.refreshToken);
  assert.equal(second.tokens.refreshToken, 'caller-refresh-token');
  assert.deepEqual(seen, ['caller-refresh-token', 'caller-refresh-token']);
});

test('AWS refresh refuses a token whose account is no longer active', async () => {
  const inactive: UserAccount = { userId: 'user-2', companyId: 'company-a', phone: '9876500000', displayName: 'Staff', role: 'CASHIER', permissions: [], status: 'INACTIVE', createdAtEpochMs: 1, updatedAtEpochMs: 1, isCloudTier: true };
  const client = { send: async () => ({ AuthenticationResult: { IdToken: 'fresh-id-token', ExpiresIn: 900 } }) };
  const identity = new AwsIdentityProvider(client as any, { findUserById: async () => inactive } as any, config);
  (identity as any).verifier = { verify: async () => ({ 'custom:user_id': 'user-2', 'custom:company_id': 'company-a', 'custom:role': 'CASHIER' }) };
  await assert.rejects(() => identity.refresh('caller-refresh-token'), (error: any) => error.code === 'AUTH_REFRESH_INVALID');
});
