/**
 * There is no PENDING_APPROVAL here on purpose. Access is gated by the trial and the
 * Master-granted duration, not by a shop admin approving each account, so nothing ever created
 * that status and the approve/reject routes it existed for are gone. LicenseStatus below has its
 * own PENDING_APPROVAL, which is unrelated and live.
 */
export type AccountStatus = 'ACTIVE' | 'INACTIVE' | 'REJECTED';

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

export type LicenseStatus = 'PENDING_APPROVAL' | 'TRIAL' | 'ACTIVE_PAID' | 'EXPIRED' | 'REVOKED';
export type LicenseType = 'TRIAL_2_DAYS' | 'YEARLY' | 'CUSTOM_DAYS';

/** Vocabulary matches the Android `LicenseEntity` so the app can persist it verbatim. */
export interface LicenseRecord {
  companyId: string;
  ownerMobile: string;
  status: LicenseStatus;
  licenseType: LicenseType;
  daysGranted: number;
  yearsGranted: number;
  activatedAtEpochMs: number;
  validUntilEpochMs: number;
  updatedAtEpochMs: number;
  businessName?: string;
  ownerName?: string;
  notes?: string;
}

/**
 * The tenant-owned source of truth for branding and bill header details.
 * License and user `businessName` fields are deliberately only compatibility
 * mirrors; consumers must prefer this record.
 */
export interface ShopProfileRecord {
  companyId: string;
  shopName: string;
  ownerName: string;
  gstNumber: string;
  address: string;
  phone: string;
  email: string;
  logoObjectKey?: string;
  /** When the logo was last replaced or removed; devices compare it with theirs to know whether to download. */
  logoUpdatedAtEpochMs?: number;
  version: number;
  updatedAtEpochMs: number;
  updatedByUserId?: string;
}

export interface AuditEntry {
  auditId: string;
  companyId: string;
  action: string;
  actorUserId: string;
  actorRole: string;
  targetId?: string;
  details?: Record<string, unknown>;
  ip?: string;
  createdAtEpochMs: number;
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
  status: 'APPLIED' | 'DUPLICATE' | 'CONFLICT' | 'REJECTED';
  version?: number;
  record?: CloudRecord;
  /** Only on REJECTED: why this operation can never be applied as sent. */
  error?: { code: string; message: string };
}

/** Who is pushing, as the account holds it now (see requireAuth). */
export interface SyncActor { role: string; permissions: string[] }

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
  /** Every user of one company, owner included. A point query, never a table scan. */
  listCompanyUsers(companyId: string): Promise<UserAccount[]>;
  createStaff(input: StaffInput): Promise<UserAccount>;
  deleteStaff(companyId: string, userId: string): Promise<void>;
  updateStaff(companyId: string, userId: string, changes: Partial<Pick<UserAccount, 'displayName' | 'permissions' | 'status'>>): Promise<UserAccount>;
  getLicense(companyId: string): Promise<LicenseRecord | null>;
  getShopProfile(companyId: string): Promise<ShopProfileRecord | null>;
  updateShopProfile(companyId: string, changes: Partial<Omit<ShopProfileRecord, 'companyId' | 'version' | 'updatedAtEpochMs'>>): Promise<ShopProfileRecord>;
  consumeNonce(scope: string, nonce: string, expiresAtEpochMs: number): Promise<boolean>;
  /** With [actor], each write that would land is also checked against that user's permissions. */
  applySyncBatch(companyId: string, operations: SyncOperation[], actor?: SyncActor): Promise<SyncResult[]>;
  pullSync(companyId: string, cursor: string, limit: number): Promise<SyncPage>;
  purgeCompanyRecords(companyId: string): Promise<number>;
  /**
   * Which generation of the shop's cloud data this is. A purge moves it on, and a device that
   * still holds data from before must throw it away instead of pushing it back up (see
   * syncController). 0 until the first purge.
   */
  getSyncEpoch(companyId: string): Promise<number>;
  bumpSyncEpoch(companyId: string): Promise<number>;
  adminOverview(): Promise<{ licenses: LicenseRecord[]; users: UserAccount[]; masterConfig: MasterConfig }>;
  updateLicense(companyId: string, changes: Partial<LicenseRecord>): Promise<LicenseRecord>;
  deleteCompany(companyId: string): Promise<void>;
  getMasterConfig(): Promise<MasterConfig>;
  updateMasterConfig(changes: Pick<MasterConfig, 'mobile' | 'pin'>): Promise<MasterConfig>;
  appendAudit(entry: Omit<AuditEntry, 'auditId' | 'createdAtEpochMs'>): Promise<AuditEntry>;
  listAudit(companyId: string, limit: number): Promise<AuditEntry[]>;
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
  /**
   * The two implementations differ here on purpose, and callers must not assume either shape:
   * the local provider rotates the refresh token and refuses the old one on the next call, while
   * the AWS provider hands Cognito's own token back unchanged, so the same value keeps working
   * until Cognito expires or revokes it. Both halves are pinned: the reuse in
   * aws_provider_contract.test.ts, the rotation in local_provider_contract.test.ts.
   */
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
  deviceName?: string;
  revoked: boolean;
  lastSeenAtEpochMs: number;
  expiresAtEpochMs: number;
}

export interface SessionStore {
  register(input: Omit<SessionRecord, 'revoked' | 'lastSeenAtEpochMs'>): Promise<SessionRecord>;
  /** Marks the session seen and moves its expiry to [expiresAtEpochMs]: a device in use stays signed in. */
  heartbeat(companyId: string, userId: string, sessionId: string, expiresAtEpochMs: number): Promise<SessionRecord>;
  revoke(companyId: string, actorUserId: string, sessionId: string): Promise<void>;
  validate(companyId: string, userId: string, sessionId: string): Promise<boolean>;
  /** Single-device policy: revokes every other live session of the user and returns them. */
  revokeOtherSessions(companyId: string, userId: string, keepSessionId: string): Promise<SessionRecord[]>;
  /** Revokes every live session of the user (password reset, deactivation, deletion). */
  revokeAllSessions(companyId: string, userId: string): Promise<SessionRecord[]>;
}

export interface SmsSender {
  sendOtp(phone: string, code: string): Promise<string | void>;
  /** Optional provider-side check. Providers that cannot verify leave it out and otpController
   *  falls back to its own commitment comparison. */
  verifyOtp?(reqId: string, otp: string): Promise<boolean>;
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
  /**
   * A product photo has no PENDING/READY bookkeeping the way a backup does: it is small, keyed by
   * productId alone, and idempotent (a re-upload just overwrites the same object). The device
   * commits the returned [publicUrl] into the product's normal sync payload itself once the upload
   * succeeds, so this never touches the data store.
   */
  createProductImageUploadUrl(companyId: string, productId: string, contentType: string): Promise<{ uploadUrl: string; publicUrl: string; requiredHeaders?: Record<string, string> }>;
  writeProductImageContent(companyId: string, productId: string, content: Buffer): Promise<void>;
  readProductImageContent(companyId: string, productId: string): Promise<Buffer>;
  /**
   * The shop logo: one small picture per company, replaced by each upload and private to the
   * company (it is read back through the API, not a public URL). Returns the key it is kept under.
   */
  writeShopLogo(companyId: string, content: Buffer, contentType: string): Promise<string>;
  /** Throws 404 SHOP_LOGO_NOT_FOUND when the company has no logo. */
  readShopLogo(companyId: string): Promise<Buffer>;
  /** Removing a logo that is not there is not an error. */
  deleteShopLogo(companyId: string): Promise<void>;
}
