import { AppError } from '../../core/errors';

/**
 * The pull cursor is opaque to every client (Android just persists and echoes back
 * whatever `nextCursor` a pull returns), so providers are free to encode whatever they
 * need here. Three shapes:
 *  - snapshotStart: a fresh pull ("" or "0") — no history yet, begin a full-record scan.
 *  - snapshotResume: continuing a paginated full-record scan started earlier.
 *  - tail: the original behaviour — resume the change-log after a known sequence.
 * This lets a fresh pull (new device, or a full resync) always reconstruct complete
 * state from the durable current-state records, instead of only from the change log,
 * which providers may expire/prune independently of the records themselves.
 */
export type SyncCursor =
  | { mode: 'snapshotStart' }
  | { mode: 'snapshotResume'; capturedMaxSequence: number; pageToken: string }
  | { mode: 'tail'; afterSequence: number };

const SNAPSHOT_PREFIX = 'S1:';
const invalidCursor = () => new AppError(400, 'SYNC_CURSOR_INVALID', 'Sync cursor is invalid');

export const parseCursor = (raw: string): SyncCursor => {
  if (!raw || raw === '0') return { mode: 'snapshotStart' };
  if (raw.startsWith(SNAPSHOT_PREFIX)) {
    const rest = raw.slice(SNAPSHOT_PREFIX.length);
    const separator = rest.indexOf(':');
    if (separator <= 0 || separator === rest.length - 1) throw invalidCursor();
    const capturedMaxSequence = Number(rest.slice(0, separator));
    const pageToken = rest.slice(separator + 1);
    if (!Number.isSafeInteger(capturedMaxSequence) || capturedMaxSequence < 0) throw invalidCursor();
    return { mode: 'snapshotResume', capturedMaxSequence, pageToken };
  }
  const afterSequence = Number(raw.startsWith(TAIL_PREFIX) ? raw.slice(TAIL_PREFIX.length) : raw);
  if (!raw.length || !Number.isSafeInteger(afterSequence) || afterSequence < 0) throw invalidCursor();
  return { mode: 'tail', afterSequence };
};

export const encodeSnapshotCursor = (capturedMaxSequence: number, pageToken: string): string =>
  `${SNAPSHOT_PREFIX}${capturedMaxSequence}:${pageToken}`;

// A plain "0" already means "start a fresh snapshot", so a tail that resumes from the very first
// change needs its own spelling; otherwise the client would snapshot again on every pull.
const TAIL_PREFIX = 'T:';
export const encodeTailCursor = (sequence: number): string => (sequence === 0 ? `${TAIL_PREFIX}0` : String(sequence));
