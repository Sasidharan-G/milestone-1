# KadaiKutty POS — Project State & Firebase → AWS Migration Status

_Last verified: 2026-09-16 on branch `security-hardening`._

This file is the single source of truth for what is **actually implemented**. Older docs that mention
Supabase, MongoDB, Firebase or MSG91 describe previous architectures and are historical only.

## 1. Architecture (current)

```
Android (Kotlin / Compose / Room+SQLCipher, offline-first)
   │  HTTPS  BuildConfig.BACKEND_BASE_URL + /api/v1/*   (OkHttp, Bearer token + X-Session-Id)
   │  socket.io  (same origin, bearer token in handshake auth)   → data_changed → immediate pull
   ▼
Express server (server/, Node 22, TypeScript)  — PROVIDER_MODE=local | aws
   ├─ IdentityProvider  → Cognito user pool (ADMIN_USER_PASSWORD_AUTH, ID-token verify, custom:* claims)
   ├─ DataStore         → DynamoDB single table (pk/sk, GSI CompanyUsersIndex, TTL expiresAtEpochSeconds)
   ├─ ObjectStorage     → S3 private bucket, presigned PUT/GET for backups
   ├─ SessionStore      → DynamoDB (device sessions + heartbeat)
   ├─ Master PIN        → Secrets Manager
   └─ OTP SMS           → SNS direct SMS
Hosting: App Runner (image from ECR, built by GitHub Actions), CloudWatch alarm on 5xx.
```

- **No Firebase / Firestore / FCM / MSG91 / Supabase / MongoDB anywhere in code.** `node scripts/check-legacy-cloud-free.cjs` (also run in CI) fails the build if any reappear.
- Android never talks to AWS directly; there is no Amplify/Cognito SDK on the device.
- Room stays the offline source of truth; sync is push/pull batches with idempotency + versions + tombstones (`docs/architecture/FIREBASE_TO_AWS_MIGRATION_DESIGN.md`).

## 2. Migration status

| Area | Status | Notes |
|---|---|---|
| Android: Firebase SDK/config removed | ✅ Done | `google-services.json`, firebase BOM, appcheck, `Msg91OtpService` deleted |
| Android: REST client (`core/network/BackendApiClient.kt`) | ✅ Done | login/register/OTP/reset/sessions/sync/license/backups/staff/admin |
| Android: realtime (`core/network/WebSocketManager.kt`) | ✅ Done (2026-09-16) | Now injected in `HomeViewModel`, authenticates with bearer token, triggers `SyncScheduler.requestPull()` |
| Android: Master Control auth | ✅ Fixed (2026-09-16) | `MasterAuthSession.save()` is now called in `LoginViewModel.validateMasterPin` |
| Android: release config | ✅ Done | `BACKEND_BASE_URL`, `SENTRY_DSN`, `MASTER_SUPPORT_PHONE`, keystore all read from `release.properties` / env (no hard-coded passwords) |
| Server: provider abstraction (`server/src/providers`) | ✅ Done | local JSON providers + AWS providers, 18 node:test tests green |
| Server: security hardening | ✅ Done (2026-09-16) | production refuses missing `JWT_SECRET`; CORS from `ALLOWED_ORIGINS`; socket.io requires token, tenant-scoped rooms; SNS region fixed |
| Server: container + CI/CD | ✅ Done (2026-09-16) | `server/Dockerfile`, `.github/workflows/server.yml` (Node 22, tests), `deploy-server.yml` (OIDC → ECR → App Runner) |
| Infra as code (`aws-infrastructure/`) | ✅ Written, ⏳ not yet deployed | `cloudformation-template.yaml` + `setup-aws-resources.ps1/.sh`; needs AWS credentials to run |
| Super Master seeding | ✅ Script ready | `cd server && npm run seed:master -- <mobile> <6-12 digit pin>` |
| AWS deployment (Cognito/DynamoDB/S3/App Runner live) | ⏳ Pending | follow `docs/AWS_SETUP_GUIDE.md` |
| SNS SMS production access (India DLT) | ⏳ Pending (manual, AWS console) | sandbox works only for verified numbers |
| Android release pointed at App Runner URL | ⏳ Pending | set `BACKEND_BASE_URL` in `android-app/release.properties` |
| Live staging verification | ⏳ Pending | `docs/testing/staging_verification_checklist.md` |
| Firestore data migration | ✖ Not planned | design decision: fresh start on AWS, no dual-write |
| Committing the migration | ⏳ Pending | entire migration is uncommitted on `security-hardening` |

## 3. How to finish (short version)

1. `aws login` (or `aws configure`) with an account that can create IAM roles, region `ap-south-1`.
2. `cd aws-infrastructure && .\setup-aws-resources.ps1 -GitHubRepository "<owner>/<repo>"` → creates everything except App Runner, prints env block + GitHub secrets.
3. Add GitHub repo secrets `AWS_REGION`, `AWS_DEPLOY_ROLE_ARN`, `ECR_REPOSITORY_URI`; run the **Deploy Backend to AWS App Runner** workflow (pushes first image).
4. `.\setup-aws-resources.ps1 -GitHubRepository "<owner>/<repo>" -CreateAppRunnerService` → creates the service, prints `BACKEND_BASE_URL`.
5. Seed master: `cd server`, put the printed AWS block in `.env`, `npm run build && npm run seed:master -- <mobile> <pin>`.
6. Request SNS production SMS access (console) or verify test numbers in the SNS sandbox.
7. `android-app/release.properties` ← `BACKEND_BASE_URL=https://<apprunner>/` and keystore values; `./gradlew :app:assembleRelease`.
8. Walk `docs/testing/staging_verification_checklist.md`; commit.

Full detail: `docs/AWS_SETUP_GUIDE.md`. Local dev without AWS: `docs/LOCAL_DEVELOPMENT.md`.

## 4. Environment variables (server)

| Var | Local | AWS / App Runner |
|---|---|---|
| `NODE_ENV` | development | production |
| `PROVIDER_MODE` | local | aws |
| `JWT_SECRET`, `RESET_SECRET` | any ≥32 chars | Secrets Manager (injected by App Runner) |
| `ALLOWED_ORIGINS` | empty = allow all | comma-separated browser origins (Android needs none) |
| `LOCAL_DATA_DIR`, `LOCAL_DEV_OTP_CODE`, `LOCAL_DEV_OTP_BYPASS`, `MASTER_SUPPORT_PHONE`, `MASTER_ADMIN_PIN` | dev conveniences | ignored / seeded via script |
| `AWS_REGION`, `AWS_COGNITO_USER_POOL_ID`, `AWS_COGNITO_CLIENT_ID`, `AWS_COGNITO_PHONE_COUNTRY_CODE`, `AWS_DYNAMODB_TABLE`, `AWS_S3_BACKUP_BUCKET`, `AWS_PRESIGNED_URL_SECONDS`, `AWS_MASTER_PIN_SECRET_ARN` | — | CloudFormation outputs (auto-wired into App Runner) |

`npm run check:release` in `server/` validates a production `.env`.

## 5. Verification commands

```bash
# server
cd server && npm ci && npm test && npm run check:legacy
# android
cd android-app && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug
# infra
aws cloudformation validate-template --template-body file://aws-infrastructure/cloudformation-template.yaml
```

## 6. Known open items (non-migration)

- 16 KB native-library page-size warning (SQLCipher/Sentry) — see `docs/testing/2026-09-12_hardening_results.md`.
- No load test yet (5k products / 50k bills); cash-movement ledger incomplete; purchase draft memory-only.
- Physical printer validation (58/80 mm, Tamil) still pending.
- Rate limiter is in-memory (per App Runner instance); fine at MaxSize 3, revisit if scaling further.
- `npm audit` findings from Aug 2026 not yet triaged.
