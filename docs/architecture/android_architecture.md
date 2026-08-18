# Android architecture

Feature-first Clean Architecture: `presentation → domain → data`. Core contains common errors/results, database, network, authentication, sync, printing, export, sharing, security, and UI. No Compose screen may perform SQL, networking, or financial computation. Money uses integer minor units.

