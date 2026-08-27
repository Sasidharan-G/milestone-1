# Development handoff

Updated: 2026-08-17

## Completed and validated

- Phase 0: repository contracts, documentation, backend scaffold, requirements traceability.
- Phase 1: Android Compose/Hilt/Room/Retrofit/DataStore/WorkManager foundation.
- Phase 2: authentication contracts, session store, PBKDF2 offline verifier, permission model.
- Phase 3: master-entity foundation and Room migration.
- Phase 4: sale totals, sale persistence contract, SALE stock movement contract.
- Phase 5: Purchase draft, room migrations, purchase repository, viewmodel, and tests.
- Phase 6: Report Room queries, report ViewModel/UI, PDF/Excel integration, and tests.

Validated earlier: Android `test`, `lint`, and `assembleDebug`; backend `npm run lint`, `npm test`, and `npm run build`.

## Current in-progress work

### Phase 7 — User and Settings

Implemented: `UserEntity`, `AppPreferences`, basic auth sync.

Remaining: User management UI (admin only), permission toggles, dark mode/language settings, printer configuration UI.

## Mandatory confirmations

Tax/GST/discount/rounding/payment rules; bill-number strategy; optional product/expense fields; `PROFIT_COSTING_METHOD`; `SYNC_CONFLICT_POLICY`; report columns/formulas; printer details; and voice-message transcript.

## Resume order

1. Finish Phase 7 User management and settings UI.
2. Continue Phases 8–12 sequentially.

Do not mark Phase 7 complete until its remaining work and validation are done.
