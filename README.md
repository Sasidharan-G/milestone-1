# Client Billing

Offline-first Android billing system with an optional Node.js synchronization backend.

## Status

Phase 0 and the Phase 1 core foundation are scaffolded. Business workflows requiring
client decisions are explicitly tracked under `docs/requirements`.

## Layout

- `android-app/` — Kotlin, Compose, Clean Architecture application
- `server/` — TypeScript API for online authentication and synchronization
- `docs/` — requirements, architecture, API, database, and test evidence

## Local validation

```powershell
cd server
npm install
npm run lint
npm test
npm run build
```

Android requires an installed Android SDK and Gradle wrapper before `assembleDebug` can be executed.

