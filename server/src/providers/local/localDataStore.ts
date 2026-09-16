import crypto, { randomUUID } from 'node:crypto';
import { AppError } from '../../core/errors';
import { AuditEntry, CloudRecord, DataStore, LicenseRecord, NewAccountInput, StaffInput, SyncOperation, SyncPage, SyncResult, UserAccount } from '../contracts';
import { newTrialLicense } from '../../core/license';
import { AtomicJsonStore } from './atomicJsonStore';

const recordKey = (companyId: string, entityType: string, entityId: string) => `${companyId}\u001f${entityType}\u001f${entityId}`;
const operationKey = (companyId: string, operationId: string) => `${companyId}\u001f${operationId}`;
const supportedEntities = new Set(['Category', 'Product', 'Customer', 'Supplier', 'Expense', 'Sale', 'Purchase', 'CustomerCredit', 'SupplierCredit', 'StockMovement']);

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
      state.users[user.userId] = user;
      state.phoneIndex[input.phone] = user.userId;
      state.licenses[companyId] = license;
      return { user, license };
    });
  }

  findUserByPhone(phone: string): Promise<UserAccount | null> {
    return this.store.read(state => state.users[state.phoneIndex[phone]] || null);
  }

  findUserById(userId: string): Promise<UserAccount | null> {
    return this.store.read(state => state.users[userId] || null);
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

  applySyncBatch(companyId: string, operations: SyncOperation[]): Promise<SyncResult[]> {
    return this.store.write(state => operations.map(operation => {
      const idempotencyKey = operationKey(companyId, operation.operationId);
      const prior = state.idempotency[idempotencyKey];
      if (prior) return { ...prior.result, status: 'DUPLICATE' };
      if (operation.companyId !== companyId) throw new AppError(403, 'TENANT_MISMATCH', 'Operation tenant does not match authenticated tenant');
      if (!supportedEntities.has(operation.entityType)) throw new AppError(422, 'SYNC_ENTITY_UNSUPPORTED', 'Unsupported sync entity type');
      const key = recordKey(companyId, operation.entityType, operation.entityId);
      const existing = state.records[key];
      const baseVersion = operation.baseVersion || 0;
      if (existing && existing.version !== baseVersion && operation.operation !== 'INSERT') {
        const result: SyncResult = { operationId: operation.operationId, status: 'CONFLICT', record: existing };
        state.idempotency[idempotencyKey] = { result, createdAtEpochMs: Date.now() };
        return result;
      }
      if (!existing && baseVersion > 0) {
        const result: SyncResult = { operationId: operation.operationId, status: 'CONFLICT' };
        state.idempotency[idempotencyKey] = { result, createdAtEpochMs: Date.now() };
        return result;
      }
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
    const parsedCursor = cursor ? Number(cursor) : 0;
    if (!Number.isSafeInteger(parsedCursor) || parsedCursor < 0) throw new AppError(400, 'SYNC_CURSOR_INVALID', 'Sync cursor is invalid');
    return this.store.read(state => {
      const matching = state.changes.filter(change => change.sequence > parsedCursor && change.record.companyId === companyId);
      const page = matching.slice(0, limit);
      return {
        records: page.map(change => change.record),
        nextCursor: String(page.length ? page[page.length - 1].sequence : parsedCursor),
        hasMore: matching.length > page.length
      };
    });
  }

  purgeCompanyRecords(companyId: string): Promise<number> {
    return this.store.write(state => {
      let count = 0;
      const now = Date.now();
      Object.entries(state.records).forEach(([key, existing]) => {
        if (existing.companyId !== companyId || existing.deleted) return;
        const tombstone: CloudRecord = { ...existing, version: existing.version + 1, updatedAtEpochMs: now, deleted: true, payload: { companyId } };
        state.records[key] = tombstone;
        state.sequence += 1;
        state.changes.push({ sequence: state.sequence, record: tombstone });
        count += 1;
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
      return updated;
    });
  }

  deleteCompany(companyId: string): Promise<void> {
    return this.store.write(state => {
      Object.values(state.users).filter(user => user.companyId === companyId).forEach(user => {
        delete state.phoneIndex[user.phone]; delete state.credentials[user.userId]; delete state.users[user.userId];
      });
      delete state.licenses[companyId];
      Object.entries(state.records).forEach(([key, record]) => { if (record.companyId === companyId) delete state.records[key]; });
      Object.entries(state.sessions).forEach(([key, session]) => { if (session.companyId === companyId) delete state.sessions[key]; });
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
