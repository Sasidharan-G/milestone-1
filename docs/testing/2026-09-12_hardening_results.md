# Production hardening — implementation results

Indha document, earlier 55% audit-ku approm panna implementation-ai track pannudhu. Model change/sub-agents use pannala. Existing working-tree changes preserve pannappattadhu.

## Implemented

- Sale/purchase edits original document-ai open-time-la delete pannaadhu. Save-la atomic replacement, stable document identity, revision check, audit entry. Cancel/back original-ai affect pannaadhu.
- Purchase KG/LITER snapshots preserve aagum. Used product unit maatha restriction irukku.
- Ledger entries exact document ID-oda linked. Invoice substring delete remove pannappattadhu. Ambiguous legacy purchase-credit associations irundhaa destructive edit/cancel block aagum; manual review thevai.
- Sale + items + stock + credit + local outbox + checkout receipt single Room transaction. Failed queue insertion rolls back; same request retry duplicate bill create pannaadhu.
- Multi-supplier purchase batch atomic. Integer payment allocation every paise reconcile pannum.
- Persistent document counters timestamp/deleted-last-bill dependence-ai remove pannudhu. New financial records use conflict-abort. Existing document numbers edit-la preserve aagum.
- Exact decimal payment parser; nonnegative/range checks; net cash after change; UPI overpayment rejected; customer credit limit and previous-due collection validated inside checkout transaction.
- Previous-due switch actual payable/ledger-oda connected. Previous dues-ai credit-aa roll over panna reject pannum. Already-settled bills edit restriction explicit.
- Profit revenue bill discount-oda reconcile aagum. New bills cost/revenue snapshots store pannum. Historical records without cost snapshots explicitly estimated-nu label pannappattadhu.
- Active/held cart customer, discount, unit, edit context, checkout ID preserve pannum. Atomic active replacement; held resume existing cart overwrite pannaadhu. Split checkout remaining cart gets fresh durable checkout ID. KG/LITER held total correct.
- Billing/purchase operation errors user-facing state-ku convert pannappattadhu. Mutation/save overlap guards and bounded monetary inputs add pannappattadhu.
- Stock adjustment current database balance-ai transaction-kulla read pannum; administrator-only.
- DB 21→22 migration add pannappattadhu. Generic DB-open error-ku automatic quarantine/fresh empty DB creation remove pannappattadhu. Startup fails safely with preserved-file recovery instructions.
- Backup exports consistent transaction snapshot; table read failures propagate. Archive/entry read bounded; foreign-key, required identity and basic quantity validation before restore commit. Invalid restore rolls back. Unsafe best-effort raw legacy `.db` restore blocked; validated JSON ZIP supported.
- Account registration moved to server transaction with OTP proof, unique normalized phone, server-created IDs, replay consumption and retry recognition. No silent local-only registration success. Direct OTP bypass no longer creates accounts.
- Staff create/update/deactivate uses authenticated backend operation with same-company/admin checks. Password verifiers server credential collection-la store aagum. Checked-in identity rules tightened. Empty local permission set no longer grants all permissions. Offline admin inactive/password-reset flags respected.
- Existing shift feature accurately labelled **Cash Sales Reconciliation**. Full cash-drawer accounting claim remove pannappattadhu; opening float/payouts/standalone receipts automatic ledger-la innum illa.

## Validation

- Android JVM: **36 tests passed**, zero failures/errors/skips.
- Android emulator: **11 tests passed**, including the 21-to-22 migration and local billing integrity scenarios. Android 17 / API 37 emulator; selected instrumentation classes only.
- Backend: **14 tests passed**; TypeScript build passed.
- Final combined Android build: **BUILD SUCCESSFUL**. Migration test assets now depend on KSP generation, avoiding a stale schema fixture.
- Debug APK installed and cold-launched successfully; login screen reached, crash buffer empty during this smoke check. This is a startup check, not an authenticated end-to-end shop session.
- Startup exposed a **16 KB native-library compatibility warning** on this x86_64 emulator. Android ran the app in compatibility mode. SQLCipher, Sentry and other bundled native libraries need compatibility remediation and ARM64 physical-device verification before release.
- APK: `android-app/app/build/outputs/apk/debug/app-debug.apk` (testing build only).

Commands:

```powershell
cd android-app
.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.kadaikutty.pos.LocalBillingIntegrityTest,com.kadaikutty.pos.core.database.MigrationTest#migrate21To22PreservesExistingData' --max-workers=2 --console=plain
cd ../server
npm test
```

Instrumentation scenarios: duplicate request, failed edit rollback, successful edit with identity/stock preservation, stale revision, discount/cost snapshot, multi-supplier rollback, cancelled number non-reuse, exact ledger deletion, KG purchase edit, queue-failure rollback, previous-due/credit-limit, held-cart amount, backup round-trip/invalid archive, migration 21→22.

## Scope and remaining release checks

- **16 KB native-library compatibility remains unresolved** (observed emulator startup warning). Dependency/native binary updates and supported-device verification are needed before calling this release-ready.

- Backend `/auth/register`, `/auth/staff` endpoints and revised Firestore rules are **source changes only**. Deployment/live OTP and real account approval/reset/deactivation end-to-end verification were not performed. New account-management flow needs the matching backend update; do not distribute the APK against an old backend.
- Android emulator verification is not 100–500 installations load testing, low-memory physical-device testing, or a crash-free guarantee. No measured readiness/crash-free percentage is claimed after this change.
- Full cash movement ledger (opening float, payouts, refunds, separate credit collections) remains a separate accounting feature. Current reconciliation explicitly covers cash captured by checkout.
- Legacy historical cost cannot be reconstructed exactly when the original app never stored it; estimated records remain labelled. Ambiguous old credit records need review.
- JSON backup is size-limited to avoid unlimited decompression. Large-dataset streaming archive support, pre-restore archival workflow, and all supported old-version upgrade fixtures still need broader validation.
- Purchase unsaved cart remains memory-backed; committed purchases are protected. A persisted purchase draft and a broader account-switch/cart lifecycle test matrix remain follow-up work.
- Existing full-list product/customer/purchase loading and long-history performance were not redesigned. 5k products / 50k bills / 250k items benchmark remains pending.
- Printing, Play Store/release signing, cloud-sync conflict resolution and visual redesign remain outside the requested hardening scope. New schema fields are passed through existing payloads to preserve compatibility; no cloud convergence guarantee is made.

Production readiness should be reassessed after backend integration and a supervised real-shop pilot. Passing these checks supports the verified local operations, not an unrestricted production certification.

## Login architecture correction

User clarified that there is no deployed Express backend: existing authentication uses Firebase/Firestore with cached offline credentials. The earlier hardening incorrectly made online login depend on `/auth/login`. Restored the existing direct Firestore login and local credential fallback, preserving account inactive/reset checks. Normal login no longer calls Msg91OtpService or requires the Express backend. Registration/staff/reset backend flows have not been restored by this targeted correction and remain a separate integration limitation. No Firestore rules were deployed during this session. Real account sign-in still requires on-device verification; build/unit checks do not prove deployed Firebase access.
