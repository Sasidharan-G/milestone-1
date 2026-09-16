# AWS-Only Online-First Production Design

## Scope

- Firebase data is disposable and will not be migrated.
- The production system is AWS-only: no Firebase SDK, config, credential, auth flow, dependency, fallback, or data model remains.
- The app is online-first and offline-safe for already authenticated tenant users.
- Unrelated POS functionality remains enabled while its cloud implementations move to AWS.

## Roles and tenancy

| Role | Authority |
| --- | --- |
| `SUPER_ADMIN` | Platform-wide tenants, licenses, audits, and subscription control. |
| `ADMIN` | One tenant: business profile, users, roles, and permissions. |
| `STAFF` / `CASHIER` | Only explicitly assigned permissions in their own tenant. |

Tenant identity is derived exclusively from a verified token; an API never trusts a tenant identifier sent in an Android request. Every backend read and write applies this tenant boundary, except intentionally privileged Super Admin endpoints.

## AWS services

| Concern | Service |
| --- | --- |
| Identity, passwords, refresh tokens | Amazon Cognito User Pool |
| API and authorization logic | Node.js/Express on AWS App Runner |
| SMS OTP | Amazon SNS through the backend |
| Tenants, identities, data, subscriptions, audits, idempotency | Amazon DynamoDB |
| Encrypted tenant snapshots | Private Amazon S3 bucket with short-lived presigned URLs |
| Credentials and OTP/signing secrets | AWS Secrets Manager |
| Logs, metrics, alarms | Amazon CloudWatch |
| Public-edge protection | AWS WAF plus application rate limits |

Android never carries AWS service credentials. It calls only the Express API.

## Bootstrap and authentication

One deployment-only seed script reads the initial Super Master mobile number and password from secure environment input, creates the Cognito identity, and creates its DynamoDB `SUPER_ADMIN` profile. No master credential is hardcoded in the APK or database plaintext.

Android login calls `POST /api/v1/auth/login`. The backend validates Cognito credentials, reads the DynamoDB profile, verifies status and license where applicable, and returns normalized access and refresh tokens plus role and permissions. Android stores those tokens in the session store.

Registration is direct and in-app: request OTP, show the app OTP screen, verify the purpose-bound OTP, then create the tenant and `ADMIN` identity only after verification. No browser redirect is used.

Forgot-password, Master PIN set/change, and mobile-number changes require an SMS OTP. Changing Master credentials also requires the current password and password confirmation. The backend performs the Cognito and DynamoDB changes as a controlled operation; if it cannot complete, it preserves the prior credential state. Existing sessions remain active by product decision; subsequent sign-ins require the new credentials.

## API surface

- `POST /auth/register/request-otp`, `POST /auth/register/verify-otp`
- `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`
- `POST /auth/forgot-password/request-otp`, `POST /auth/forgot-password/verify-otp`
- `POST /account/change-mobile/request-otp`, `POST /account/change-mobile/confirm`
- `POST /account/change-password`
- `GET|POST|PATCH|DELETE /staff`
- `POST /sync/push`, `GET /sync/pull`
- `POST /restore/latest`
- `GET|PATCH /admin/tenants/:tenantId/license`, `POST /admin/tenants/:tenantId/revoke`
- `GET /admin/audit`

## DynamoDB logical records

- `USER#<userId>`: Cognito-linked mobile, tenant, role, permissions, status.
- `TENANT#<tenantId>`: business profile and subscription state.
- `ENTITY#<tenantId>#<type>#<id>`: tenant record, version, schema and tombstone metadata.
- `OPERATION#<tenantId>#<operationId>`: idempotency result.
- `LICENSE#<tenantId>`: authoritative expiry and subscription terms.
- `AUDIT#<timestamp>#<id>`: immutable actor/action/request record.
- `OTP#<purpose>#<mobile>`: expiring, one-use proof and attempt state.
- `MASTER_CONFIG`: non-secret Super Master configuration only; password belongs in Cognito.

## Online-first and offline reliability

All permitted offline business writes use one Room transaction to save the business record and a sync outbox operation. WorkManager retries the outbox when online. The server deduplicates by operation ID and uses record versions; financial and stock conflicts never silently overwrite records.

New-device login is online-only. After successful login, it restores the latest authorized tenant snapshot from S3 and then pulls cursor-based deltas. Manual backup is not exposed to users; snapshots are automatic.

Registration, login, OTP, password/mobile/Master changes, Super Master subscription control, and first-device restore are online-only. Previously authenticated tenant users can work offline with cached records. License expiry is enforced at the exact cached signed expiry timestamp in both modes, without a grace period. Warnings begin seven days before expiry. A revoke applies immediately once a device reconnects; an offline device cannot receive a remote revocation until it has network access.

## Security and verification

- OTP is purpose- and mobile-bound, one-use, five-minute expiry, attempt-limited, and rate-limited.
- Backend checks role, status, tenant, permissions, subscription, session, schema, and idempotency for every write.
- Audit logs cover credential, role, subscription, and tenant-data administration actions.
- Tests must cover no-Firebase repository scans, OTP replay/reuse, tenant-boundary attacks, exact-expiry locks, offline crash/retry exactly-once writes, and new-device restore plus delta sync.

## Decision log

1. Select an AWS-only backend-authoritative architecture; reject Firebase dual-mode and data migration.
2. Seed one global Super Master with a deployment-only script, not an APK constant.
3. Keep existing sessions after Master credential changes; only new sign-ins require changed credentials.
4. Require current Master password, new-mobile OTP, and new-password confirmation for Master credential changes.
5. Use Room transactional outbox and WorkManager for online-first offline reliability.
6. Use automated S3 snapshots and new-device restore; remove manual backup UI.
7. Enforce exact subscription expiry without an offline grace period.
