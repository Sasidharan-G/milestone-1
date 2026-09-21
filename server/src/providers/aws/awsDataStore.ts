import { randomUUID } from 'node:crypto';
import {
  BatchWriteCommand,
  DynamoDBDocumentClient,
  GetCommand,
  PutCommand,
  QueryCommand,
  ScanCommand,
  TransactWriteCommand,
  UpdateCommand
} from '@aws-sdk/lib-dynamodb';
import { AppError } from '../../core/errors';
import { GetSecretValueCommand, PutSecretValueCommand, SecretsManagerClient } from '@aws-sdk/client-secrets-manager';
import {
  AuditEntry,
  CloudRecord,
  DataStore,
  LicenseRecord,
  ShopProfileRecord,
  MasterConfig,
  NewAccountInput,
  StaffInput,
  SyncActor,
  SyncOperation,
  SyncPage,
  SyncResult,
  UserAccount
} from '../contracts';
import { activePermissions } from '../local/localDataStore';
import { newTrialLicense } from '../../core/license';
import { isAwsError, mapAwsError } from './awsErrors';
import { encodeSnapshotCursor, encodeTailCursor, parseCursor } from '../sync/syncCursor';
import { CHANGE_GAP_SETTLE_MS, SNAPSHOT_TAIL_OVERLAP, decideSyncOperation, permissionDenial, rejectedResult, rejectionReason, replayStoredResult } from '../sync/syncRules';

const companyPk = (companyId: string) => `COMPANY#${companyId}`;
const userPk = (userId: string) => `USER#${userId}`;
const phonePk = (phone: string) => `PHONE#${phone}`;
const recordSk = (entityType: string, entityId: string) => `RECORD#${entityType}#${entityId}`;
const idempotencySk = (operationId: string) => `IDEMPOTENCY#${operationId}`;
const sequenceWidth = 20;
const changeSk = (sequence: number) => `CHANGE#${String(sequence).padStart(sequenceWidth, '0')}`;
const ttlSeconds = (milliseconds: number) => Math.floor(milliseconds / 1000);

interface StoredItem<T> {
  pk: string;
  sk: string;
  itemType: string;
  data: T;
  gsi1pk?: string;
  gsi1sk?: string;
  expiresAtEpochSeconds?: number;
}

export class AwsDataStore implements DataStore {
  constructor(
    private readonly client: DynamoDBDocumentClient,
    private readonly tableName: string,
    private readonly secrets?: SecretsManagerClient,
    private readonly masterPinSecretArn?: string
  ) {}

  async createAccount(input: NewAccountInput): Promise<{ user: UserAccount; license: LicenseRecord }> {
    const now = Date.now();
    const companyId = randomUUID();
    const user: UserAccount = {
      userId: randomUUID(), companyId, phone: input.phone, displayName: input.displayName, businessName: input.businessName,
      role: 'ADMIN', permissions: [...activePermissions], status: 'ACTIVE', createdAtEpochMs: now, updatedAtEpochMs: now
    };
    const license: LicenseRecord = newTrialLicense(companyId, input.phone, input.businessName, input.displayName, now);
    const shopProfile: ShopProfileRecord = { companyId, shopName: input.businessName, ownerName: input.displayName, gstNumber: '', address: '', phone: input.phone, email: '', version: 1, updatedAtEpochMs: now, updatedByUserId: user.userId };
    try {
      await this.client.send(new TransactWriteCommand({ TransactItems: [
        { Put: { TableName: this.tableName, Item: this.phoneItem(user), ConditionExpression: 'attribute_not_exists(pk)' } },
        { Put: { TableName: this.tableName, Item: this.userItem(user), ConditionExpression: 'attribute_not_exists(pk)' } },
        { Put: { TableName: this.tableName, Item: this.companyItem(companyId, 'LICENSE', license), ConditionExpression: 'attribute_not_exists(pk) AND attribute_not_exists(sk)' } },
        { Put: { TableName: this.tableName, Item: this.companyItem(companyId, 'SHOP_PROFILE', shopProfile), ConditionExpression: 'attribute_not_exists(pk) AND attribute_not_exists(sk)' } }
      ] }));
      return { user, license };
    } catch (error) {
      if (isAwsError(error, 'TransactionCanceledException')) throw new AppError(409, 'ACCOUNT_PHONE_EXISTS', 'Mobile number is already registered');
      throw mapAwsError(error, 'DynamoDB account creation');
    }
  }

  async findUserByPhone(phone: string): Promise<UserAccount | null> {
    const lock = await this.get<{ userId: string }>(phonePk(phone), 'USER');
    return lock ? this.findUserById(lock.userId) : null;
  }

  findUserById(userId: string): Promise<UserAccount | null> { return this.get<UserAccount>(userPk(userId), 'PROFILE'); }

  async listStaff(companyId: string): Promise<UserAccount[]> {
    const users = await this.queryCompanyUsers(companyId);
    return users.filter(user => user.role === 'CASHIER');
  }

  listCompanyUsers(companyId: string): Promise<UserAccount[]> { return this.queryCompanyUsers(companyId); }

  async createStaff(input: StaffInput): Promise<UserAccount> {
    const now = Date.now();
    const user: UserAccount = {
      userId: randomUUID(), companyId: input.companyId, phone: input.phone, displayName: input.displayName, role: 'CASHIER',
      permissions: input.permissions.filter(permission => activePermissions.includes(permission) && permission !== 'USER_MANAGE'),
      status: 'ACTIVE', createdAtEpochMs: now, updatedAtEpochMs: now
    };
    try {
      await this.client.send(new TransactWriteCommand({ TransactItems: [
        { Put: { TableName: this.tableName, Item: this.phoneItem(user), ConditionExpression: 'attribute_not_exists(pk)' } },
        { Put: { TableName: this.tableName, Item: this.userItem(user), ConditionExpression: 'attribute_not_exists(pk)' } }
      ] }));
      return user;
    } catch (error) {
      if (isAwsError(error, 'TransactionCanceledException')) throw new AppError(409, 'ACCOUNT_PHONE_EXISTS', 'Mobile number is already registered');
      throw mapAwsError(error, 'DynamoDB staff creation');
    }
  }

  async deleteStaff(companyId: string, userId: string): Promise<void> {
    const user = await this.findUserById(userId);
    if (!user || user.companyId !== companyId || user.role !== 'CASHIER') return;
    try {
      await this.client.send(new TransactWriteCommand({ TransactItems: [
        { Delete: { TableName: this.tableName, Key: { pk: userPk(userId), sk: 'PROFILE' } } },
        { Delete: { TableName: this.tableName, Key: { pk: phonePk(user.phone), sk: 'USER' } } }
      ] }));
    } catch (error) { throw mapAwsError(error, 'DynamoDB staff rollback'); }
  }

  async updateStaff(companyId: string, userId: string, changes: Partial<Pick<UserAccount, 'displayName' | 'permissions' | 'status'>>): Promise<UserAccount> {
    const current = await this.findUserById(userId);
    if (!current || current.companyId !== companyId || current.role !== 'CASHIER') throw new AppError(404, 'STAFF_NOT_FOUND', 'Staff account was not found');
    const updated: UserAccount = {
      ...current,
      ...(changes.displayName === undefined ? {} : { displayName: changes.displayName }),
      ...(changes.status === undefined ? {} : { status: changes.status }),
      ...(changes.permissions === undefined ? {} : { permissions: changes.permissions.filter(permission => activePermissions.includes(permission) && permission !== 'USER_MANAGE') }),
      updatedAtEpochMs: Date.now()
    };
    try {
      await this.client.send(new PutCommand({ TableName: this.tableName, Item: this.userItem(updated), ConditionExpression: 'attribute_exists(pk)' }));
      return updated;
    } catch (error) { throw mapAwsError(error, 'DynamoDB staff update'); }
  }

  getLicense(companyId: string): Promise<LicenseRecord | null> { return this.get<LicenseRecord>(companyPk(companyId), 'LICENSE'); }

  getShopProfile(companyId: string): Promise<ShopProfileRecord | null> { return this.get<ShopProfileRecord>(companyPk(companyId), 'SHOP_PROFILE'); }

  async updateShopProfile(companyId: string, changes: Partial<Omit<ShopProfileRecord, 'companyId' | 'version' | 'updatedAtEpochMs'>>): Promise<ShopProfileRecord> {
    const [license, current, users] = await Promise.all([this.getLicense(companyId), this.getShopProfile(companyId), this.queryCompanyUsers(companyId)]);
    if (!license) throw new AppError(404, 'LICENSE_NOT_FOUND', 'Company license was not found');
    const now = Date.now();
    const fallback: ShopProfileRecord = { companyId, shopName: license.businessName || '', ownerName: license.ownerName || '', gstNumber: '', address: '', phone: license.ownerMobile || '', email: '', version: 0, updatedAtEpochMs: 0 };
    const base = current || fallback;
    const updated: ShopProfileRecord = { ...base, ...changes, companyId, version: base.version + 1, updatedAtEpochMs: now };
    const updatedLicense: LicenseRecord = { ...license, businessName: updated.shopName, ownerName: updated.ownerName, updatedAtEpochMs: now };
    const updatedUsers = users.map(user => ({ ...user, businessName: updated.shopName, ...(user.role === 'ADMIN' ? { displayName: updated.ownerName } : {}), updatedAtEpochMs: now }));
    try {
      await this.client.send(new TransactWriteCommand({ TransactItems: [
        { Put: { TableName: this.tableName, Item: this.companyItem(companyId, 'SHOP_PROFILE', updated) } },
        { Put: { TableName: this.tableName, Item: this.companyItem(companyId, 'LICENSE', updatedLicense), ConditionExpression: 'attribute_exists(pk)' } },
        ...updatedUsers.map(user => ({ Put: { TableName: this.tableName, Item: this.userItem(user), ConditionExpression: 'attribute_exists(pk)' } }))
      ] }));
      return updated;
    } catch (error) { throw mapAwsError(error, 'DynamoDB shop profile update'); }
  }

  async consumeNonce(scope: string, nonce: string, expiresAtEpochMs: number): Promise<boolean> {
    const pk = `NONCE#${scope}#${nonce}`;
    try {
      await this.client.send(new PutCommand({
        TableName: this.tableName,
        Item: { pk, sk: 'VALUE', itemType: 'NONCE', expiresAtEpochSeconds: ttlSeconds(expiresAtEpochMs) },
        ConditionExpression: 'attribute_not_exists(pk) OR expiresAtEpochSeconds < :now',
        ExpressionAttributeValues: { ':now': ttlSeconds(Date.now()) }
      }));
      return true;
    } catch (error) {
      if (isAwsError(error, 'ConditionalCheckFailedException')) return false;
      throw mapAwsError(error, 'DynamoDB nonce consumption');
    }
  }

  async applySyncBatch(companyId: string, operations: SyncOperation[], actor?: SyncActor): Promise<SyncResult[]> {
    const results: SyncResult[] = [];
    for (const operation of operations) results.push(await this.applySyncOperation(companyId, operation, actor));
    return results;
  }

  async pullSync(companyId: string, cursor: string, limit: number): Promise<SyncPage> {
    const pk = companyPk(companyId);
    const parsed = parseCursor(cursor);
    if (parsed.mode === 'tail') {
      try {
        const response = await this.client.send(new QueryCommand({
          TableName: this.tableName,
          KeyConditionExpression: 'pk = :pk AND sk BETWEEN :from AND :to',
          ExpressionAttributeValues: { ':pk': pk, ':from': changeSk(parsed.afterSequence + 1), ':to': 'CHANGE#~' },
          Limit: limit,
          ConsistentRead: true
        }));
        const items = (response.Items || []) as Array<StoredItem<{ sequence: number; record: CloudRecord }>>;
        // Stop in front of a recent hole in the sequence: that write is still in flight and would
        // otherwise be stepped over for good (see CHANGE_GAP_SETTLE_MS).
        const records: CloudRecord[] = [];
        let last = parsed.afterSequence;
        let heldBack = false;
        const now = Date.now();
        for (const item of items) {
          const { sequence, record } = item.data;
          if (sequence !== last + 1 && now - (record.updatedAtEpochMs || 0) < CHANGE_GAP_SETTLE_MS) { heldBack = true; break; }
          records.push(record);
          last = sequence;
        }
        return { records, nextCursor: encodeTailCursor(last), hasMore: !heldBack && Boolean(response.LastEvaluatedKey) };
      } catch (error) { throw mapAwsError(error, 'DynamoDB sync pull'); }
    }
    // Fresh pull (new device, full resync, or "restore from cloud"): the CHANGE# log this
    // tail query reads from is TTL-pruned (see applySyncOperation), so a cursor starting
    // at 0 can't rely on it alone. Reconstruct from the durable RECORD# rows instead, then
    // hand off to the ordinary tail above once the scan is exhausted.
    const capturedMaxSequence = parsed.mode === 'snapshotStart' ? await this.peekSequence(pk) : parsed.capturedMaxSequence;
    const pageToken = parsed.mode === 'snapshotResume' ? parsed.pageToken : undefined;
    const { items, nextPageToken } = await this.queryPartitionPage<CloudRecord>(pk, 'RECORD#', limit, pageToken);
    if (nextPageToken) {
      return { records: items.map(item => item.data), nextCursor: encodeSnapshotCursor(capturedMaxSequence, nextPageToken), hasMore: true };
    }
    return { records: items.map(item => item.data), nextCursor: encodeTailCursor(Math.max(0, capturedMaxSequence - SNAPSHOT_TAIL_OVERLAP)), hasMore: false };
  }

  async getSyncEpoch(companyId: string): Promise<number> {
    try {
      const response = await this.client.send(new GetCommand({ TableName: this.tableName, Key: { pk: companyPk(companyId), sk: 'SYNC_EPOCH' }, ConsistentRead: true }));
      const epoch = Number(response.Item?.epoch);
      return Number.isSafeInteger(epoch) ? epoch : 0;
    } catch (error) { throw mapAwsError(error, 'DynamoDB sync epoch read'); }
  }

  async bumpSyncEpoch(companyId: string): Promise<number> {
    try {
      const response = await this.client.send(new UpdateCommand({
        TableName: this.tableName, Key: { pk: companyPk(companyId), sk: 'SYNC_EPOCH' },
        UpdateExpression: 'SET itemType = if_not_exists(itemType, :type) ADD #epoch :one',
        ExpressionAttributeNames: { '#epoch': 'epoch' }, ExpressionAttributeValues: { ':one': 1, ':type': 'SYNC_EPOCH' }, ReturnValues: 'UPDATED_NEW'
      }));
      const epoch = Number(response.Attributes?.epoch);
      if (!Number.isSafeInteger(epoch)) throw new AppError(502, 'SYNC_EPOCH_INVALID', 'DynamoDB returned an invalid sync epoch', true);
      return epoch;
    } catch (error) { throw mapAwsError(error, 'DynamoDB sync epoch update'); }
  }

  async purgeCompanyRecords(companyId: string): Promise<number> {
    const pk = companyPk(companyId);
    const records = await this.queryPartition<CloudRecord>(pk, 'RECORD#');
    const changes = await this.queryPartition<unknown>(pk, 'CHANGE#');
    // applySyncOperation short-circuits on a cached operationId and returns DUPLICATE. Leaving
    // these rows behind after a purge means a client replaying its outbox gets DUPLICATE for a
    // record that no longer exists, so the entity is silently dropped.
    const idempotency = await this.queryPartition<unknown>(pk, 'IDEMPOTENCY#');
    // Batched 25 at a time: one DeleteCommand per row took minutes for a real shop and ran past
    // API Gateway's 29-second limit, leaving the purge half done.
    await this.batchDelete([...records, ...changes, ...idempotency].map(item => ({ pk: item.pk, sk: item.sk })), 'DynamoDB sync purge');
    return records.length;
  }

  async adminOverview(): Promise<{ licenses: LicenseRecord[]; users: UserAccount[]; masterConfig: MasterConfig }> {
    const items = await this.scanAll();
    return {
      licenses: items.filter(item => item.itemType === 'LICENSE').map(item => item.data as LicenseRecord),
      users: items.filter(item => item.itemType === 'USER').map(item => item.data as UserAccount),
      masterConfig: await this.getMasterConfig()
    };
  }

  async updateLicense(companyId: string, changes: Partial<LicenseRecord>): Promise<LicenseRecord> {
    const current = await this.getLicense(companyId);
    if (!current) throw new AppError(404, 'LICENSE_NOT_FOUND', 'Company license was not found');
    const updated: LicenseRecord = { ...current, ...changes, companyId, updatedAtEpochMs: Date.now() };
    try {
      await this.client.send(new PutCommand({ TableName: this.tableName, Item: this.companyItem(companyId, 'LICENSE', updated), ConditionExpression: 'attribute_exists(pk)' }));
      return updated;
    } catch (error) { throw mapAwsError(error, 'DynamoDB license update'); }
  }

  async deleteCompany(companyId: string): Promise<void> {
    try {
      const companyItems = await this.queryPartition<unknown>(companyPk(companyId));
      const users = await this.queryCompanyUsers(companyId);
      const keys = [
        ...companyItems.map(item => ({ pk: item.pk, sk: item.sk })),
        ...users.flatMap(user => [{ pk: userPk(user.userId), sk: 'PROFILE' }, { pk: phonePk(user.phone), sk: 'USER' }])
      ];
      await this.batchDelete(keys, 'DynamoDB company deletion');
    } catch (error) { throw mapAwsError(error, 'DynamoDB company deletion'); }
  }

  private async batchDelete(keys: Array<{ pk: string; sk: string }>, operation: string): Promise<void> {
    try {
      for (let index = 0; index < keys.length; index += 25) {
        let pending: Array<{ DeleteRequest: { Key: Record<string, string> } }> = keys.slice(index, index + 25).map(Key => ({ DeleteRequest: { Key } }));
        for (let attempt = 0; pending.length && attempt < 5; attempt += 1) {
          const response = await this.client.send(new BatchWriteCommand({ RequestItems: { [this.tableName]: pending } }));
          pending = (response.UnprocessedItems?.[this.tableName] || []).filter(item => item.DeleteRequest?.Key).map(item => ({ DeleteRequest: { Key: item.DeleteRequest!.Key! as Record<string, string> } }));
          if (pending.length) await new Promise(resolve => setTimeout(resolve, 25 * (2 ** attempt)));
        }
        if (pending.length) throw new AppError(503, 'AWS_BATCH_INCOMPLETE', `${operation} is temporarily incomplete`, true);
      }
    } catch (error) { throw mapAwsError(error, operation); }
  }

  async getMasterConfig(): Promise<MasterConfig> {
    const stored = await this.get<Omit<MasterConfig, 'pin'>>('CONFIG', 'MASTER');
    let pin = process.env.MASTER_ADMIN_PIN || '';
    if (this.secrets && this.masterPinSecretArn) {
      try { pin = (await this.secrets.send(new GetSecretValueCommand({ SecretId: this.masterPinSecretArn }))).SecretString || ''; }
      catch (error) { throw mapAwsError(error, 'Secrets Manager master PIN read'); }
    }
    return { mobile: stored?.mobile || process.env.MASTER_SUPPORT_PHONE || '', pin, updatedAtEpochMs: stored?.updatedAtEpochMs || Date.now() };
  }

  async updateMasterConfig(changes: Pick<MasterConfig, 'mobile' | 'pin'>): Promise<MasterConfig> {
    const updated = { ...changes, updatedAtEpochMs: Date.now() };
    try {
      if (!this.secrets || !this.masterPinSecretArn) throw new AppError(500, 'MASTER_SECRET_NOT_CONFIGURED', 'Master PIN secret is not configured');
      await this.secrets.send(new PutSecretValueCommand({ SecretId: this.masterPinSecretArn, SecretString: changes.pin }));
      await this.client.send(new PutCommand({ TableName: this.tableName, Item: { pk: 'CONFIG', sk: 'MASTER', itemType: 'MASTER_CONFIG', data: { mobile: changes.mobile, updatedAtEpochMs: updated.updatedAtEpochMs } } }));
      return updated;
    } catch (error) { throw mapAwsError(error, 'DynamoDB master configuration update'); }
  }

  async appendAudit(entry: Omit<AuditEntry, 'auditId' | 'createdAtEpochMs'>): Promise<AuditEntry> {
    const stored: AuditEntry = { ...entry, auditId: randomUUID(), createdAtEpochMs: Date.now() };
    const sk = `AUDIT#${String(stored.createdAtEpochMs).padStart(15, '0')}#${stored.auditId}`;
    try {
      // Audit rows age out after 400 days via the table TTL attribute.
      await this.client.send(new PutCommand({ TableName: this.tableName, Item: { ...this.companyItem(entry.companyId, sk, stored), itemType: 'AUDIT', expiresAtEpochSeconds: ttlSeconds(stored.createdAtEpochMs) + 400 * 86_400 } }));
      return stored;
    } catch (error) { throw mapAwsError(error, 'DynamoDB audit write'); }
  }

  async listAudit(companyId: string, limit: number): Promise<AuditEntry[]> {
    try {
      const response = await this.client.send(new QueryCommand({
        TableName: this.tableName, KeyConditionExpression: 'pk = :pk AND begins_with(sk, :prefix)',
        ExpressionAttributeValues: { ':pk': companyPk(companyId), ':prefix': 'AUDIT#' }, ScanIndexForward: false, Limit: limit
      }));
      return (response.Items || []).map(item => (item as StoredItem<AuditEntry>).data);
    } catch (error) { throw mapAwsError(error, 'DynamoDB audit query'); }
  }

  private async applySyncOperation(companyId: string, operation: SyncOperation, actor?: SyncActor): Promise<SyncResult> {
    if (operation.companyId !== companyId) throw new AppError(403, 'TENANT_MISMATCH', 'Operation tenant does not match authenticated tenant');
    const rejection = rejectionReason(operation);
    if (rejection) return rejectedResult(operation, rejection);
    const pk = companyPk(companyId);
    const prior = await this.get<SyncResult>(pk, idempotencySk(operation.operationId));
    if (prior) return replayStoredResult(prior);
    const current = await this.get<CloudRecord>(pk, recordSk(operation.entityType, operation.entityId));
    const decision = decideSyncOperation(current, operation);
    if (decision === 'CONFLICT') return this.storeConflict(pk, operation.operationId, current || undefined);
    if (decision === 'NOOP') return this.storeNoop(pk, operation.operationId, current!.version);
    const denial = actor ? permissionDenial(actor, operation, current) : null;
    if (denial) return rejectedResult(operation, denial);
    const now = Date.now();
    const payload = operation.operation === 'PARTIAL_UPDATE' ? { ...(current?.payload || {}), ...(operation.payload || {}) } : { ...(operation.payload || {}) };
    payload.companyId = companyId;
    const record: CloudRecord = {
      companyId, entityType: operation.entityType, entityId: operation.entityId, version: (current?.version || 0) + 1,
      schemaVersion: operation.schemaVersion, updatedAtEpochMs: now, deleted: operation.operation === 'DELETE', payload
    };
    const sequence = await this.nextSequence(pk);
    const result: SyncResult = { operationId: operation.operationId, status: 'APPLIED', version: record.version };
    const recordItem: StoredItem<CloudRecord> = { pk, sk: recordSk(operation.entityType, operation.entityId), itemType: 'SYNC_RECORD', data: record };
    if (record.deleted) recordItem.expiresAtEpochSeconds = ttlSeconds(now + 90 * 86_400_000);
    try {
      await this.client.send(new TransactWriteCommand({ TransactItems: [
        { Put: {
          TableName: this.tableName, Item: recordItem,
          ConditionExpression: current ? '#data.#version = :version' : 'attribute_not_exists(pk)',
          ...(current ? { ExpressionAttributeNames: { '#data': 'data', '#version': 'version' }, ExpressionAttributeValues: { ':version': current.version } } : {})
        } },
        { Put: { TableName: this.tableName, Item: { pk, sk: changeSk(sequence), itemType: 'SYNC_CHANGE', data: { sequence, record }, expiresAtEpochSeconds: ttlSeconds(now + 365 * 86_400_000) }, ConditionExpression: 'attribute_not_exists(pk) AND attribute_not_exists(sk)' } },
        { Put: { TableName: this.tableName, Item: { pk, sk: idempotencySk(operation.operationId), itemType: 'IDEMPOTENCY', data: result, expiresAtEpochSeconds: ttlSeconds(now + 7 * 86_400_000) }, ConditionExpression: 'attribute_not_exists(pk) AND attribute_not_exists(sk)' } }
      ] }));
      return result;
    } catch (error) {
      if (isAwsError(error, 'TransactionCanceledException')) {
        const duplicate = await this.get<SyncResult>(pk, idempotencySk(operation.operationId));
        if (duplicate) return replayStoredResult(duplicate);
        const authoritative = await this.get<CloudRecord>(pk, recordSk(operation.entityType, operation.entityId));
        return this.storeConflict(pk, operation.operationId, authoritative || undefined);
      }
      throw mapAwsError(error, 'DynamoDB sync mutation');
    }
  }

  private async storeNoop(pk: string, operationId: string, version: number): Promise<SyncResult> {
    const result: SyncResult = { operationId, status: 'APPLIED', version };
    try {
      await this.client.send(new PutCommand({
        TableName: this.tableName,
        Item: { pk, sk: idempotencySk(operationId), itemType: 'IDEMPOTENCY', data: result, expiresAtEpochSeconds: ttlSeconds(Date.now() + 7 * 86_400_000) },
        ConditionExpression: 'attribute_not_exists(pk) AND attribute_not_exists(sk)'
      }));
      return result;
    } catch (error) {
      if (isAwsError(error, 'ConditionalCheckFailedException')) {
        const stored = await this.get<SyncResult>(pk, idempotencySk(operationId));
        return stored ? replayStoredResult(stored) : result;
      }
      throw mapAwsError(error, 'DynamoDB no-op persistence');
    }
  }

  private async storeConflict(pk: string, operationId: string, record?: CloudRecord): Promise<SyncResult> {
    const result: SyncResult = { operationId, status: 'CONFLICT', ...(record ? { record } : {}) };
    try {
      await this.client.send(new PutCommand({
        TableName: this.tableName,
        Item: { pk, sk: idempotencySk(operationId), itemType: 'IDEMPOTENCY', data: result, expiresAtEpochSeconds: ttlSeconds(Date.now() + 7 * 86_400_000) },
        ConditionExpression: 'attribute_not_exists(pk) AND attribute_not_exists(sk)'
      }));
      return result;
    } catch (error) {
      if (isAwsError(error, 'ConditionalCheckFailedException')) return await this.get<SyncResult>(pk, idempotencySk(operationId)) || result;
      throw mapAwsError(error, 'DynamoDB conflict persistence');
    }
  }

  private async nextSequence(pk: string): Promise<number> {
    try {
      const response = await this.client.send(new UpdateCommand({
        TableName: this.tableName, Key: { pk, sk: 'SYNC_COUNTER' },
        UpdateExpression: 'SET itemType = if_not_exists(itemType, :type) ADD #sequence :one',
        ExpressionAttributeNames: { '#sequence': 'sequence' }, ExpressionAttributeValues: { ':one': 1, ':type': 'SYNC_COUNTER' }, ReturnValues: 'UPDATED_NEW'
      }));
      const sequence = Number(response.Attributes?.sequence);
      if (!Number.isSafeInteger(sequence)) throw new AppError(502, 'SYNC_SEQUENCE_INVALID', 'DynamoDB returned an invalid sync sequence', true);
      return sequence;
    } catch (error) { throw mapAwsError(error, 'DynamoDB sequence allocation'); }
  }

  private async get<T>(pk: string, sk: string): Promise<T | null> {
    try {
      const response = await this.client.send(new GetCommand({ TableName: this.tableName, Key: { pk, sk }, ConsistentRead: true }));
      return response.Item ? (response.Item as StoredItem<T>).data : null;
    } catch (error) { throw mapAwsError(error, 'DynamoDB read'); }
  }

  private userItem(user: UserAccount): StoredItem<UserAccount> {
    return { pk: userPk(user.userId), sk: 'PROFILE', itemType: 'USER', data: user, gsi1pk: companyPk(user.companyId), gsi1sk: `USER#${user.role}#${user.userId}` };
  }

  private phoneItem(user: UserAccount): StoredItem<{ userId: string }> {
    return { pk: phonePk(user.phone), sk: 'USER', itemType: 'PHONE_INDEX', data: { userId: user.userId } };
  }

  private companyItem<T>(companyId: string, sk: string, data: T): StoredItem<T> { return { pk: companyPk(companyId), sk, itemType: sk, data }; }

  private async queryCompanyUsers(companyId: string): Promise<UserAccount[]> {
    try {
      const users: UserAccount[] = [];
      let ExclusiveStartKey: Record<string, unknown> | undefined;
      do {
        const response = await this.client.send(new QueryCommand({
          TableName: this.tableName, IndexName: 'CompanyUsersIndex', KeyConditionExpression: 'gsi1pk = :pk AND begins_with(gsi1sk, :prefix)',
          ExpressionAttributeValues: { ':pk': companyPk(companyId), ':prefix': 'USER#' }, ExclusiveStartKey
        }));
        users.push(...(response.Items || []).map(item => (item as StoredItem<UserAccount>).data));
        ExclusiveStartKey = response.LastEvaluatedKey;
      } while (ExclusiveStartKey);
      return users;
    } catch (error) { throw mapAwsError(error, 'DynamoDB company user query'); }
  }

  /** Reads the sequence counter without mutating it (unlike `nextSequence`, which increments). */
  private async peekSequence(pk: string): Promise<number> {
    try {
      const response = await this.client.send(new GetCommand({ TableName: this.tableName, Key: { pk, sk: 'SYNC_COUNTER' }, ConsistentRead: true }));
      const sequence = Number(response.Item?.sequence);
      return Number.isSafeInteger(sequence) ? sequence : 0;
    } catch (error) { throw mapAwsError(error, 'DynamoDB sequence peek'); }
  }

  /** Single-page, resumable partition query — unlike `queryPartition`, which drains the whole partition in one call. */
  private async queryPartitionPage<T>(pk: string, prefix: string, limit: number, pageToken?: string): Promise<{ items: Array<StoredItem<T>>; nextPageToken: string | null }> {
    let exclusiveStartKey: Record<string, unknown> | undefined;
    if (pageToken) {
      try {
        exclusiveStartKey = JSON.parse(Buffer.from(pageToken, 'base64url').toString('utf8'));
      } catch {
        throw new AppError(400, 'SYNC_CURSOR_INVALID', 'Sync cursor is invalid');
      }
      // Don't trust a decoded ExclusiveStartKey blindly — reject one that doesn't belong to
      // this tenant's partition (the query itself is already scoped to `pk`, but there's no
      // reason to accept a bookmark object that round-tripped through the client unverified).
      if (!exclusiveStartKey || exclusiveStartKey.pk !== pk) throw new AppError(400, 'SYNC_CURSOR_INVALID', 'Sync cursor is invalid');
    }
    try {
      const response = await this.client.send(new QueryCommand({
        TableName: this.tableName,
        KeyConditionExpression: 'pk = :pk AND begins_with(sk, :prefix)',
        ExpressionAttributeValues: { ':pk': pk, ':prefix': prefix },
        Limit: limit,
        ExclusiveStartKey: exclusiveStartKey,
        ConsistentRead: true
      }));
      const items = (response.Items || []) as Array<StoredItem<T>>;
      const nextPageToken = response.LastEvaluatedKey
        ? Buffer.from(JSON.stringify(response.LastEvaluatedKey), 'utf8').toString('base64url')
        : null;
      return { items, nextPageToken };
    } catch (error) { throw mapAwsError(error, 'DynamoDB partition page query'); }
  }

  private async queryPartition<T>(pk: string, prefix?: string): Promise<Array<StoredItem<T>>> {
    try {
      const items: Array<StoredItem<T>> = [];
      let ExclusiveStartKey: Record<string, unknown> | undefined;
      do {
        const response = await this.client.send(new QueryCommand({
          TableName: this.tableName,
          KeyConditionExpression: prefix ? 'pk = :pk AND begins_with(sk, :prefix)' : 'pk = :pk',
          ExpressionAttributeValues: { ':pk': pk, ...(prefix ? { ':prefix': prefix } : {}) }, ExclusiveStartKey, ConsistentRead: true
        }));
        items.push(...(response.Items || []) as Array<StoredItem<T>>);
        ExclusiveStartKey = response.LastEvaluatedKey;
      } while (ExclusiveStartKey);
      return items;
    } catch (error) { throw mapAwsError(error, 'DynamoDB partition query'); }
  }

  private async scanAll(): Promise<Array<StoredItem<unknown>>> {
    try {
      const items: Array<StoredItem<unknown>> = [];
      let ExclusiveStartKey: Record<string, unknown> | undefined;
      do {
        // Only licenses and users are wanted; the filter keeps every tenant's sync records out
        // of the response (DynamoDB still reads them, so this stays an operator-only call).
        const response = await this.client.send(new ScanCommand({
          TableName: this.tableName, ExclusiveStartKey,
          FilterExpression: 'itemType IN (:license, :user)',
          ExpressionAttributeValues: { ':license': 'LICENSE', ':user': 'USER' }
        }));
        items.push(...(response.Items || []) as Array<StoredItem<unknown>>);
        ExclusiveStartKey = response.LastEvaluatedKey;
      } while (ExclusiveStartKey);
      return items;
    } catch (error) { throw mapAwsError(error, 'DynamoDB administration scan'); }
  }
}
