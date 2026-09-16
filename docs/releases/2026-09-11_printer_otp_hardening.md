# Printer / OTP hardening — 11 September 2026

## Release status

**Production approval pending.** Local code fixes implement panniyirukku; live backend setup, tenant authorization migration, physical printer validation innum complete aagala. Indha APK-ai production-ready-nu treat panna koodadhu.

## Implemented

- Actual sale receipt ippo configured Bluetooth / USB / Wi-Fi-LAN ESC/POS connection-ai use pannum. Settings test ticket-um shared formatter-ai use pannum.
- 58 mm / 32-column and 80 mm / 48-column layouts; saved printer profiles device-wise retain aagum. Unknown printer-ku default 58 mm. Generic printer-la universal paper-width discovery implement pannala; paper setting verify pannanum.
- Product names and oversized numbers truncate pannaama wrap aagum. Amount/name control characters printer command-a execute aagaama sanitize aagum.
- Weight-based item receipt totals saved sale-item amount-la irundhu varum; quantity x price-ai incorrect-a recalculate pannaadhu.
- Tamil / non-ASCII receipts Android fonts use panni ESC/POS raster output-a render aagum. Native vendor SDK support and physical raster compatibility innum verify pannala.
- Bluetooth/network socket deadlines; USB partial-transfer continuation; connection cleanup; actual receipts and diagnostics share print-job lock. Interrupted receipt automatically resend pannaadhu (duplicate bills avoid panna).
- Connected USB device selection and system permission request add panniyirukku. Saved printer selection supports different devices and paper sizes.
- Client SMS credentials and direct MSG91 fallback remove panniyirukku. OTP network requests cancellable; 5-second connect and 15-second total call limit. SMS delivery time provider/mobile network-ai depend pannum.
- Backend provider errors HTTP 200-la vandhaalum reject aagum. Text-la “success/verified” irukkuradha vechu authorization decide pannaadhu.
- Signed OTP session phone + provider request ID bind pannum; verified challenge single-use consumption Firestore transaction-la nadakkum.
- Reset token-ku configured random secret mandatory; future/expired/malformed tokens reject aagum. Password-reset token consumption and account credential updates transaction-la nadakkum.
- Password reset client direct cloud writes / fallback administrator provisioning remove panniyirukku. Existing local credentials server-returned verifier use panni update aagum.
- Master PIN reset server-la registered master mobile check pannum. Hardcoded master login PIN fallback remove panniyirukku. Existing master configuration access rules innum insecure; keezha blocker paarkavum.
- Server password derivation asynchronous-a run aagum; OTP requests-ai crypto computation block pannaadhu.
- Socket server Firebase Admin unavailable-na supplied token-ai accept pannaadhu.

## Verification

- Android `testDebugUnitTest`: 27 tests pass (receipt wrapping / fractional quantities / command sanitization included).
- Android release compiler, R8 and signing pipeline full run pass aachu (5m 31s). Adhukku piragu release configuration gate wire pannitu `assembleRelease` rerun pannadhula missing BACKEND_BASE_URL-ku expected failure vandhadhu. Current release build blocked; earlier generated APK distribution-ku approved illa.
- Server `npm test`: source build + 13 tests pass. Provider response, signed phone binding, token expiry / tampering tests included; external SMS send pannala.
- `npm run check:release`: current local configuration NOT READY. MSG91 widget/token, RESET_SECRET missing; service-account path readable illa; production environment unset/incorrect.
- Android `verifyProductionConfiguration`: deployed explicit HTTPS BACKEND_BASE_URL required; release pre-build depends on this check. Existing generated release uses default `https://api.kadaikutty.com/`; `/health` check indha environment-la DNS resolution failure kuduthadhu. Backend configure pannum varai production release gate block pannum.

## Remaining release blockers

1. Firebase client anonymous authentication + permissive Firestore rules still remain. Shop data, master configuration and roles-ku real tenant isolation illa. Server-issued identity/claims, registration/staff/master flows, rules and adversarial emulator tests coordinated migration thevai.
2. Deployed HTTPS backend URL and secure server configuration provide/deploy pannanum. Existing exposed MSG91 token rotate pannanum; actual secret values report/log-la podakoodadhu.
3. New Android OTP flow and new server signed-session endpoints together deploy pannanum. Old clients raw provider request ID send pannina new verification endpoint accept pannaadhu.
4. Master configuration innum legacy plaintext PIN schema use pannudhu. Master auth + server-owned verifier migration complete panna vendum.
5. Live registration, merchant/staff/master recovery, offline login after reset, tenant isolation and multi-device sync staging-la verify pannanum. Current unit tests indha integration coverage-ai replace pannaadhu.
6. Representative physical printers-la 58/80 mm, long Tamil name, large totals, 100+ items, paper-out, USB unplug, Bluetooth loss, test-print vs sale-print verify pannanum. Sunmi/iMin proprietary built-in printer SDK support implement/verify pannala.
7. `used_otp_sessions` and `used_reset_tokens` collections server-only-a remain pannanum; `expiresAt` TTL configure pannanum for storage cleanup. Current unmatched rules default deny these collections.
8. Rate limits currently process memory-la irukku. Multi-instance backend deploy panna shared rate-limit store thevai.

Code changes local workspace-la irukku. Live Firebase rules/deployment, credentials rotation, production SMS, git commit/push indha work-la perform pannala.
