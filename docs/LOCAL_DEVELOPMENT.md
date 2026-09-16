# Local Development Without Docker

## Backend

1. Copy `server/.env.example` to `server/.env`.
2. Replace `JWT_SECRET` and `RESET_SECRET` with different random values containing at least 32 characters.
3. Keep `NODE_ENV=development` and `PROVIDER_MODE=local`.
4. Set `LOCAL_DEV_OTP_CODE` to a private 4-6 digit development code.
5. Run:

```powershell
cd server
npm install
npm run dev
```

The provider writes development state atomically under `server/.local-data`. This directory is ignored by Git and must never be used as App Runner production persistence.

## Android Emulator

The debug build defaults to `http://10.0.2.2:3000/`, which maps the Android emulator to the development machine. Build with:

```powershell
cd android-app
.\gradlew.bat assembleDebug
```

For a physical Android device, set `BACKEND_BASE_URL` to the development machine's LAN address, including the trailing slash. Release builds require an HTTPS backend URL.

## Local Flow

1. Start the backend.
2. Open merchant registration in Android.
3. Enter the configured `LOCAL_DEV_OTP_CODE` when prompted.
4. Create a merchant account and sign in online once.
5. Room remains authoritative locally; WorkManager pushes the outbox and pulls server-cursor deltas.

## Verification

```powershell
cd server
npm test
cd ..
node scripts/check-legacy-cloud-free.cjs
cd android-app
.\gradlew.bat :app:compileDebugKotlin
```

Do not enable `LOCAL_DEV_OTP_CODE`, local providers, or local master PIN authentication in production.
