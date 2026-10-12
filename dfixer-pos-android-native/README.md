# D’Fixer POS — Android Native Foundation

This is a starter project for migrating the existing D’Fixer POS web app to a native Android tablet app.

## Current status

- Native Android project structure: scaffolded.
- Kotlin + Jetpack Compose home screen: scaffolded.
- Local database, PIN authentication, POS transactions, Bluetooth printing, backup/restore, and cloud sync: **not implemented yet**.
- Build/device testing: **not performed in this environment**.

## Open in Android Studio

1. Open this directory in a current Android Studio version.
2. Install Android SDK Platform 35 and a compatible JDK 17.
3. Allow Gradle sync to finish.
4. Run the `app` configuration on an Android tablet or emulator.

## Planned architecture

- UI: Kotlin + Jetpack Compose
- Local persistence: Room (SQLite)
- Sensitive local keys: Android Keystore
- Background sync: WorkManager, only after owner enables cloud
- HTTP: HTTPS API client with retry-safe event IDs
- Printing: Android Bluetooth APIs or a printer-vendor SDK, selected after testing the target printer model

## Migration principles

1. Keep the existing web app and its main branch unchanged.
2. Reuse business rules and API contracts only after reviewing them; do not copy browser-specific code into Kotlin.
3. Every completed transaction must be committed locally before success is shown.
4. Use a stable transaction UUID and server-side idempotency for cloud sync.
5. A printer failure must never create another transaction.
6. Do not ship with the demo PIN from the web version.

## Suggested implementation sequence

1. Room entities, DAO, migrations, and repository tests.
2. First-run owner setup and local PIN authentication with attempt throttling.
3. Products/categories and offline transaction flow.
4. Transaction history, receipt layout, reports, and cancellation audit trail.
5. Bluetooth printer discovery, test print, and 58/80 mm layouts.
6. Encrypted backup and restore.
7. Optional cloud settings, durable sync queue, retry, and idempotent API integration.
8. Android tablet acceptance tests and release signing.

## Existing web repository

https://github.com/qinnmuuu-maker/D-Fixer-POS
