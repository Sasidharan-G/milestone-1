import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import { AwsDataStore } from '../providers/aws/awsDataStore';
import { AwsIdentityProvider } from '../providers/aws/awsIdentityProvider';
import { AwsObjectStorage } from '../providers/aws/awsObjectStorage';
import { AwsSessionStore } from '../providers/aws/awsSessionStore';
import { AwsProviderConfig } from '../providers/aws/awsConfig';
import { UserAccount } from '../providers/contracts';

class FakeDocumentClient {
  readonly items = new Map<string, any>();
  sequence = 0;

  async send(command: any): Promise<any> {
    const input = command.input;
    switch (command.constructor.name) {
      case 'GetCommand': return { Item: this.items.get(this.key(input.Key)) };
      case 'PutCommand': this.items.set(this.key(input.Item), input.Item); return {};
      case 'DeleteCommand': this.items.delete(this.key(input.Key)); return {};
      case 'UpdateCommand': {
        if (input.Key.sk === 'SYNC_COUNTER') return { Attributes: { sequence: ++this.sequence } };
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
        const Items = [...this.items.values()].filter(item => item.pk === company && (!prefix || item.sk.startsWith(prefix)) && (!from || (item.sk >= from && item.sk <= to))).sort((left, right) => left.sk.localeCompare(right.sk));
        return { Items };
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

test('AWS Cognito provider provisions backend-owned users and returns ID tokens', async () => {
  const user: UserAccount = { userId: 'user-1', companyId: 'company-a', phone: '9876543210', displayName: 'Owner', role: 'ADMIN', permissions: ['USER_MANAGE'], status: 'ACTIVE', createdAtEpochMs: 1, updatedAtEpochMs: 1 };
  const commands: string[] = [];
  const client = { send: async (command: any) => {
    commands.push(command.constructor.name);
    if (command.constructor.name === 'AdminGetUserCommand') throw Object.assign(new Error('missing'), { name: 'UserNotFoundException' });
    if (command.constructor.name === 'AdminInitiateAuthCommand') return { AuthenticationResult: { IdToken: 'id-token', RefreshToken: 'refresh-token', ExpiresIn: 900 } };
    return {};
  } };
  const dataStore = { findUserById: async () => user, findUserByPhone: async () => user };
  const identity = new AwsIdentityProvider(client as any, dataStore as any, config);
  await identity.setPassword(user.userId, 'secret1');
  assert.ok(commands.includes('AdminCreateUserCommand'));
  assert.ok(commands.includes('AdminSetUserPasswordCommand'));
  const login = await identity.authenticate(user.phone, 'secret1');
  assert.equal(login.tokens.accessToken, 'id-token');
  assert.equal(login.tokens.refreshToken, 'refresh-token');
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
