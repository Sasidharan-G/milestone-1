export type AccountStatus = 'ACTIVE' | 'INACTIVE' | 'PENDING_APPROVAL';

export interface UserAccount {
  userId: string;
  companyId: string;
  phone: string;
  displayName: string;
  businessName?: string;
  role: 'ADMIN' | 'CASHIER' | 'SUPER_ADMIN';
  permissions: string[];
  status: AccountStatus;
  createdAtEpochMs: number;
  updatedAtEpochMs: number;
}

export interface LicenseRecord {
  companyId: string;
  ownerMobile: string;
  status: 'TRIAL' | 'ACTIVE' | 'EXPIRED' | 'SUSPENDED';
  validUntilEpochMs: number;
  updatedAtEpochMs: number;
  businessName?: string;
  ownerName?: string;
}

export interface MasterConfig { mobile: string; pin: string; updatedAtEpochMs: number; }

export interface CloudRecord {
  companyId: string;
  entityType: string;
  entityId: string;
  version: number;
  schemaVersion: number;
  updatedAtEpochMs: number;
  deleted: boolean;
  payload: Record<string, unknown>;
}

export interface SyncOperation {
  operationId: string;
  companyId: string;
  entityType: string;
  entityId: string;
  operation: 'INSERT' | 'UPDATE' | 'PARTIAL_UPDATE' | 'DELETE' | 'UPSERT';
  baseVersion?: number;
  schemaVersion: number;
  payload?: Record<string, unknown>;
}

export interface SyncResult {
  operationId: string;
  status: 'APPLIED' | 'DUPLICATE' | 'CONFLICT';
  version?: number;
  record?: CloudRecord;
}

export interface SyncPage {
  records: CloudRecord[];
  nextCursor: string;
  hasMore: boolean;
}

export interface NewAccountInput {
  phone: string;
  displayName: string;
  businessName: string;
  password: string;
}

export interface StaffInput {
  companyId: string;
  phone: string;
  displayName: string;
  password?: string;
  permissions: string[];
}

export interface DataStore {
  createAccount(input: NewAccountInput): Promise<{ user: UserAccount; license: LicenseRecord }>;
  findUserByPhone(phone: string): Promise<UserAccount | null>;
  findUserById(userId: string): Promise<UserAccount | null>;
  listStaff(companyId: string): Promise<UserAccount[]>;
  createStaff(input: StaffInput): Promise<UserAccount>;
  deleteStaff(companyId: string, userId: string): Promise<void>;
  updateStaff(companyId: string, userId: string, changes: Partial<Pick<UserAccount, 'displayName' | 'permissions' | 'status'>>): Promise<UserAccount>;
  getLicense(companyId: string): Promise<LicenseRecord | null>;
  consumeNonce(scope: string, nonce: string, expiresAtEpochMs: number): Promise<boolean>;
  applySyncBatch(companyId: string, operations: SyncOperation[]): Promise<SyncResult[]>;
  pullSync(companyId: string, cursor: string, limit: number): Promise<SyncPage>;
  purgeCompanyRecords(companyId: string): Promise<number>;
  adminOverview(): Promise<{ licenses: LicenseRecord[]; users: UserAccount[]; masterConfig: MasterConfig }>;
  updateLicense(companyId: string, changes: Partial<LicenseRecord>): Promise<LicenseRecord>;
  deleteCompany(companyId: string): Promise<void>;
  getMasterConfig(): Promise<MasterConfig>;
  updateMasterConfig(changes: Pick<MasterConfig, 'mobile' | 'pin'>): Promise<MasterConfig>;
}

export interface IdentityTokens {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
}

export interface VerifiedIdentity {
  userId: string;
  companyId: string;
  role: UserAccount['role'];
  permissions: string[];
  phone_number?: string;
  super_admin?: boolean;
  [key: string]: unknown;
}

export interface IdentityProvider {
  setPassword(userId: string, password: string): Promise<void>;
  authenticate(phone: string, password: string): Promise<{ user: UserAccount; tokens: IdentityTokens }>;
  refresh(refreshToken: string): Promise<{ user: UserAccount; tokens: IdentityTokens }>;
  revokeUser(userId: string): Promise<void>;
  deleteUser(userId: string): Promise<void>;
  verifyAccessToken(token: string): Promise<VerifiedIdentity>;
  authenticatePlatform(phone: string, pin: string): Promise<{ tokens: IdentityTokens; mobile: string }>;
}

export interface SessionRecord {
  sessionId: string;
  companyId: string;
  userId: string;
  deviceId: string;
  revoked: boolean;
  lastSeenAtEpochMs: number;
  expiresAtEpochMs: number;
}

export interface SessionStore {
  register(input: Omit<SessionRecord, 'revoked' | 'lastSeenAtEpochMs'>): Promise<SessionRecord>;
  heartbeat(companyId: string, userId: string, sessionId: string): Promise<SessionRecord>;
  revoke(companyId: string, actorUserId: string, sessionId: string): Promise<void>;
  validate(companyId: string, userId: string, sessionId: string): Promise<boolean>;
}

export interface BackupRecord {
  backupId: string;
  companyId: string;
  objectKey: string;
  fileName: string;
  sizeBytes: number;
  checksumSha256: string;
  schemaVersion: number;
  status: 'PENDING' | 'READY';
  createdAtEpochMs: number;
}

export interface ObjectStorage {
  createUploadIntent(input: Omit<BackupRecord, 'backupId' | 'objectKey' | 'status' | 'createdAtEpochMs'>): Promise<BackupRecord & { uploadUrl: string; requiredHeaders?: Record<string, string> }>;
  complete(companyId: string, backupId: string): Promise<BackupRecord>;
  list(companyId: string): Promise<BackupRecord[]>;
  createDownloadIntent(companyId: string, backupId: string): Promise<{ backup: BackupRecord; downloadUrl: string }>;
  writeLocalContent(companyId: string, backupId: string, content: Buffer): Promise<void>;
  readLocalContent(companyId: string, backupId: string): Promise<Buffer>;
  delete(companyId: string, backupId: string): Promise<void>;
}
