# Firebase to AWS Migration Design

## Understanding Summary

- Room remains the Android offline source of truth.
- Android communicates only with the Express backend and never accesses AWS directly.
- The backend remains provider-neutral through `IdentityProvider`, `DataStore`, `ObjectStorage`, and `SessionStore` contracts.
- Local development uses durable JSON and filesystem providers with atomic writes and process locking.
- Production providers use Cognito, DynamoDB, S3, and App Runner.
- Existing Firebase data is not migrated, and no Firebase dual-write or fallback is retained.
- Firebase is removed only after the replacement path passes its automated verification gates.

## Assumptions

- Initial usage targets small and medium businesses, while all cloud records remain tenant-isolated and horizontally partitionable.
- Local providers are development and test infrastructure only; App Runner never relies on its ephemeral filesystem for production data.
- Backend-issued local JWTs and Cognito tokens expose a normalized identity containing user, company, role, permissions, approval, license, and session claims.
- Every mutation has an immutable operation identifier and every cloud record contains `companyId`, entity identity, schema version, server version, and deletion state.
- Socket.IO notifications are hints; REST pull responses are authoritative.
- Backups are encrypted before upload and verified by size, checksum, tenant, and schema metadata before restore.

## Data Flow

1. A user action updates business data and writes a sync outbox item in one Room transaction.
2. WorkManager sends ordered batches to the Express sync API when connectivity is available.
3. Express validates identity, tenant, role, approval, license, session, schema, and idempotency before calling `DataStore`.
4. The provider writes the mutation and server-ordered change event atomically.
5. Android marks acknowledged operations as synced and retains retryable failures.
6. Android pulls cursor-based deltas, applies them in one Room transaction, and advances the cursor only after a successful commit.
7. Remote-applied changes do not create new local outbox items.
8. Deletes are retained as tombstones for at least 90 days.

## API Surface

### Authentication and Accounts

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `POST /api/v1/auth/otp/request`
- `POST /api/v1/auth/otp/verify`
- `POST /api/v1/auth/password/reset`
- `GET /api/v1/account/me`
- `POST /api/v1/staff`
- `PATCH /api/v1/staff/:userId`
- `DELETE /api/v1/staff/:userId`
- `POST /api/v1/staff/:userId/approve`

### License and Sessions

- `GET /api/v1/license/current`
- `POST /api/v1/sessions/register`
- `POST /api/v1/sessions/heartbeat`
- `DELETE /api/v1/sessions/current`
- `DELETE /api/v1/sessions/:sessionId`

### Sync

- `POST /api/v1/sync/push`
- `GET /api/v1/sync/pull?cursor=&limit=`
- `POST /api/v1/sync/conflicts/:id/resolve`

### Backup

- `POST /api/v1/backups/upload-intent`
- `POST /api/v1/backups/:backupId/complete`
- `GET /api/v1/backups`
- `POST /api/v1/backups/:backupId/download-intent`
- `DELETE /api/v1/backups/:backupId`

## Sync Model

- Push batches contain at most 50 operations and 1 MB of serialized data.
- Idempotency is scoped by `companyId` and `operationId`.
- Server versions are monotonically incremented; clients submit their `baseVersion`.
- Version mismatches return `SYNC_VERSION_CONFLICT` with the authoritative server record.
- Sales, purchases, and stock movements never use silent last-write-wins conflict resolution.
- Pending operations coalesce safely: insert plus update remains insert, while update plus delete becomes delete.
- Pull pagination uses server sequence cursors rather than device timestamps.
- Retry uses exponential backoff with jitter. Network failures, timeouts, rate limits, and temporary upstream failures are retryable.
- Invalid payloads, tenant violations, unsupported schemas, and business authorization failures are non-retryable.
- Poison operations move to a dead-letter queue after their retry budget is exhausted.

## Error Model

```json
{
  "error": {
    "code": "SYNC_VERSION_CONFLICT",
    "message": "Record changed on another device",
    "retryable": false,
    "requestId": "uuid",
    "details": {}
  }
}
```

Clients make retry decisions from the stable error code and `retryable` flag rather than message text alone. A single token refresh is attempted after an expiry response; repeated authentication failure requires interactive sign-in.

## Security Model

- Cognito is the production identity provider; local development uses a compatible JWT identity provider.
- AWS access is restricted to the backend IAM role. No AWS credential is packaged in Android.
- Tenant membership is derived from verified identity and checked against every requested `companyId`.
- Sensitive operations require current role permissions, active staff approval, active license, and a non-revoked session.
- Backend controls rate limits, payload limits, audit events, log redaction, and idempotency.
- Password material is stored only as a salted PBKDF2 verifier in local development.
- Presigned S3 operations use short expiry, tenant-prefixed keys, private objects, encryption, and server-side completion verification.

## Backup and Restore

Android creates encrypted backup content and checksum metadata. The backend validates authorization and quota before returning a local authenticated upload target or an AWS presigned S3 PUT. Completion verifies the expected tenant, object key, size, and checksum metadata. Restore uses a short-lived authenticated download target or presigned S3 GET; Android validates checksum and schema before performing an atomic Room restore.

## Testing Strategy

- Shared provider contract tests validate local and AWS provider semantics.
- Sync tests cover process restarts, offline queues, duplicate operations, retries, pagination, conflict detection, and tombstones.
- Security tests cover expired and revoked tokens, inactive licenses, staff approval, sessions, permissions, and cross-tenant attacks.
- Backup tests cover interrupted transfers, corruption, wrong-tenant access, checksum mismatch, and restore validation.
- Android tests cover outbox atomicity, retry classification, cursor commits, remote-apply suppression, and auth refresh.
- A repository scan and dependency check fail when Firebase plugins, packages, imports, config files, or rules remain.

## Decision Log

1. Chose provider abstraction with durable local providers over Docker-based emulators or immediate live AWS deployment.
2. Kept Room as the offline source of truth and selected a transactional outbox for reliable mobile mutation capture.
3. Selected backend-only cloud access to prevent AWS credentials and authorization logic from entering the APK.
4. Selected cursor-based server ordering, optimistic versions, tombstones, and idempotency instead of timestamp-only synchronization.
5. Selected presigned S3 URLs for production backup transfer to keep large payloads away from App Runner instances.
6. Rejected Firebase dual-write, fallback, and legacy data migration.
7. Restricted local filesystem providers to development and automated tests.

## Acknowledged Risks

- Existing direct Firestore code spans authentication, licensing, sessions, sync, backup, analytics, and administration; migration must preserve each feature contract.
- Local providers cannot prove live Cognito, DynamoDB, S3, IAM, or App Runner behavior. Deployment-stage integration verification remains mandatory.
- Removing Firebase before replacement verification would disable cloud behavior, so the zero-Firebase gate is the final migration step.
