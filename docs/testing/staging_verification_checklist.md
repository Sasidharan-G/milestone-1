# KadaKutty POS — Staging Verification & Disaster Recovery Checklist

This checklist defines mandatory manual and automated verification procedures before promoting a build from staging to production.

---

## 1. Authentication & Multi-Tier RBAC

- [ ] **Merchant Registration:**
  - Enter 10-digit mobile number, shop name, and password.
  - Receive MSG91 SMS OTP, verify challenge.
  - Confirm 2-day free trial activates automatically (`TRIAL_2_DAYS`, 48-hour epoch).
- [ ] **Backend Custom Token Minting:**
  - Inspect network traffic for `POST /api/v1/auth/login`.
  - Confirm HTTP 200 returns `customToken` and `user` payload with claims.
  - Confirm Firebase Auth session has `request.auth.token.companyId` populated.
- [ ] **Cashier / Staff Onboarding:**
  - Cashier signs up with mobile number and requests company link.
  - Account status initiates as `PENDING_APPROVAL`.
  - Cashier attempts login: verify application displays *"Staff account is pending Master Admin approval"*.
  - Super Master approves staff in Master Control Panel.
  - Cashier logs in successfully.
- [ ] **Single-Device Active Session Revocation:**
  - Log in as Cashier on Device 1.
  - Log in with same Cashier credentials on Device 2.
  - Verify Device 1 immediately displays session expired alert and returns to login screen.

---

## 2. Super Master Control Hardening

- [ ] **Super Master PIN Authentication:**
  - Tap Super Master Control icon on login screen.
  - Enter Master PIN: verify request dispatches to `POST /api/v1/auth/verify-master-pin`.
  - Verify incorrect PIN returns HTTP 401 and access is denied.
  - Verify correct PIN signs into Firebase with `SUPER_ADMIN` claims.
  - Confirm Firestore rules block non-super users from accessing `/master_admin/**`.
- [ ] **Super Master Remote Kill-Switch:**
  - From Super Master panel, toggle merchant status to `DEACTIVATED`.
  - Verify merchant terminal transitions immediately to `LicenseExpiredLockScreen`.

---

## 3. Offline Capabilities & Sync Integrity

- [ ] **Offline POS Billing:**
  - Enable Airplane mode on terminal.
  - Create cart, apply discount, select cash payment, checkout.
  - Confirm bill saves to Room DB with sync status `PENDING`.
  - Confirm local stock movement ledger decrements inventory.
- [ ] **Bi-directional Cloud Sync:**
  - Re-enable Wi-Fi.
  - Observe `SyncWorker` push: status transitions to `SYNCED`.
  - Open second terminal belonging to same company: observe `PullWorker` downstream update with matching inventory.
- [ ] **Poison-Pill & Dead-Letter Queue Handling:**
  - Introduce an intentional foreign key conflict.
  - Confirm failed item routes to `SyncDeadLetterEntity` without halting queue processing for subsequent sales.

---

## 4. Hardware Thermal Receipt Printing

- [ ] **58mm (32-Col) and 80mm (48-Col) Formatting:**
  - Connect 58mm Bluetooth thermal printer.
  - Print sale bill: verify lines wrap without character truncation.
  - Connect 80mm USB thermal printer.
  - Print sale bill: verify column spacing utilizes full 48-column paper width.
- [ ] **Unicode / Tamil Dynamic Bitmap Rasterization:**
  - Add items with Tamil names (e.g., *பொன்னி அரிசி*, *நாட்டுச் சர்க்கரை*).
  - Print receipt: verify clean graphics rendering via ESC/POS raster bit-image commands without mojibake/garbled text.
- [ ] **Hardware Disconnect Safeguards:**
  - Turn off printer mid-print.
  - Verify application catches I/O timeout gracefully without hanging the billing UI or generating duplicate charges.

---

## 5. Disaster Recovery & Backup Integrity

- [ ] **Local Encrypted Backup:**
  - In Settings -> Backup, generate local backup.
  - Confirm encrypted ZIP created with `metadata.json` and MD5 checksum.
- [ ] **Database Restoration Drill:**
  - Clear app storage or simulate corrupted database.
  - Trigger restore from backup ZIP.
  - Confirm SQLCipher key decrypts database, Room `PRAGMA user_version` matches schema 21, and all historical bills and stock ledgers load accurately.
