import test from 'node:test';
import assert from 'node:assert/strict';
import { promises as fs } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { AtomicJsonStore } from '../providers/local/atomicJsonStore';
import { LocalDataStore } from '../providers/local/localDataStore';
import { CloudRecord, SyncOperation } from '../providers/contracts';
import { decideSyncOperation, documentAmountsReason, permissionDenial, replayStoredResult } from '../providers/sync/syncRules';

const record = (overrides: Partial<CloudRecord> = {}): CloudRecord => ({
  companyId: 'c', entityType: 'Product', entityId: 'p', version: 3, schemaVersion: 1,
  updatedAtEpochMs: 1, deleted: false, payload: { name: 'Tea', salePriceMinorUnits: 1000 }, ...overrides
});
const op = (overrides: Partial<SyncOperation> = {}): SyncOperation => ({
  operationId: 'o', companyId: 'c', entityType: 'Product', entityId: 'p', operation: 'UPDATE',
  baseVersion: 3, schemaVersion: 1, payload: { name: 'Tea', salePriceMinorUnits: 1200 }, ...overrides
});

test('an INSERT over an existing record is version-checked instead of silently overwriting it', () => {
  assert.equal(decideSyncOperation(record(), op({ operation: 'INSERT', baseVersion: 0 })), 'CONFLICT');
  assert.equal(decideSyncOperation(record(), op({ operation: 'INSERT', baseVersion: 2 })), 'CONFLICT');
  // Older clients send a bill edit as INSERT with the correct base; that still goes through.
  assert.equal(decideSyncOperation(record(), op({ operation: 'INSERT', baseVersion: 3 })), 'APPLY');
});

test('re-sending exactly what the server already has is a no-op, not a conflict or a new version', () => {
  assert.equal(decideSyncOperation(record(), op({ operation: 'INSERT', baseVersion: 0, payload: { name: 'Tea', salePriceMinorUnits: 1000, companyId: 'c', syncStatus: 'SYNCED' } })), 'NOOP');
});

test('the edit timestamp alone does not make a re-send a different record', () => {
  assert.equal(decideSyncOperation(record(), op({ operation: 'INSERT', baseVersion: 0, payload: { name: 'Tea', salePriceMinorUnits: 1000, editedAtEpochMs: 99 } })), 'NOOP');
});

test('an INSERT onto a deleted record conflicts, so a stale re-upload cannot resurrect it', () => {
  assert.equal(decideSyncOperation(record({ deleted: true }), op({ operation: 'INSERT', baseVersion: 0, payload: { name: 'Tea', salePriceMinorUnits: 1000 } })), 'CONFLICT');
});

test('deleting something already deleted is a no-op whatever the base version', () => {
  assert.equal(decideSyncOperation(record({ deleted: true }), op({ operation: 'DELETE', baseVersion: 1 })), 'NOOP');
});

test('updates and deletes still need the current version', () => {
  assert.equal(decideSyncOperation(record(), op({ baseVersion: 2 })), 'CONFLICT');
  assert.equal(decideSyncOperation(record(), op({ operation: 'DELETE', baseVersion: 2 })), 'CONFLICT');
  assert.equal(decideSyncOperation(record(), op()), 'APPLY');
  // A resolver restoring a deleted record writes against the tombstone's version.
  assert.equal(decideSyncOperation(record({ deleted: true }), op()), 'APPLY');
});

test('a write against a version that no longer exists conflicts', () => {
  assert.equal(decideSyncOperation(null, op({ baseVersion: 2 })), 'CONFLICT');
  assert.equal(decideSyncOperation(null, op({ operation: 'INSERT', baseVersion: 0 })), 'APPLY');
});

test('a replayed conflict stays a conflict instead of reading as synced', () => {
  assert.equal(replayStoredResult({ operationId: 'o', status: 'CONFLICT' }).status, 'CONFLICT');
  assert.equal(replayStoredResult({ operationId: 'o', status: 'APPLIED', version: 2 }).status, 'DUPLICATE');
});

test('local store: stale INSERT conflicts, identical INSERT is acknowledged without a new version, replayed conflict stays a conflict', async t => {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'kadaikutty-sync-rules-'));
  t.after(() => fs.rm(directory, { recursive: true, force: true }));
  const store = new LocalDataStore(new AtomicJsonStore(path.join(directory, 'state.json')));
  const companyId = 'company-a';
  const base = { companyId, entityType: 'Sale', entityId: 'sale-1', schemaVersion: 1 };

  const created = await store.applySyncBatch(companyId, [{ ...base, operationId: 'op-1', operation: 'INSERT', payload: { revision: 0, totalMinorUnits: 100 } }]);
  assert.equal(created[0].status, 'APPLIED');
  const edited = await store.applySyncBatch(companyId, [{ ...base, operationId: 'op-2', operation: 'UPDATE', baseVersion: 1, payload: { revision: 1, totalMinorUnits: 150 } }]);
  assert.equal(edited[0].version, 2);

  const staleEdit = await store.applySyncBatch(companyId, [{ ...base, operationId: 'op-3', operation: 'INSERT', baseVersion: 1, payload: { revision: 1, totalMinorUnits: 175 } }]);
  assert.equal(staleEdit[0].status, 'CONFLICT');
  assert.equal(staleEdit[0].record?.payload.totalMinorUnits, 150);
  assert.equal((await store.applySyncBatch(companyId, [{ ...base, operationId: 'op-3', operation: 'INSERT', baseVersion: 1, payload: { revision: 1, totalMinorUnits: 175 } }]))[0].status, 'CONFLICT');

  const reUpload = await store.applySyncBatch(companyId, [{ ...base, operationId: 'op-4', operation: 'INSERT', payload: { revision: 1, totalMinorUnits: 150 } }]);
  assert.equal(reUpload[0].status, 'APPLIED');
  assert.equal(reUpload[0].version, 2);
  const page = await store.pullSync(companyId, '2', 10);
  assert.equal(page.records.length, 0, 'a no-op must not emit a change');
});

const cashier = { role: 'CASHIER', permissions: ['SALE_CREATE', 'SALE_VIEW', 'PRODUCT_VIEW'] };

test('a cashier cannot change or delete a live product through the sync API', () => {
  assert.equal(permissionDenial(cashier, op(), record())?.code, 'SYNC_PERMISSION_DENIED');
  assert.equal(permissionDenial(cashier, op({ operation: 'DELETE' }), record())?.code, 'SYNC_PERMISSION_DENIED');
  assert.equal(permissionDenial({ ...cashier, permissions: [...cashier.permissions, 'PRODUCT_EDIT'] }, op(), record()), null);
  assert.equal(permissionDenial({ role: 'ADMIN', permissions: [] }, op(), record()), null);
});

test('sync may still create or restore records from a cashier device', () => {
  // Restoring a product another device deleted while a bill here still uses it.
  assert.equal(permissionDenial(cashier, op(), record({ deleted: true })), null);
  // A placeholder for a product that never arrived.
  assert.equal(permissionDenial(cashier, op({ operation: 'INSERT', baseVersion: 0 }), undefined), null);
  // Cleaning up the stock rows of a bill cancelled elsewhere.
  assert.equal(permissionDenial(cashier, op({ entityType: 'StockMovement', operation: 'DELETE' }), record({ entityType: 'StockMovement' })), null);
});

test('only an administrator can change a finished bill, and a view-only account writes nothing', () => {
  assert.equal(permissionDenial(cashier, op({ entityType: 'Sale' }), record({ entityType: 'Sale' }))?.code, 'SYNC_PERMISSION_DENIED');
  assert.equal(permissionDenial(cashier, op({ entityType: 'Sale', operation: 'INSERT', baseVersion: 0 }), undefined), null);
  assert.equal(permissionDenial({ role: 'CASHIER', permissions: ['SALE_VIEW', 'REPORT_SALES'] }, op({ operation: 'INSERT', baseVersion: 0 }), undefined)?.code, 'SYNC_PERMISSION_DENIED');
});

test('a denied write is answered on its own, stores nothing and does not block the rest of the batch', async () => {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'sync-perm-'));
  const store = new LocalDataStore(new AtomicJsonStore(path.join(dir, 'state.json')));
  const companyId = 'c';
  const insert: SyncOperation = { operationId: 'p-1', companyId, entityType: 'Product', entityId: 'p', operation: 'INSERT', baseVersion: 0, schemaVersion: 1, payload: { name: 'Tea', salePriceMinorUnits: 1000 } };
  assert.equal((await store.applySyncBatch(companyId, [insert], { role: 'ADMIN', permissions: [] }))[0].status, 'APPLIED');
  const [priceCut, sale] = await store.applySyncBatch(companyId, [
    { ...insert, operationId: 'p-2', operation: 'UPDATE', baseVersion: 1, payload: { name: 'Tea', salePriceMinorUnits: 1 } },
    { ...insert, operationId: 's-1', entityType: 'Sale', entityId: 's', payload: { totalMinorUnits: 1000 } }
  ], cashier);
  assert.equal(priceCut.status, 'REJECTED');
  assert.equal(sale.status, 'APPLIED');
  const page = await store.pullSync(companyId, '', 50);
  assert.equal(page.records.find(r => r.entityType === 'Product')?.payload.salePriceMinorUnits, 1000);
});

test('bill and purchase amounts: honest documents pass, malformed ones are refused', () => {
  const sale = (payload: Record<string, unknown>) => ({ operationId: 'op-1', entityType: 'Sale', entityId: 's1', operation: 'INSERT', payload } as any);
  const line = { quantity: 1500, unitPriceMinorUnits: 5200, lineTotalMinorUnits: 7800, unitType: 'KG' };
  // 1.5 kg at Rs 52/kg = Rs 78, less a Rs 3 bill discount.
  assert.equal(documentAmountsReason(sale({ items: [line], totalMinorUnits: 7500, discountMinorUnits: 300 })), null);
  // Whole-unit line, floor rounding from an older build still passes.
  assert.equal(documentAmountsReason(sale({ items: [{ quantity: 3, unitPriceMinorUnits: 3333, lineTotalMinorUnits: 9999, unitType: 'PIECE' }], totalMinorUnits: 9999 })), null);
  assert.notEqual(documentAmountsReason(sale({ items: [line], totalMinorUnits: -1 })), null);
  assert.notEqual(documentAmountsReason(sale({ items: [line], totalMinorUnits: 9000 })), null);
  assert.notEqual(documentAmountsReason(sale({ items: [{ ...line, lineTotalMinorUnits: 7_800_000 }], totalMinorUnits: 7800 })), null);
  assert.notEqual(documentAmountsReason(sale({ items: [{ ...line, quantity: 0 }], totalMinorUnits: 0 })), null);
  // Deletes and documents without lines are left to the other rules.
  assert.equal(documentAmountsReason({ ...sale({}), operation: 'DELETE' }), null);
  const purchase = { operationId: 'op-2', entityType: 'Purchase', entityId: 'p1', operation: 'INSERT', payload: { items: [{ quantity: 25000, unitValueMinorUnits: 4000, lineTotalMinorUnits: 100000, unitType: 'KG' }], totalMinorUnits: 100000 } } as any;
  assert.equal(documentAmountsReason(purchase), null);
});
