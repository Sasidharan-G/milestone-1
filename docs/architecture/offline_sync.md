# Offline synchronization

Records are locally committed transactionally, then queued as `PENDING`. WorkManager uploads with connectivity constraints, retry/backoff, UUID idempotency, and state changes through `SYNCING`, `SYNCED`, `FAILED`, or `CONFLICT`. `REQUIRES_CLIENT_CONFIRMATION: SYNC_CONFLICT_POLICY`; financial conflicts are never automatically resolved.

