# KadaiKutty POS — Project State & Firebase → AWS Migration Status

_Last verified: 2026-09-17 on branch `security-hardening`, by actually running the local server end-to-end
(`scripts/e2e-local.sh`, 40/40 checks) and `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`, not
just reading the code. See §7 for exactly what that run covers and what it does not._

This file is the single source of truth for what is **actually implemented**. Older docs that mention
Supabase, MongoDB, Firebase or MSG91 describe previous architectures and are historical only.

## 0. What changed on 2026-09-17 (security/correctness hardening on top of the Firebase→AWS migration)

The migration in §1-§6 below was already written when this pass started, but had never been run end to
end and had real gaps. Fixed this pass:

- **Single-device session enforcement was a no-op.** `POST /sessions/register` now revokes every other
  live session for that `companyId`+`userId` (tenant admin, staff, *and* the platform master all go
  through this one endpoint); `requireAuth` now rejects any call missing a valid `X-Session-Id`, so a
  signed-out device is cut off on its very next request, not when its 15-minute access token expires.
- **OTP nonce bug**: in development, `LOCAL_DEV_OTP_CODE` is fixed, which made the verify-nonce
  (previously derived from the code) collide across sends to the same phone — a second forgot-password
  OTP for a phone that had already registered would silently fail. Nonce is now a random per-send id,
  independent of the code value. Regression test added.
- **License model reworked** to match the Android `LicenseEntity` vocabulary exactly
  (`PENDING_APPROVAL|TRIAL|ACTIVE_PAID|EXPIRED|REVOKED`, `licenseType`, `daysGranted`/`yearsGranted`) and
  to an explicit action API (`PATCH /admin/licenses/:companyId {action: TRIAL|GRANT_DAYS|GRANT_YEARS|
  EXTEND_DAYS|REVOKE}`) instead of the client computing `validUntilEpochMs` itself. Expiry has zero grace
  period (checked server-side on every request via `requireActiveLicense`), and `GET /license/current`
  returns a 7-day `renewalWarning` flag.
- **Master PIN/mobile change no longer bypasses OTP.** The old `PATCH /admin/config` let a
  SUPER_ADMIN-scoped bearer token change the master PIN with no OTP proof at all — removed. The only way
  to change it is now `POST /auth/master/pin`, which requires an OTP sent to the *current* master mobile.
  The Android "Master Settings" dialog is now a two-step OTP flow instead of prefilling the current PIN
  in plaintext (the server no longer returns it — `admin/overview` returns `pinConfigured: boolean` only).
- **Password reset / staff deactivation / account deletion now revoke every live session** of the
  affected account server-side and push a `session_revoked` socket event, instead of leaving old tokens
  usable until they naturally expired.
- **Audit log implemented** (`DataStore.appendAudit`/`listAudit`, both providers) and wired into every
  sensitive action: login, login-blocked, register, staff create/update/approve/reject/deactivate,
  password reset, master login, master PIN change, license actions, company deletion, sync purge. Exposed
  via `GET /account/audit` (shop admin, own tenant) and `GET /admin/audit` (master, any tenant).
  `POST /staff` no longer requires master approval — the shop admin owns staff onboarding (per spec:
  "Admin fully controls user creation and permissions"), so a newly created cashier is `ACTIVE`
  immediately; the master's `PATCH /admin/staff/:userId` remains as a platform-level override/kill switch.
  Added `REJECTED` as a distinct account status.
- **Server hardening**: global JSON error handler + 404 handler (previously body-parser/route-miss errors
  could return an HTML stack trace); `X-Request-Id` echoed and used consistently in every error body
  (was hard-coded `"unknown"`); login has its own rate limiter; rate-limit responses are now the same
  `{error:{code,...}}` shape as every other error.
- **Android client updated to match**: `BackendApiClient.request()` takes an explicit `sessionId`
  override (needed because the master session lives only in memory, never in the tenant `SessionStore`);
  `SessionSecurityManager.registerMasterSession()`/`clearMasterSession()` are real implementations with a
  heartbeat loop (were a stub returning a random UUID and a no-op); `DefaultAuthRepository.loginOnline`
  no longer silently swallows a session-registration failure — since every call now needs a session, that
  failure now fails the login instead of leaving a token with no usable session.
- **`npm test` silently corrupted the local dev master PIN.** `otp_http.test.ts` called the real
  `sendOtp`/`verifyOtp` handlers, which reach the shared `providers()` singleton; without an explicit
  `LOCAL_DATA_DIR`, that defaults to `server/.local-data/state.json` — the exact file the dev server
  reads — and the test process never sets `MASTER_SUPPORT_PHONE`/`MASTER_ADMIN_PIN`, so it seeded an
  empty master config there. `docs/LOCAL_DEVELOPMENT.md`'s own verification steps run `npm test` then
  `npm run dev` from the same directory, so this bit on the very workflow the docs recommend (it bit
  this pass's own end-to-end run). Fixed by pointing that test at an isolated temp directory, matching
  `local_provider_contract.test.ts`.
- `server/.env` on disk was left in `PROVIDER_MODE=aws`/`NODE_ENV=production` pointing at a Sydney region
  that doesn't match the CloudFormation default (`ap-south-1`) — switched to `PROVIDER_MODE=local` for
  development; the AWS values were preserved in `server/.env.aws-sydney.bak` (gitignored) rather than
  discarded, in case they were deliberately provisioned.

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
| Server: provider abstraction (`server/src/providers`) | ✅ Done | local JSON providers + AWS providers, 22 node:test tests green |
| Server: single-device session enforcement | ✅ Done (2026-09-17) | `sessions/register` revokes every other session for companyId+userId; `requireAuth` requires `X-Session-Id`; verified live with two concurrent "devices" in `scripts/e2e-local.sh` |
| Server: license/subscription model | ✅ Done (2026-09-17) | action API, zero-grace expiry, 7-day renewal warning, matches Android `LicenseEntity` vocabulary exactly |
| Server: audit log | ✅ Done (2026-09-17) | `GET /account/audit` (shop admin), `GET /admin/audit` (master) |
| Server: master PIN/mobile change OTP-gated | ✅ Done (2026-09-17) | old non-OTP `PATCH /admin/config` removed |
| Server: security hardening | ✅ Done (2026-09-17) | production refuses missing `JWT_SECRET`; CORS from `ALLOWED_ORIGINS`; socket.io requires token + optional session validation, tenant-scoped rooms; global JSON error handler + 404 handler; consistent `X-Request-Id` |
| Server: container + CI/CD | ✅ Done | `server/Dockerfile`, `.github/workflows/server.yml` (Node 22, tests), `deploy-server.yml` (OIDC → ECR → App Runner) |
| Local dev run end-to-end | ✅ Verified 2026-09-17 | `scripts/e2e-local.sh`: register→OTP→login→single-device session→sync (tenant isolation, idempotency)→staff RBAC→forgot password→master login→license grant/extend/revoke (zero grace)→audit — 40/40 checks pass against a live `PROVIDER_MODE=local` server |
| Android compiles + unit tests against the new contract | ✅ Verified 2026-09-17 | `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` — BUILD SUCCESSFUL |
| Infra as code (`aws-infrastructure/cloudformation-template.yaml`, App Runner path) | ✖ Blocked on this account | **AWS Organizations SCP explicitly denies App Runner** on account 638120274634 — not an IAM permissions issue, `AdministratorAccess` still gets denied. Do not retry this path without an org-admin lifting the SCP. See `docs/AWS_SETUP_GUIDE.md` §0. |
| AWS deployment — actually live | ✅ Done 2026-09-17 | Elastic Beanstalk (`kadaikutty-pos-production`, ap-southeast-2), not App Runner. Cognito/DynamoDB/S3/Secrets Manager provisioned via a separate CFN stack (`kadakutty-pos-production`) that predates this pass. Today's hardened server code deployed as version `kadaikutty-pos-server-v7`; `GET /health` confirms `mode:"aws"`. Full detail: `docs/AWS_SETUP_GUIDE.md`. |
| AWS `PROVIDER_MODE` was silently falling back to local | ✅ Fixed 2026-09-17 | Three EB env var **names** had trailing whitespace (`'AWS_REGION   '` etc.) from a console copy-paste, so `process.env.PROVIDER_MODE` was `undefined` and the live server had been running in ephemeral local-JSON mode the whole time — nothing was actually reaching DynamoDB/S3/Cognito despite the environment being fully provisioned. Real, verified-live bug; see `docs/AWS_SETUP_GUIDE.md` §2. |
| HTTPS for the live backend | ✅ Done 2026-09-17 (no custom domain) | CloudFront was tried first but this account needs AWS-support account verification before it'll create any CloudFront resource — also not an IAM issue. Used an API Gateway HTTP API (`https://u3bmxkaw25.execute-api.ap-southeast-2.amazonaws.com/`) as a plain reverse proxy in front of the EB origin instead; free, no domain needed, verified end-to-end. Trade-off: it's HTTP-proxy only, so socket.io falls back to long-polling instead of a persistent WebSocket (still functional — see guide §3). |
| Super Master seeding | ✅ Script ready | `cd server && npm run seed:master -- <mobile> <6-12 digit pin>` (now hashes the PIN — see `localDataStore.hashMasterPin`) |
| SNS SMS production access (India DLT) | ⏳ Pending (manual, AWS console + India TRAI DLT registration) | sandbox works only for verified numbers; this needs the business's own PAN/GST and cannot be done from this session — see guide §5 |
| Android release pointed at the live HTTPS URL | ✅ Done 2026-09-17 | `android-app/release.properties.template` now defaults `BACKEND_BASE_URL` to the API Gateway URL above; update it once you have a custom domain |
| Android on-device / emulator manual test | ⏳ Pending | this pass verified the server contract and that Android compiles against it; nobody has tapped through the actual app UI against this server build (see §7) |
| Live staging verification | ⏳ Pending | `docs/testing/staging_verification_checklist.md` |
| Firestore data migration | ✖ Not planned | design decision: fresh start on AWS, no dual-write |
| Committing this work | ✅ Done | commit `b629686` (migration) + this hardening pass |

## 3. How to finish (short version)

**The App Runner path below (`aws-infrastructure/setup-aws-resources.ps1`, `.github/workflows/deploy-server.yml`)
does not work on the currently-used AWS account — App Runner is denied by an Organizations SCP. The
account already has a working Elastic Beanstalk deployment instead; see `docs/AWS_SETUP_GUIDE.md` for
what's actually live and how to deploy a new version to it.** The steps below are the *original* plan,
kept for an account where App Runner isn't blocked:

1. `aws login` (or `aws configure`) with an account that can create IAM roles, region `ap-south-1`.
2. `cd aws-infrastructure && .\setup-aws-resources.ps1 -GitHubRepository "<owner>/<repo>"` → creates everything except App Runner, prints env block + GitHub secrets.
3. Add GitHub repo secrets `AWS_REGION`, `AWS_DEPLOY_ROLE_ARN`, `ECR_REPOSITORY_URI`; run the **Deploy Backend to AWS App Runner** workflow (pushes first image).
4. `.\setup-aws-resources.ps1 -GitHubRepository "<owner>/<repo>" -CreateAppRunnerService` → creates the service, prints `BACKEND_BASE_URL`.
5. Seed master: `cd server`, put the printed AWS block in `.env`, `npm run build && npm run seed:master -- <mobile> <pin>`.
6. Request SNS production SMS access (console) or verify test numbers in the SNS sandbox.
7. `android-app/release.properties` ← `BACKEND_BASE_URL=https://<apprunner>/` and keystore values; `./gradlew :app:assembleRelease`.
8. Walk `docs/testing/staging_verification_checklist.md`; commit.

Full detail (what's actually deployed, App Runner/CloudFront blockers, HTTPS-without-a-domain,
how to ship a new server version): `docs/AWS_SETUP_GUIDE.md`. Local dev without AWS: `docs/LOCAL_DEVELOPMENT.md`.

## 4. Environment variables (server)

| Var | Local | AWS (live: Elastic Beanstalk) |
|---|---|---|
| `NODE_ENV` | development | production |
| `PROVIDER_MODE` | local | aws — **the env var name itself must have no trailing whitespace**; see `docs/AWS_SETUP_GUIDE.md` §2 for the exact bug this caused live |
| `JWT_SECRET`, `RESET_SECRET` | any ≥32 chars | injected via EB's environment-secrets feature from Secrets Manager (`/kadakutty-pos/production/{jwt-secret,reset-secret}`) — not plain EB env vars, don't try to set them as such (conflicts) |
| `ALLOWED_ORIGINS` | empty = allow all | comma-separated browser origins (Android needs none) |
| `LOCAL_DATA_DIR`, `LOCAL_DEV_OTP_CODE`, `LOCAL_DEV_OTP_BYPASS`, `MASTER_SUPPORT_PHONE`, `MASTER_ADMIN_PIN` | dev conveniences | ignored / seeded via script |
| `AWS_REGION`, `AWS_COGNITO_USER_POOL_ID`, `AWS_COGNITO_CLIENT_ID`, `AWS_COGNITO_PHONE_COUNTRY_CODE`, `AWS_DYNAMODB_TABLE`, `AWS_S3_BACKUP_BUCKET`, `AWS_PRESIGNED_URL_SECONDS`, `AWS_MASTER_PIN_SECRET_ARN` | — | plain EB environment variables, currently `ap-southeast-2` (not the CFN template's `ap-south-1` default) |

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
- The P0/P1 correctness bugs in `docs/testing/2026-09-12_local_production_readiness_audit.md` (edit-deletes-original-transaction, purchase unit-type coercion, ledger delete by text match, bill-sequence clock dependency, payment rounding, previous-due switch) are about local billing logic, unrelated to this migration, and were **not** touched or re-verified this pass.

## 7. What "verified end-to-end" means here, precisely

Run 2026-09-17, in order, against a real `PROVIDER_MODE=local` server process (not mocks):

1. `cd server && npm run build && npm test` — 22/22 `node:test` pass (includes both local and AWS-mocked provider contract tests).
2. Server started (`node dist/index.js`), then `bash scripts/e2e-local.sh` drove it over real HTTP with `curl`, asserting on actual response bodies: OTP send/verify (including a wrong-code rejection and a replay rejection), registration + trial grant, wrong-password rejection, session-required enforcement, two-device single-session enforcement (registering device B kills device A's heartbeat within the same request), sync push/pull with tenant-isolation and idempotency, staff creation+RBAC (a cashier cannot create staff or read admin data), staff deactivation killing its session instantly, forgot-password resetting the password and killing the old session, master login, master license grant/extend/revoke with the revoke instantly blocking sync (zero grace), backup intent creation, and audit log entries for both a tenant admin and the master. **40/40 checks passed.** The script is `scripts/e2e-local.sh` in the repo — rerun it any time with `PROVIDER_MODE=local` to reconfirm.
3. `cd android-app && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` — BUILD SUCCESSFUL, confirming the Android client (BackendApiClient, SessionSecurityManager, MasterControlViewModel, LoginViewModel) compiles and its existing unit tests still pass against the updated contract (mandatory `X-Session-Id`, the new license action API, master session now real instead of a stub).

**What this does not cover** (nobody has done these; do them before calling this production-ready):
- Actually running the Android app — emulator or device — against this server build and tapping through registration, login, a sale, sync, backup/restore, and the Master Control screen by hand. The server contract and the Android client code were checked to agree with each other file-by-file, but no APK has been launched this pass.
- AWS mode (`PROVIDER_MODE=aws`) against real Cognito/DynamoDB/S3/Secrets Manager/SNS — only the mocked-SDK unit tests in `aws_provider_contract.test.ts` exercise that code path.
- Load, concurrency, and the pre-existing local-billing correctness bugs listed above.
