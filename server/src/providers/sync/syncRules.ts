import { CloudRecord, SyncActor, SyncOperation, SyncResult } from '../contracts';

/**
 * What the server does with one pushed operation, shared by the AWS and local stores so both
 * enforce exactly the same concurrency rules.
 *
 * - APPLY: write a new version.
 * - NOOP: the record already says what the client wants; acknowledge with the current version and
 *   write nothing, so a retried or re-sent operation never creates a phantom change.
 * - CONFLICT: the client wrote against a version that is no longer current. The client resolves it
 *   (see ConflictResolver on Android) and re-sends with a fresh operation id.
 *
 * INSERT used to skip the version check entirely, so an edit or a re-upload labelled INSERT
 * silently overwrote whatever another device had written since. It is now checked like any other
 * write; it only passes unchecked when there is nothing there yet.
 */
export type SyncDecision = 'APPLY' | 'NOOP' | 'CONFLICT';

export const decideSyncOperation = (existing: CloudRecord | undefined | null, operation: SyncOperation): SyncDecision => {
  const baseVersion = operation.baseVersion || 0;
  if (!existing) return baseVersion > 0 ? 'CONFLICT' : 'APPLY';
  if (operation.operation === 'DELETE' && existing.deleted) return 'NOOP';
  if (operation.operation === 'INSERT') {
    if (existing.deleted) return 'CONFLICT';
    if (samePayload(existing.payload, operation.payload)) return 'NOOP';
    return existing.version === baseVersion ? 'APPLY' : 'CONFLICT';
  }
  return existing.version === baseVersion ? 'APPLY' : 'CONFLICT';
};

/**
 * A stored result replayed for the same operation id. A conflict stays a conflict: reporting it as
 * DUPLICATE made the client mark a write that never landed as synced.
 */
export const replayStoredResult = (prior: SyncResult): SyncResult =>
  prior.status === 'CONFLICT' ? prior : { ...prior, status: 'DUPLICATE' };

export const supportedEntities = new Set(['Category', 'Product', 'Customer', 'Supplier', 'Expense', 'Sale', 'Purchase', 'CustomerCredit', 'SupplierCredit', 'StockMovement']);
const supportedOperations = new Set(['INSERT', 'UPDATE', 'PARTIAL_UPDATE', 'DELETE', 'UPSERT']);
const MAX_PAYLOAD_BYTES = 350_000;

/**
 * Why one operation can never be applied, or null when it can. These used to be thrown, which
 * failed the whole batch: every other operation in it (already applied, or perfectly valid) came
 * back as an error, and the client blamed and eventually dead-lettered all of them. A rejected
 * operation is now answered on its own and the rest of the batch goes through.
 */
export const rejectionReason = (operation: SyncOperation): { code: string; message: string } | null => {
  if (typeof operation.operationId !== 'string' || !/^[A-Za-z0-9\-_:.]{1,128}$/.test(operation.operationId)) {
    return { code: 'SYNC_OPERATION_ID_INVALID', message: 'Operation id is missing or invalid' };
  }
  if (!supportedEntities.has(operation.entityType)) return { code: 'SYNC_ENTITY_UNSUPPORTED', message: 'Unsupported sync entity type' };
  if (typeof operation.entityId !== 'string' || !operation.entityId.trim() || operation.entityId.length > 256) {
    return { code: 'SYNC_ENTITY_ID_INVALID', message: 'Entity id is missing or invalid' };
  }
  if (!supportedOperations.has(operation.operation)) return { code: 'SYNC_OPERATION_INVALID', message: 'Unsupported sync operation' };
  if (operation.baseVersion !== undefined && operation.baseVersion !== null && (!Number.isSafeInteger(operation.baseVersion) || operation.baseVersion < 0)) {
    return { code: 'SYNC_BASE_VERSION_INVALID', message: 'Base version must be a non-negative integer' };
  }
  if (operation.payload !== undefined && (operation.payload === null || typeof operation.payload !== 'object' || Array.isArray(operation.payload))) {
    return { code: 'SYNC_PAYLOAD_INVALID', message: 'Payload must be an object' };
  }
  if (Buffer.byteLength(JSON.stringify(operation.payload || {}), 'utf8') > MAX_PAYLOAD_BYTES) {
    return { code: 'SYNC_RECORD_TOO_LARGE', message: 'Sync record exceeds the safe item size' };
  }
  return null;
};

// Any one of these makes the user someone who writes shop data at all.
const WRITER_PERMISSIONS = ['SALE_CREATE', 'PURCHASE_CREATE', 'CATEGORY_CREATE', 'CATEGORY_EDIT', 'PRODUCT_CREATE', 'PRODUCT_EDIT'];

// What it takes to change or delete a record that is live in the cloud. 'ADMIN' means the role
// itself, matching the app: only an administrator can edit or cancel a finished bill or purchase.
// Ledger rows (stock movements, credits) are absent on purpose: every device deletes the
// children of a cancelled bill as part of sync, whoever is signed in there.
const LIVE_EDIT_REQUIREMENT: Record<string, string> = {
  Category: 'CATEGORY_EDIT', Product: 'PRODUCT_EDIT', Customer: 'SALE_CREATE',
  Supplier: 'PURCHASE_CREATE', Expense: 'PURCHASE_CREATE', Sale: 'ADMIN', Purchase: 'ADMIN'
};

/**
 * Why this user may not make this write, or null when they may. Only a write that is actually
 * going to land is checked (after the version decision), and the line is drawn between creating
 * and changing:
 *
 * - Creating a record, or bringing back a deleted one, needs only some writer permission. Sync
 *   does this on its own from any device: it restores a product another device deleted while a
 *   bill here still uses it, and makes a placeholder for one that never arrived. Refusing those
 *   for a cashier would loop forever (the pull deletes it, sync restores it, the server refuses).
 * - Changing or deleting a live record needs the edit permission for that kind of record, which
 *   is the same rule the app applies before it lets anyone make the edit locally.
 */
export const permissionDenial = (actor: SyncActor, operation: SyncOperation, existing: CloudRecord | undefined | null): { code: string; message: string } | null => {
  if (actor.role === 'ADMIN' || actor.role === 'SUPER_ADMIN') return null;
  if (!WRITER_PERMISSIONS.some(permission => actor.permissions.includes(permission))) {
    return { code: 'SYNC_PERMISSION_DENIED', message: 'This account is not allowed to change shop data' };
  }
  if (!existing || existing.deleted) return null;
  const needed = LIVE_EDIT_REQUIREMENT[operation.entityType];
  if (!needed) return null;
  if (needed === 'ADMIN' || !actor.permissions.includes(needed)) {
    return { code: 'SYNC_PERMISSION_DENIED', message: `This account is not allowed to change or delete a ${operation.entityType}` };
  }
  return null;
};

export const rejectedResult = (operation: SyncOperation, reason: { code: string; message: string }): SyncResult => ({
  operationId: typeof operation.operationId === 'string' ? operation.operationId : '',
  status: 'REJECTED',
  error: reason
});

/**
 * How long a hole in the change log may be treated as a write still in flight. Sequence numbers
 * are handed out before the write commits, so two concurrent pushes can commit out of order: 11
 * becomes visible while 10 is still being written. A tail pull that stepped over 10 then would
 * never see it. A hole younger than this holds the cursor back; an older one is a sequence whose
 * write failed (a lost race) and is skipped.
 */
export const CHANGE_GAP_SETTLE_MS = 30_000;

/**
 * A fresh (snapshot) pull hands over to the change log a little before the sequence it captured,
 * for the same reason: a write that took its number before the capture may commit after the scan
 * has already passed its row. Replaying a few changes twice is harmless; the client applies them
 * again in order.
 */
export const SNAPSHOT_TAIL_OVERLAP = 100;

// Fields the client stamps on every write that say nothing about the record's content.
// editedAtEpochMs is when the change was made (used by the client to order conflicting edits);
// re-sending the same content later must still count as the same record.
const volatileKeys = new Set(['companyId', 'syncStatus', 'editedAtEpochMs']);

const samePayload = (stored: Record<string, unknown>, incoming: Record<string, unknown> | undefined): boolean =>
  stableStringify(stripVolatile(stored)) === stableStringify(stripVolatile(incoming || {}));

const stripVolatile = (value: Record<string, unknown>) =>
  Object.fromEntries(Object.entries(value).filter(([key]) => !volatileKeys.has(key)));

const stableStringify = (value: unknown): string => {
  if (Array.isArray(value)) return `[${value.map(stableStringify).join(',')}]`;
  if (value && typeof value === 'object') {
    const entries = Object.keys(value as object).sort()
      .map(key => `${JSON.stringify(key)}:${stableStringify((value as Record<string, unknown>)[key])}`);
    return `{${entries.join(',')}}`;
  }
  return JSON.stringify(value === undefined ? null : value);
};
