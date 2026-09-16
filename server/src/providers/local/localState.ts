import { AuditEntry, BackupRecord, CloudRecord, LicenseRecord, MasterConfig, SessionRecord, UserAccount } from '../contracts';

export interface StoredCredential {
  saltBase64: string;
  verifierBase64: string;
  updatedAtEpochMs: number;
}

export interface StoredRefreshToken {
  tokenHash: string;
  userId: string;
  expiresAtEpochMs: number;
  revoked: boolean;
}

export interface LocalState {
  schemaVersion: 1;
  sequence: number;
  users: Record<string, UserAccount>;
  phoneIndex: Record<string, string>;
  credentials: Record<string, StoredCredential>;
  refreshTokens: Record<string, StoredRefreshToken>;
  licenses: Record<string, LicenseRecord>;
  sessions: Record<string, SessionRecord>;
  records: Record<string, CloudRecord>;
  changes: Array<{ sequence: number; record: CloudRecord }>;
  idempotency: Record<string, { result: import('../contracts').SyncResult; createdAtEpochMs: number }>;
  consumedNonces: Record<string, number>;
  backups: Record<string, BackupRecord>;
  masterConfig: MasterConfig;
  audits: AuditEntry[];
}

export const emptyLocalState = (): LocalState => ({
  schemaVersion: 1,
  sequence: 0,
  users: {},
  phoneIndex: {},
  credentials: {},
  refreshTokens: {},
  licenses: {},
  sessions: {},
  records: {},
  changes: [],
  idempotency: {},
  consumedNonces: {},
  backups: {},
  audits: [],
  masterConfig: { mobile: process.env.MASTER_SUPPORT_PHONE || '', pin: process.env.MASTER_ADMIN_PIN || '', updatedAtEpochMs: Date.now() }
});
