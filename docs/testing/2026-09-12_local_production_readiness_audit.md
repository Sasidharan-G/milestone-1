# Kadaikutty POS — local production readiness audit

> **Implementation update:** Indha audit findings-ku approm fixes execute pannappattullana. Current changes, test evidence, remaining checks: [Hardening results](2026-09-12_hardening_results.md). Keela irukkura 55% score original pre-fix assessment.

Date: 2026-09-12. Current working-tree code review; existing changes preserve pannappattadhu. Application source fixes indha audit-la pannala.

## Verdict

**Readiness estimate: 55/100. Ippo unrestricted production-ku ready illa.** Idhu engineering judgement; measured crash-free percentage, test coverage percentage, illa 100/500-user load-test result kedaiyaadhu. Confidence: medium. Cloud sync correctness, Play Store, printing score-la include pannala. Account creation/auth user ketta scope; local save-ai affect panra queue boundary mattum inspect pannappattadhu.

100–500 separate installations-nu assume pannirukken. Orey company-la 100 simultaneous counters capacity indha audit establish pannala. Per-device records count, device memory, transaction integrity mukkiyam.

## Validation

- `gradlew.bat :app:cleanTestDebugUnitTest :app:testDebugUnitTest --console=plain`: BUILD SUCCESSFUL; fresh test task execute aachu.
- 28 tests, 6 suites, 0 failures/errors/skips. Includes 3 printer + 5 sync tests; avai requested readiness scope score-ku evidence illa.
- Actual DAO SQL strings extract panni isolated Python SQLite-la check pannappattadhu: invoice substring over-delete; held-cart KG amount; wall-clock bill sequence collision.
- Android emulator/physical-device UI, process kill, disk-full, SQLCipher recovery, migration chain, sustained dataset benchmark run pannala. UI visual polish-ai screenshot/device inspection moolama assess pannala.
- Existing tests focus: credentials, Money, backup helper logic, receipt layout, sync manager, costing. Billing/purchase edit, checkout, ledger deletion, registration state-machine regression suites repository-la kaanala. Migration test 3→4 mattum; current DB version 21.

## P0 — real customer data-ku munnaadi fix

### 1. Edit open pannumbodhe original transaction delete

Evidence: `BillingViewModel.kt:593` (`loadSaleForEditing`, delete at 611), `PurchaseViewModel.kt:223` (`loadPurchaseForEditing`). Sale/purchase old rows, stock movements, credits delete pannitu draft load pannudhu. Back/cancel/process kill aana original transaction restore logic illa. Purchase draft memory-la mattum irukku. Sale edit original discounts/payment metadata-um full-aa restore pannala.

Solution: original transaction read-only-aa retain panni edit draft store pannanum. Confirm save-la original + replacement stock/credit delta + audit single DB transaction. Cancel no mutation. Stable transaction ID + edit version use pannanum.

### 2. Purchase edit KG/LITER-ai PIECE-aa maathudhu

Evidence: `PurchaseViewModel.kt:236` PurchaseLine build-la unitType pass pannala; `PurchaseDraft.kt:10` default PIECE. 1 KG = quantity 1000, price Rs100 purchase edit pannaa line total Rs100,000 aagum. Product history unit snapshot illa.

Solution: purchase item-la unitType snapshot persist panni edit-la restore pannanum; migration for old rows; units immutable after stock usage or explicit conversion workflow.

### 3. Ledger delete invoice text-ai match pannudhu

Evidence: `PurchaseDao.kt:85`, `SaleDao.kt:85`. `%invoice%` / `%billNumber%` LIKE delete; purchase supplierId filter-um illa. Actual purchase DAO query-la invoice 12 delete pannadhu invoice 123 matrum vera supplier same invoice-number entry-aiyum remove pannum.

Solution: credit entries-ku exact saleId/purchaseId foreign-key reference; deletion/reversal by immutable ID. Human-readable reason field identity illa. Already-settled bill cancellation-ku explicit refund/reversal policy.

### 4. Account creation uniqueness / false success

Evidence: `DefaultAuthRepository.kt:673` OTP registration-la existing phone check illa; new company create panni `users/{phone}` set pannudhu. Current Firebase UID reuse (`:691`), multi-document writes individual-aa nadakkudhu; failures catch panniyum `RegisterResult.Success` (`:846`). Direct register (`:483`) cloud duplicate read fail aana local-only check pannitu continue. `SettingsViewModel.kt:583` staff duplicate check local DB mattum; writes fail aana success message (`:686`).

Impact: repeated owner registration account company/password mapping overwrite panna chance; staff phone already another installation-la irundhaa conflict; local success aana account remote-la incomplete, later login/approval fail. Current Firestore rules-la anonymous user company_users write/license write allowed illa; OTP flow errors swallow pannuvadhu indha mismatch-ai conceal pannudhu. Deployment rules actual state verify pannala.

Solution: single authoritative account-creation endpoint, normalized phone reservation with atomic uniqueness, verified OTP token, idempotency key, server-assigned company/user ID, all account records atomic creation. Local session success authoritative result-ku approm. Staff creation explicit pending/retry state; global identity vs company membership separate.

### 5. Local bill save success-ai later failure hide pannalam

Evidence: `SaleRepositoryImpl.kt:103` DB save complete; queue enqueue afterwards same outer try. Enqueue throws aana Failure return despite committed bill; next retry new ID create pannum. Purchase repository-layum same pattern. Cloud synchronization algorithm issue illa; local commit/reporting boundary issue.

Solution: bill + stock + credit + outbox same transaction; unique checkout request ID. Commit success-ukku approm scheduling failure bill failure-aa kaatta koodaadhu. Crash/retry same ID return pannanum.

## P1 — daily billing correctness

### 6. Payment change / negative input / rounding

Evidence: `PaymentCheckoutDialog.kt:132,160,188`; received amounts raw-aa save. Rs100 payable, Rs200 cash, Rs100 change display; stored paidCash Rs200. `HomeViewModel.kt:166` same paidCash sum expected cash-aa use pannudhu. Negative pasted values and non-finite/huge amounts explicit reject pannala. Double-toLong truncation, separate amount conversions inconsistent paise reconciliation produce pannalam.

Solution: decimal string → exact minor units parser, input caps/nonnegative validation; cash tendered, cash change, net cash separate; UPI overpayment explicit policy. Repository validate net cash + UPI + credit = invoice total; credit requires real customer; configured credit limit enforce.

### 7. Previous due switch no effect

Evidence: `BillingScreen.kt:702`: finalPayableTotal = activeBillTotal; settleDueAmount = 0L. Dialog switch “Added to Total Bill” nu solludhu, aana payable/settlement change illa.

Solution: previous-due amount explicit settlement component, actual cash allocation + credit ledger atomic update; separate current sale revenue. Feature implement varaikkum misleading switch remove.

### 8. Multi-supplier purchase partial commit

Evidence: `PurchaseViewModel.kt:153` supplier groups sequential save. First supplier committed; next supplier missing/error aana whole cart retain; retry first supplier duplicate. Split payments proportional Double conversion truncates each group independently.

Solution: all suppliers prevalidate; complete checkout single transaction or persistent per-group idempotency + resumable progress. Integer proportional allocation with remainder distribution; allocated amounts exact reconcile.

### 9. Bill sequence clock / deletion problem

Evidence: `SaleRepositoryImpl.kt:33`, `SaleDao.kt:63` most-recent timestamp bill determine next sequence. Actual query experiment: ABC-0001 at t100, 0002 at t200, 0003 after clock rollback at t150; next query selects 0002 and generates existing 0003. `SaleEntities.kt:9` unique company/billNumber with `SaleDao.kt:12` REPLACE can replace old bill. Deleting newest bill also permits number reuse.

Solution: persistent monotonic counter reserved inside transaction; never derive counter from timestamp or surviving bills. Conflict ABORT + controlled retry, no REPLACE for new financial documents. Cancelled bills retain their number.

### 10. Profit report accounting mismatch

Evidence: `ReportDao.kt:139` profit query sums sale-item lineTotal, ignores bill-level global discount. Rs100 items minus Rs10 bill discount: sales report Rs90, profit revenue Rs100. `PurchaseDao.kt:64` uses unweighted AVG(unitValueMinorUnits) across all dates; `DefaultCostingStrategy.kt:20` historical report uses that current average. Later purchases change prior-period reported cost.

Solution: allocate invoice discount across lines exactly; freeze cost basis at sale using defined weighted-average/FIFO policy. Example: 1 unit at Rs100 + 99 at Rs10 => weighted Rs10.90, current unweighted Rs55.

### 11. Draft/held-cart recovery incomplete

Evidence: `BillingViewModel.kt:117,155`: resume replaces existing active cart without merge/confirmation; held entry deleted before delayed active save. Draft clear + insert separate operations; customerId/discount missing from DraftCartItemEntity. Process kill can lose draft or recover items without intended customer. Post-sale delayed clear can leave already-billed cart recoverable.

Solution: Cart header (customer, discount, state, checkout ID) + item snapshot; atomic cart replacement/resume/checkout completion. Resolve existing cart before resume. `DraftCartEntities.kt:42` held summary lacks KG/LITER /1000; actual query Rs100 KG example displays Rs100,000. Use shared unit-aware calculation.

### 12. Product unit mutation corrupts interpretation

Evidence: `MasterViewModels.kt:294` allows unitType change on existing product; stock_movements quantity and historical item rows have no unit snapshot. KG stock 1000 becomes interpreted as 1000 PIECE; historical edit/report uses current product unit/name.

Solution: disallow unit mutation once transactions exist, or explicit audited conversion; historical immutable product name/unit/cost snapshots.

## P1/P2 — crash, recovery, permissions, scale

### 13. Unhandled coroutine failures

Evidence: billing `save` / `checkoutSelectedItems` use try/finally without catch outside repository; settle/audit/draft DB operations and `loadSaleForEditing` launches have failure paths without user-facing exception conversion. Money exact operators can throw on extreme values; input bounds incomplete.

Impact: storage/DB failure or invalid restored values can escape coroutine and terminate screen/app; exact crash rate cannot establish without device fault injection.

Solution: bounded inputs; domain errors + UI recovery; preserve CancellationException; guaranteed isSaving cleanup; committed transaction must remain distinguishable from uncommitted failure.

### 14. Backup snapshot and restore validation gaps

Evidence: `BackupManager.kt:140` table-by-table export without single read transaction; read exceptions logged but partial backup can return success. Restore `:376` disables foreign keys, substitutes missing data with zero/empty values, commits without foreign_key_check. Entire backup JSON/ZIP in memory (`:220` readBytes).

Impact: concurrent writes can produce inconsistent snapshot; malformed/partial backup may be accepted; large file OOM risk, not measured.

Solution: consistent snapshot; fail on missing required table/read failure; validate schema, required fields, company identity and relationships in staging DB; atomic replace after validation; size limits/streaming; automatic pre-restore backup.

### 15. DB-open errors trigger generic quarantine

Evidence: `CoreModule.kt:95` any open exception quarantines DB and builds fresh DB. Migration error/disk issue gets same treatment as corruption. User can see empty/recovery dataset; file retained is useful but recovery UX/tests not established. Current MigrationTest validates only 3→4 while DB is 21.

Solution: classify migration/disk/key errors; block mutation and offer explicit recovery screen; old encrypted DB preserve. Test supported upgrade versions to 21 with real fixture rows and totals.

### 16. Permissions/auth consistency

Evidence: sale cancel checks SALE_CREATE, no separate cancel authorization (`BillingViewModel.kt:561`). Purchase ViewModel save/edit/delete lacks action permission guard. UI hiding is not consistent domain enforcement. Offline admin login (`DefaultAuthRepository.kt:358`) removes ACCOUNT_INACTIVE/REQUIRE_PASSWORD_CHANGE flags. Staff update/delete fetches user by ID without company equality assertion.

Solution: central operation permission/company checks in repository/service; distinct cancel/edit authorization and audit in same transaction; enforce inactive status uniformly; define intentional offline revocation policy. Check last-admin/self-delete behavior.

Account-security adjunct: checked-in `firestore.rules:73` authenticated users can read/create/update arbitrary root users; those documents contain password verifiers/role/company data. `staff_requests` similarly broad. This is account isolation, not sync correctness. Restrict credential/role writes to server; scope authorized readers/writers. Actual deployed rules/exploit not tested.

### 17. Shift reconciliation incomplete

Evidence: `HomeViewModel.kt:166` expected drawer = sum of cash sales only. Opening float, cash purchases, expenses, standalone credit collections/refunds not represented in this calculation.

Solution: cash movement ledger with opening balance + net receipts − payouts, explicit payment method for each movement; shift boundary and repeated close protection.

### 18. Growth/performance evidence insufficient

Positive: Room transactions, company filters, useful indexes, sales Paging(20), save mutex/isSaving guards, Money minor units, backup checksum/quarantine scaffolding irukku.

Gaps: products/customers full lists loaded; purchase history full list; purchase number generation scans all orders; profit does per-product cost queries; backup holds all data in memory. Idhu 100 users-na crash nu prove pannala; long-lived busy shop dataset benchmark thevai.

## Fix order / acceptance

1. P0: non-destructive edit; KG/LITER preservation; exact ledger references; account creation uniqueness; idempotent atomic checkout.
2. P1: payment/change/due reconciliation; monotonic numbering; profit discounts/cost history; cart recovery and unit immutability.
3. P1/P2: crash handling, backup/migration restore fixtures, repository permissions, cash movement ledger.
4. Measure on low/mid-range target phones with e.g. 5k products, 50k bills, 250k items; state these as proposed test datasets, not validated limits. Include disk-full, kill during checkout/edit/restore, repeated taps, invoice-number collision, user switching, old DB upgrades.
5. Small supervised pilot after data-loss blockers fixed; then expand toward 100 users using observed reconciliations/crash evidence. 500-installation guarantee indha audit-la illa.

Full rewrite mandatory-nu evidence illa. Existing Room/Compose foundation retain panni transactional correctness, account state, error recovery fix pannalaam. Professional feel-ku immediate priority: trustworthy totals, safe edit/cancel, honest status messages, customer/cart continuity, clear validation. Visual redesign need-ai device screenshots/testing approm decide pannanum.
