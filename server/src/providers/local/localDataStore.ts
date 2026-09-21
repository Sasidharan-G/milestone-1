import crypto, { randomUUID } from 'node:crypto';
import { AppError } from '../../core/errors';
import { AuditEntry, CloudRecord, DataStore, LicenseRecord, NewAccountInput, ShopProfileRecord, StaffInput, SyncActor, SyncOperation, SyncPage, SyncResult, UserAccount } from '../contracts';
import { newTrialLicense } from '../../core/license';
import { AtomicJsonStore } from './atomicJsonStore';
import { encodeSnapshotCursor, encodeTailCursor, parseCursor } from '../sync/syncCursor';
import { decideSyncOperation, permissionDenial, rejectedResult, rejectionReason, replayStoredResult } from '../sync/syncRules';

const recordKey = (companyId: string, entityType: string, entityId: string) => `${companyId}\u001f${entityType}\u001f${entityId}`;
const operationKey = (companyId: string, operationId: string) => `${companyId}\u001f${operationId}`;

export const activePermissions = ['USER_MANAGE', 'CATEGORY_VIEW', 'CATEGORY_CREATE', 'CATEGORY_EDIT', 'PRODUCT_VIEW', 'PRODUCT_CREATE', 'PRODUCT_EDIT', 'SALE_CREATE', 'SALE_VIEW', 'PURCHASE_CREATE', 'PURCHASE_VIEW', 'REPORT_SALES', 'REPORT_STOCK', 'REPORT_PROFIT', 'BACKUP_CREATE', 'SETTINGS_VIEW', 'SETTINGS_EDIT'];

export class LocalDataStore implements DataStore {
  constructor(private readonly store: AtomicJsonStore) {}

  async createAccount(input: NewAccountInput): Promise<{ user: UserAccount; license: LicenseRecord }> {
    return this.store.write(state => {
      if (state.phoneIndex[input.phone]) throw new AppError(409, 'ACCOUNT_PHONE_EXISTS', 'Mobile number is already registered');
      const now = Date.now();
      const companyId = randomUUID();
      const user: UserAccount = {
        userId: randomUUID(), companyId, phone: input.phone, displayName: input.displayName,
        businessName: input.businessName, role: 'ADMIN', permissions: [...activePermissions], status: 'ACTIVE',
        createdAtEpochMs: now, updatedAtEpochMs: now
      };
      const license: LicenseRecord = newTrialLicense(companyId, input.phone, input.businessName, input.displayName, now);
      const shopProfile: ShopProfileRecord = { companyId, shopName: input.businessName, ownerName: input.displayName, gstNumber: '', address: '', phone: input.phone, email: '', version: 1, updatedAtEpochMs: now, updatedByUserId: user.userId };
      state.users[user.userId] = user;
      state.phoneIndex[input.phone] = user.userId;
      state.licenses[companyId] = license;
      state.shopProfiles[companyId] = shopProfile;
      return { user, license };
    });
  }

  findUserByPhone(phone: string): Promise<UserAccount | null> {
    return this.store.read(state => state.users[state.phoneIndex[phone]] || null);
  }

  findUserById(userId: string): Promise<UserAccount | null> {
    return this.store.read(state => state.users[userId] || null);
  }

  listCompanyUsers(companyId: string): Promise<UserAccount[]> {
    return this.store.read(state => Object.values(state.users).filter(user => user.companyId === companyId));
  }

  listStaff(companyId: string): Promise<UserAccount[]> {
    return this.store.read(state => Object.values(state.users).filter(user => user.companyId === companyId && user.role === 'CASHIER'));
  }

  createStaff(input: StaffInput): Promise<UserAccount> {
    return this.store.write(state => {
      if (state.phoneIndex[input.phone]) throw new AppError(409, 'ACCOUNT_PHONE_EXISTS', 'Mobile number is already registered');
      const now = Date.now();
      const user: UserAccount = {
        userId: randomUUID(), companyId: input.companyId, phone: input.phone, displayName: input.displayName,
        role: 'CASHIER', permissions: input.permissions.filter(permission => activePermissions.includes(permission) && permission !== 'USER_MANAGE'),
        status: 'ACTIVE', createdAtEpochMs: now, updatedAtEpochMs: now
      };
      state.users[user.userId] = user;
      state.phoneIndex[input.phone] = user.userId;
      return user;
    });
  }

  deleteStaff(companyId: string, userId: string): Promise<void> {
    return this.store.write(state => {
      const user = state.users[userId];
      if (!user || user.companyId !== companyId || user.role !== 'CASHIER') return;
      delete state.phoneIndex[user.phone];
      delete state.credentials[userId];
      delete state.users[userId];
    });
  }

  updateStaff(companyId: string, userId: string, changes: Partial<Pick<UserAccount, 'displayName' | 'permissions' | 'status'>>): Promise<UserAccount> {
    return this.store.write(state => {
      const current = state.users[userId];
      if (!current || current.companyId !== companyId || current.role !== 'CASHIER') throw new AppError(404, 'STAFF_NOT_FOUND', 'Staff account was not found');
      const updated: UserAccount = {
        ...current,
        ...(changes.displayName === undefined ? {} : { displayName: changes.displayName }),
        ...(changes.status === undefined ? {} : { status: changes.status }),
        ...(changes.permissions === undefined ? {} : { permissions: changes.permissions.filter(permission => activePermissions.includes(permission) && permission !== 'USER_MANAGE') }),
        updatedAtEpochMs: Date.now()
      };
      state.users[userId] = updated;
      return updated;
    });
  }

  getLicense(companyId: string): Promise<LicenseRecord | null> {
    return this.store.read(state => state.licenses[companyId] || null);
  }

  getShopProfile(companyId: string): Promise<ShopProfileRecord | null> {
    return this.store.read(state => state.shopProfiles[companyId] || null);
  }

  updateShopProfile(companyId: string, changes: Partial<Omit<ShopProfileRecord, 'companyId' | 'version' | 'updatedAtEpochMs'>>): Promise<ShopProfileRecord> {
    return this.store.write(state => {
      const license = state.licenses[companyId];
      if (!license) throw new AppError(404, 'LICENSE_NOT_FOUND', 'Company license was not found');
      const current = state.shopProfiles[companyId] || {
        companyId, shopName: license.businessName || '', ownerName: license.ownerName || '', gstNumber: '', address: '', phone: license.ownerMobile || '', email: '', version: 0, updatedAtEpochMs: 0
      };
      const now = Date.now();
      const updated: ShopProfileRecord = { ...current, ...changes, companyId, version: current.version + 1, updatedAtEpochMs: now };
      state.shopProfiles[companyId] = updated;
      state.licenses[companyId] = { ...license, businessName: updated.shopName, ownerName: updated.ownerName, updatedAtEpochMs: now };
      Object.values(state.users).forEach(user => {
        if (user.companyId !== companyId) return;
        user.businessName = updated.shopName;
        if (user.role === 'ADMIN') user.displayName = updated.ownerName;
        user.updatedAtEpochMs = now;
      });
      return updated;
    });
  }

  consumeNonce(scope: string, nonce: string, expiresAtEpochMs: number): Promise<boolean> {
    return this.store.write(state => {
      const now = Date.now();
      Object.entries(state.consumedNonces).forEach(([key, expiry]) => { if (expiry <= now) delete state.consumedNonces[key]; });
      const key = `${scope}\u001f${nonce}`;
      if (state.consumedNonces[key]) return false;
      state.consumedNonces[key] = expiresAtEpochMs;
      return true;
    });
  }

  applySyncBatch(companyId: string, operations: SyncOperation[], actor?: SyncActor): Promise<SyncResult[]> {
    return this.store.write(state => operations.map(operation => {
      const idempotencyKey = operationKey(companyId, operation.operationId);
      const prior = state.idempotency[idempotencyKey];
      if (prior) return replayStoredResult(prior.result);
      if (operation.companyId !== companyId) throw new AppError(403, 'TENANT_MISMATCH', 'Operation tenant does not match authenticated tenant');
      const rejection = rejectionReason(operation);
      if (rejection) return rejectedResult(operation, rejection);
      const key = recordKey(companyId, operation.entityType, operation.entityId);
      const existing = state.records[key];
      const decision = decideSyncOperation(existing, operation);
      if (decision === 'CONFLICT') {
        const result: SyncResult = { operationId: operation.operationId, status: 'CONFLICT', ...(existing ? { record: existing } : {}) };
        state.idempotency[idempotencyKey] = { result, createdAtEpochMs: Date.now() };
        return result;
      }
      if (decision === 'NOOP') {
        const result: SyncResult = { operationId: operation.operationId, status: 'APPLIED', version: existing!.version };
        state.idempotency[idempotencyKey] = { result, createdAtEpochMs: Date.now() };
        return result;
      }
      const denial = actor ? permissionDenial(actor, operation, existing) : null;
      if (denial) return rejectedResult(operation, denial);
      const now = Date.now();
      const payload = operation.operation === 'PARTIAL_UPDATE'
        ? { ...(existing?.payload || {}), ...(operation.payload || {}) }
        : { ...(operation.payload || {}) };
      payload.companyId = companyId;
      const record: CloudRecord = {
        companyId, entityType: operation.entityType, entityId: operation.entityId,
        version: (existing?.version || 0) + 1, schemaVersion: operation.schemaVersion,
        updatedAtEpochMs: now, deleted: operation.operation === 'DELETE', payload
      };
      state.records[key] = record;
      state.sequence += 1;
      state.changes.push({ sequence: state.sequence, record });
      const result: SyncResult = { operationId: operation.operationId, status: 'APPLIED', version: record.version };
      state.idempotency[idempotencyKey] = { result, createdAtEpochMs: now };
      return result;
    }));
  }

  pullSync(companyId: string, cursor: string, limit: number): Promise<SyncPage> {
    const parsed = parseCursor(cursor);
    return this.store.read(state => {
      if (parsed.mode === 'tail') {
        const matching = state.changes.filter(change => change.sequence > parsed.afterSequence && change.record.companyId === companyId);
        const page = matching.slice(0, limit);
        return {
          records: page.map(change => change.record),
          nextCursor: encodeTailCursor(page.length ? page[page.length - 1].sequence : parsed.afterSequence),
          hasMore: matching.length > page.length
        };
      }
      // Fresh pull: reconstruct from the durable records map instead of the change log
      // (structurally the same gap as the AWS provider's TTL-pruned CHANGE# rows, even
      // though this in-memory store never actually expires anything).
      const capturedMaxSequence = parsed.mode === 'snapshotStart' ? state.sequence : parsed.capturedMaxSequence;
      let offset = 0;
      if (parsed.mode === 'snapshotResume') {
        offset = Number(parsed.pageToken);
        if (!Number.isSafeInteger(offset) || offset < 0) throw new AppError(400, 'SYNC_CURSOR_INVALID', 'Sync cursor is invalid');
      }
      const allKeysForCompany = Object.keys(state.records).filter(key => state.records[key].companyId === companyId).sort();
      const pageKeys = allKeysForCompany.slice(offset, offset + limit);
      const records = pageKeys.map(key => state.records[key]);
      const nextOffset = offset + pageKeys.length;
      if (nextOffset < allKeysForCompany.length) {
        return { records, nextCursor: encodeSnapshotCursor(capturedMaxSequence, String(nextOffset)), hasMore: true };
      }
      return { records, nextCursor: encodeTailCursor(capturedMaxSequence), hasMore: false };
    });
  }

  getSyncEpoch(companyId: string): Promise<number> {
    return this.store.read(state => state.syncEpochs?.[companyId] || 0);
  }

  bumpSyncEpoch(companyId: string): Promise<number> {
    return this.store.write(state => {
      state.syncEpochs = state.syncEpochs || {};
      state.syncEpochs[companyId] = (state.syncEpochs[companyId] || 0) + 1;
      return state.syncEpochs[companyId];
    });
  }

  purgeCompanyRecords(companyId: string): Promise<number> {
    return this.store.write(state => {
      let count = 0;
      Object.entries(state.records).forEach(([key, existing]) => {
        if (existing.companyId !== companyId) return;
        delete state.records[key];
        count += 1;
      });
      state.changes = state.changes.filter(change => change.record.companyId !== companyId);
      // operationKey joins with \u001f, not ':' - the old colon prefix never matched, so this
      // cleanup silently never ran and stale idempotency rows outlived every purge.
      const idempotencyPrefix = operationKey(companyId, '');
      Object.keys(state.idempotency).forEach(k => {
        if (k.startsWith(idempotencyPrefix)) delete state.idempotency[k];
      });
      return count;
    });
  }

  adminOverview() {
    return this.store.read(state => ({ licenses: Object.values(state.licenses), users: Object.values(state.users), masterConfig: state.masterConfig }));
  }

  updateLicense(companyId: string, changes: Partial<LicenseRecord>): Promise<LicenseRecord> {
    return this.store.write(state => {
      const license = state.licenses[companyId];
      if (!license) throw new AppError(404, 'LICENSE_NOT_FOUND', 'Company license was not found');
      const updated = { ...license, ...changes, companyId, updatedAtEpochMs: Date.now() };
      state.licenses[companyId] = updated;
      Object.values(state.users).forEach(user => {
        if (user.companyId === companyId) {
          if (changes.businessName) user.businessName = changes.businessName;
          if (changes.ownerName && user.role === 'ADMIN') user.displayName = changes.ownerName;
          user.updatedAtEpochMs = Date.now();
        }
      });
      return updated;
    });
  }

  deleteCompany(companyId: string): Promise<void> {
    return this.store.write(state => {
      const removedUserIds = new Set<string>();
      Object.values(state.users).filter(user => user.companyId === companyId).forEach(user => {
        removedUserIds.add(user.userId);
        delete state.phoneIndex[user.phone]; delete state.credentials[user.userId]; delete state.users[user.userId];
      });
      delete state.licenses[companyId];
      delete state.shopProfiles[companyId];
      Object.entries(state.records).forEach(([key, record]) => { if (record.companyId === companyId) delete state.records[key]; });
      Object.entries(state.sessions).forEach(([key, session]) => { if (session.companyId === companyId) delete state.sessions[key]; });
      // AWS deletes the company's whole partition, so local has to clear the same things or the
      // two providers disagree about what a deleted company leaves behind.
      state.changes = state.changes.filter(change => change.record.companyId !== companyId);
      const idempotencyPrefix = operationKey(companyId, '');
      Object.keys(state.idempotency).forEach(key => { if (key.startsWith(idempotencyPrefix)) delete state.idempotency[key]; });
      Object.entries(state.backups).forEach(([key, backup]) => { if (backup.companyId === companyId) delete state.backups[key]; });
      Object.entries(state.refreshTokens).forEach(([key, token]) => { if (removedUserIds.has(token.userId)) delete state.refreshTokens[key]; });
      state.audits = state.audits.filter(entry => entry.companyId !== companyId);
    });
  }

  getMasterConfig() { return this.store.read(state => state.masterConfig); }

  /** The PIN is stored as a PBKDF2 hash; `verifyMasterPin` checks it during master login. */
  updateMasterConfig(changes: { mobile: string; pin: string }) {
    return this.store.write(state => state.masterConfig = { mobile: changes.mobile, pin: hashMasterPin(changes.pin), updatedAtEpochMs: Date.now() });
  }

  appendAudit(entry: Omit<AuditEntry, 'auditId' | 'createdAtEpochMs'>): Promise<AuditEntry> {
    return this.store.write(state => {
      const stored: AuditEntry = { ...entry, auditId: randomUUID(), createdAtEpochMs: Date.now() };
      state.audits.push(stored);
      if (state.audits.length > 50_000) state.audits.splice(0, state.audits.length - 50_000);
      return stored;
    });
  }

  listAudit(companyId: string, limit: number): Promise<AuditEntry[]> {
    return this.store.read(state => state.audits.filter(entry => entry.companyId === companyId).slice(-limit).reverse());
  }
}

const pinIterations = 120_000;

export const hashMasterPin = (pin: string): string => {
  const salt = crypto.randomBytes(16);
  const hash = crypto.pbkdf2Sync(pin, salt, pinIterations, 32, 'sha256');
  return ['pbkdf2', salt.toString('base64'), hash.toString('base64')].join('$');
};

/** Accepts a hashed PIN or, for env-seeded development state, the plaintext MASTER_ADMIN_PIN. */
export const verifyMasterPin = (stored: string, supplied: string): boolean => {
  if (!stored) return false;
  if (stored.startsWith('pbkdf2$')) {
    const [, saltBase64, hashBase64] = stored.split('$');
    const expected = Buffer.from(hashBase64, 'base64');
    const actual = crypto.pbkdf2Sync(supplied, Buffer.from(saltBase64, 'base64'), pinIterations, 32, 'sha256');
    return expected.length === actual.length && crypto.timingSafeEqual(expected, actual);
  }
  const a = Buffer.from(stored);
  const b = Buffer.from(supplied);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
};
