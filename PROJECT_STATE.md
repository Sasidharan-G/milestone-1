# KadaKutty POS - Actual Project State

This document contains a line-by-line verified summary of the **actual implemented state** of the KadaKutty POS project, replacing all previous speculative or outdated handoff files.

## Project Structure
- **android-app**: The primary offline-first Android Point-of-Sale application.
- **server**: A minimal Express.js backend (currently serving as a boilerplate with basic health endpoints).

## Android Application (Fully Implemented Features)

### 1. Architecture & Core Tech Stack
- **UI**: Jetpack Compose with Material 3 (supports Mobile and Tablet layouts).
- **Architecture**: MVVM (Model-View-ViewModel).
- **Dependency Injection**: Dagger Hilt.
- **Local Database**: Room Database.
- **Remote / Backend**: Supabase (Auth, Postgres Sync, Storage).
- **Concurrency**: Kotlin Coroutines & Flows.

### 2. Security (SecurityShield)
- **Encrypted Database**: Room DB is encrypted using **SQLCipher**. The encryption key is securely generated and stored in the **Android KeyStore**.
- **Runtime Defenses**:
  - Root detection (RootBeer + custom path checks).
  - Debugger attachment detection.
  - Proxy / VPN block.
  - Binary integrity checking (SHA-256 signature validation).
- **Biometric Auth**: Supports fingerprint/face unlock for sensitive actions (e.g., Cloud Backups).

### 3. Authentication & User Management
- **Supabase Auth**: Integrated email/password and Google Sign-In.
- **Deep Linking**: Implemented `kadakutty://login-callback` using `<activity-alias>` to handle Supabase password reset flows without breaking the launcher icon.
- **Offline Auth**: Cashiers can log in offline using locally hashed credentials (salt & verifier).
- **Role-Based Access Control (RBAC)**: Supports admin vs. cashier permissions (e.g., `SALE_CREATE`, `REPORT_VIEW`).

### 4. Offline-First Sync & Data Management
- **SyncWorker**: A robust WorkManager implementation that periodically pushes local Room DB changes to Supabase Postgres.
- **Conflict Resolution / Orphan Handling**: The sync engine explicitly handles relational deletions (e.g., deleting orphan `sale_items` or `purchase_items` before updating parent records) to ensure Supabase constraints are respected.

### 5. Backup & Restore
- **Local Backup**: Packages the encrypted `.db` file and a `metadata.json` (containing MD5 checksum and `dbVersion`) into a `.zip` archive.
- **Cloud Backup**: Uploads/Downloads the `.zip` archive to **Supabase Storage**.
- **Restore Logic**: Validates MD5 checksums, handles SQLCipher decryption via `SupportFactory`, and explicitly restores `PRAGMA encrypted.user_version` to prevent Room schema mismatch crashes.

### 6. Hardware & Integrations
- **Printers**: Built-in support for **Bluetooth** and **USB** thermal printers using ESC/POS commands (e.g., `BluetoothPrinterDriver`, `UsbPrinterDriver`).
- **Exporting**: Ability to generate and share **PDF** and **CSV/Excel** reports.
- **Crash Reporting**: Integrated **Sentry** for crash tracking (initialized in `BillingApplication`).

### 7. Core Business Modules
- **Masters**: CRUD operations for Categories, Products, Customers, Suppliers, and Expenses.
- **Billing (Sales)**: Cart management, tax calculations, and invoice generation.
- **Purchases (Stock Inward)**: Managing supplier invoices and updating stock quantities.
- **Reports**: Sales reports, stock reports, and profit analysis using a defined `CostingStrategy` (e.g., FIFO or Moving Average).

---

## What is NOT Implemented / Pending
- **Server-Side Logic**: The `server` folder is mostly an empty Express scaffold. Any complex server-side processing or multi-tenant API routing beyond Supabase direct connections is not yet built.

## Recent Critical Fixes (Aug 2026)
1. **SQLCipher Crash**: Fixed an immediate crash on launch by adding `SQLiteDatabase.loadLibs(this)` in the Application class.
2. **Deep Links**: Moved the Supabase redirect intent-filter to an `<activity-alias>` to fix the Android 12+ disappearing launcher icon bug.
3. **App Icon**: Added an Adaptive Icon (`ic_launcher.xml`) with a Primary Blue background to remove the white borders around the legacy PNG logo.
4. **Sync & Backup**: Fixed `user_version` resets during restore and Supabase foreign key constraint errors during sync.
