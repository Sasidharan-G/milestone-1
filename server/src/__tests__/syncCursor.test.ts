import test from 'node:test';
import assert from 'node:assert/strict';
import { encodeSnapshotCursor, encodeTailCursor, parseCursor } from '../providers/sync/syncCursor';

test('parseCursor treats empty string and "0" as a fresh snapshot start', () => {
  assert.deepEqual(parseCursor(''), { mode: 'snapshotStart' });
  assert.deepEqual(parseCursor('0'), { mode: 'snapshotStart' });
});

test('parseCursor reads a plain numeric cursor as a tail resume', () => {
  assert.deepEqual(parseCursor('42'), { mode: 'tail', afterSequence: 42 });
  assert.deepEqual(parseCursor(encodeTailCursor(7)), { mode: 'tail', afterSequence: 7 });
});

test('parseCursor round-trips a snapshot-resume cursor', () => {
  const encoded = encodeSnapshotCursor(123, 'some-page-token');
  assert.deepEqual(parseCursor(encoded), { mode: 'snapshotResume', capturedMaxSequence: 123, pageToken: 'some-page-token' });
});

test('parseCursor round-trips a page token that itself contains colons', () => {
  const encoded = encodeSnapshotCursor(5, 'part-a:part-b:part-c');
  assert.deepEqual(parseCursor(encoded), { mode: 'snapshotResume', capturedMaxSequence: 5, pageToken: 'part-a:part-b:part-c' });
});

test('parseCursor rejects malformed cursors', () => {
  assert.throws(() => parseCursor('not-a-number'), /SYNC_CURSOR_INVALID|invalid/i);
  assert.throws(() => parseCursor('-1'), /SYNC_CURSOR_INVALID|invalid/i);
  assert.throws(() => parseCursor('S1:'), /SYNC_CURSOR_INVALID|invalid/i);
  assert.throws(() => parseCursor('S1:abc:token'), /SYNC_CURSOR_INVALID|invalid/i);
  assert.throws(() => parseCursor('S1:-5:token'), /SYNC_CURSOR_INVALID|invalid/i);
});
