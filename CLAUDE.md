# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Context

**Conectx** is a cross-platform P2P group chat app for friends at stadiums during World Cup 2026, designed to keep groups connected even when cell service is unavailable. Peer-to-peer mesh via WiFi Aware + BLE GATT + Nearby Connections, with Firebase as an online fallback.

**Live on both stores** — Google Play Store and Apple App Store. Any Android changes that affect the DB schema or the wire format must consider the install base already in the wild.

There is a companion iOS app at `/Users/juan/Developer/conectx-ios/` (native Swift, CoreBluetooth BLE mesh). Both apps share the `SyncRecordSerializer` + `PayloadCodec` wire format. Any cross-platform change must be coordinated between repos before merge.

**Target launch:** Full feature rollout June 2026 (World Cup opens June 11 at Estadio Azteca). Currently shipping a minimal squads+chat base; other features (match schedule, stadium info, nearby discovery, E2E direct messaging) will return as separate, focused releases.
**Platforms:** Android (Kotlin) + iOS (Swift) — visual parity is a future goal; users should feel they're using one product.
**Target market:** Mexico — 3 host cities (CDMX, Guadalajara, Monterrey)

## Scope (CRITICAL — resist feature bloat)

The app intentionally does ONE thing: squad-based group chat over P2P mesh. Every other feature in prior iterations was cut because it was noise at this stage. Before adding anything, ask: does it serve the communication base?

**Currently in scope:**
- Username-based onboarding (no phone, no email required for free tier)
- Squad create / join / leave
- Group chat within a squad (text only)
- Settings (username, sign out, upgrade, privacy link)
- Paid pass activation (Pase Mundial $199 MXN, Pase por Partido $39 MXN)

**Deferred / explicitly cut:**
- 1:1 E2E direct messaging — libsignal, conversations, DirectChat
- Match schedule / Calendario tab
- Stadium info / Estadio tab / transport sub-pages
- Meetup point within squad
- Emoji reactions + check-in ("Estoy aquí")
- Manual location sharing (section/row pings)
- MeshStats / More tab / debug screens
- Bottom navigation (single stack is enough for 5 screens)

## Build & Test Commands

```bash
# Required: Android Studio's bundled JDK
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

# Debug build
./gradlew assembleDebug

# Release APK (requires keystore.properties)
./gradlew assembleRelease

# Release AAB for Play Console
./gradlew bundleRelease
# → app/build/outputs/bundle/release/app-release.aab

# All unit tests
./gradlew testDebugUnitTest

# One test class
./gradlew testDebugUnitTest --tests "app.conectx.transport.ble.BleFragmenterTest"

# Clean (if incremental cache corrupts)
./gradlew --stop && rm -rf app/build && ./gradlew assembleDebug
```

**Build config:** compileSdk 35, minSdk 26, targetSdk 35, **Kotlin 2.0.21**, Compose BOM 2024.12.01, Hilt 2.52, Room 2.6.1, Java 17. Single module (`:app`). Dependencies managed via `gradle/libs.versions.toml`.

**Versioning:** `versionCode` and `versionName` in `app/build.gradle.kts`. Bump `versionCode` for every Play Console upload. `BuildConfig.VERSION_NAME` is shown in Settings.

## Privacy Policy (CRITICAL — enforce throughout the codebase)

- Free tier requires ONLY: a username. Paid upgrade requires email + Stripe payment.
- NO phone number collection or verification. Ever.
- NO real name required. Username is user-chosen.
- NO CURP, INE, or government ID
- NO contacts/address book access
- NO GPS location tracking
- NO analytics that could identify individual users
- All user-facing strings reflect this: "solo necesitas un email"

**Pricing (one-time, not subscription):**
- Free ($0): 1 squad (max 8), text chat, mesh networking
- Pase Mundial: $199 MXN — unlimited squads (up to 25 each), valid through July 19, 2026
- Pase por Partido: $39 MXN — same benefits, single match day (6am to midnight)

## Architecture

MVVM with clean architecture layers. Single-activity Compose app with Hilt DI. Single-stack navigation (no tabs).

### Transport Layer (pluggable, all run simultaneously)

Four transport plugins implement `TransportPlugin` and are bound into a Hilt multibind set via `TransportModule`. `TransportManager` starts all, merges incoming flows via `.merge()`, broadcasts outgoing records to every available transport. Dedup at record-UUID level (`MeshRouter.markSeen` + `ConflictResolver`).

1. **WifiAwarePlugin** — WiFi Neighbor Awareness Networking (API 26+, hardware-dependent). Preferred cross-platform when available. High bandwidth. Check `PackageManager.hasSystemFeature("android.hardware.wifi.aware")`.
2. **BleGattPlugin** — Raw BLE GATT for cross-platform mesh with shipping iOS (CoreBluetooth). Dual-role: peripheral + central. Same service/characteristic UUIDs as iOS (`C0EC1001/1002/1003-0000-0000-0000-434F4E454354`). Universal hardware support. **The only cross-platform path to iOS today.**
3. **NearbyPlugin** — Google Nearby Connections (P2P_CLUSTER), BT + WiFi Direct. Android-to-Android only.
4. **FirebasePlugin** — Firebase Realtime Database, online fallback. Non-blocking; runs in parallel with mesh.

### Wire Format (cross-platform with iOS)

`DataOutputStream`/`DataInputStream` big-endian:
- Strings: 2-byte length prefix + UTF-8 (Java `writeUTF` format)
- `SyncRecordSerializer`: id | squadId | authorId | lamportClock | timestamp | type | payloadSize + payload | signatureSize + signature
- `PayloadCodec`: type-specific encoding inside payload (CHAT, SQUAD_META, SYNC_OFFER)

**Changing any codec format requires coordinating with the iOS engineer before merge.**

### Sync Engine

Bridge between transport and storage. Runs its own CoroutineScope, started/stopped by `MeshService`.

- **Ingest:** record from mesh → dedup (ConflictResolver) → persist to Room → update Lamport clock
- **Produce:** local user action → build SyncRecord → empty signature → persist → broadcast via `TransportManager`
- **Sync:** on new peer connection → exchange `SYNC_OFFER`s (squad clocks) → fill gaps via `SyncSession`
- **Queue:** `MessageQueue` (ConcurrentLinkedQueue) — records go here when no transport is available, flush when peers reconnect

Lamport clocks are per-squad, bootstrapped from DB on first access. Display uses Lamport order; wall-clock timestamp is for UI only.

**Signature verification is log-only in v1.** Signatures are written as empty bytes. Trust is at the squad-invite layer. A real signing story returns when E2E does.

### RecordTypes

`CHAT`, `SQUAD_META` (create/join/leave), `SYNC_OFFER` — that's all.

### Room Database

`conectx.db` v2, 4 entities:
- **MessageEntity** — rendered chat messages
- **SquadEntity** — squad metadata + member list (comma-separated IDs)
- **PeerEntity** — remembered peers (currently unused but reserved)
- **SyncRecordEntity** — the mesh record log (canonical source)

All DB access through DAOs, reactive queries via `Flow<List<T>>`.

**Migrations:** `MIGRATION_1_2` adds `firebaseSyncedAt` to `sync_records` for Firebase retry idempotency. Live Play Store 0.3.0 users (v1) migrate additively — no data loss. `fallbackToDestructiveMigrationOnDowngrade()` is set so we don't wipe forward-migrated data if we test an older build.

### Services

- **MeshService** — Foreground service (`FOREGROUND_SERVICE_CONNECTED_DEVICE`). Starts TransportManager + SyncEngine, shows live peer count in notification, 4-hour wake lock for match duration.
- **SyncWorker** — WorkManager periodic sync (15 min), network-required constraint. Replays unsynced records to Firebase using `firebaseSyncedAt` for idempotency.
- **BootReceiver** — Restarts MeshService after device reboot.

### Navigation

Single flat stack (no bottom nav):
`Onboarding → Activation → SquadList → Chat → Settings`

Privacy policy is a URL-intent link in Settings (opens `conectx.app/privacidad`). No in-app Privacy Dashboard screen — the URL link satisfies Google Play's in-app-access requirement.

### Package Structure (key paths)

```
app.conectx/
├── data/local/db/             # Room entities + DAOs (4 + 3)
├── sync/                      # SyncEngine, SyncSession, PayloadCodec, LamportClock, ConflictResolver, MessageQueue
├── transport/
│   ├── wifiaware/             # WifiAwarePlugin
│   ├── ble/                   # BleGattPlugin, BleFragmenter, BleConstants (iOS interop)
│   ├── nearby/                # NearbyPlugin, MeshRouter, PeerTracker, SyncRecordSerializer
│   └── firebase/              # FirebasePlugin
├── presentation/
│   ├── activation/            # Onboarding + Activation
│   ├── squad/                 # SquadList (home)
│   ├── chat/                  # Chat
│   ├── settings/              # Settings
│   └── common/                # ProminentDisclosure, PermissionGate, banners
└── service/                   # MeshService, SyncWorker, BootReceiver
```

## Coding Standards

- **Kotlin** — coroutines and Flow for all async, never raw threads
- **Compose** — single-activity, all UI in composables
- **Room** — all DB access through DAOs, never raw SQL in business logic
- **Error handling** — `Result<T>` or sealed classes, never throw from suspend functions
- **Naming** — English code, Spanish user-facing strings (with EN translations in `strings.xml`)
- **Comments** — explain WHY, not WHAT. Transport and sync code gets thorough comments since the P2P logic is non-obvious.

## Non-Obvious Gotchas

- **WiFi Aware requires API 26+ AND hardware support.** Check `PackageManager.hasSystemFeature("android.hardware.wifi.aware")` before relying on it. Many Mexican-market budget Androids lack it — BLE is the universal fallback.
- **BLE scanning requires Location Services ON** on Android, even with all permissions granted. System-level requirement, not an app permission.
- **Nearby Connections requires `ACCESS_FINE_LOCATION`** on Android 12+ even for BLE. `neverForLocation` is set for scan permissions, but Nearby still needs fine location at runtime.
- **BLE GATT cross-platform: no fragmentation headers.** iOS CoreBluetooth and Android BLE stack handle ATT segmentation. Send raw `SyncRecordSerializer` bytes. Use `WRITE_TYPE_DEFAULT` (with response) for long writes.
- **iOS prepared writes:** When iOS sends > MTU, it uses BLE Prepare Write requests. Android GATT server must handle `onExecuteWrite` to reassemble.
- **Two Android devices may discover each other via multiple transports.** `MeshRouter.markSeen()` deduplicates at the record UUID level.
- **P2P_CLUSTER has variable bandwidth.** Text only over Nearby. No images or large payloads.
- **Lamport clocks are NOT wall clocks.** Lamport order wins for ordering; wall-clock is display-only.
- **Room + Flow** — `@Query` with `Flow<List<T>>` so UI auto-updates on DB changes.
- **Firebase RTDB has 10MB/s free tier limit.** Sufficient for text chat only.
- **Room migrations are one-way.** `fallbackToDestructiveMigrationOnDowngrade()` is set, but forward migrations (v1 → v2 → future versions) MUST be real migrations. Live Play Store users can't tolerate data loss.

## Google Play Compliance

Shipped on Play Store as `app.conectx`. These policies are enforced and must not regress:

- **Prominent Disclosure:** `ProminentDisclosure.kt` shown on first launch before any permission requests. Saved via `DISCLOSURE_ACCEPTED` in DataStore.
- **No `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`** — Google prohibits without core-function justification. Foreground service + wake lock are sufficient.
- **`neverForLocation`** flag on `BLUETOOTH_SCAN` and `NEARBY_WIFI_DEVICES` manifest permissions.
- **In-app privacy policy link** — URL-intent link in Settings opens `conectx.app/privacidad`.
- **Data Safety form** — Username (required), Email (optional/paid), Messages (P2P only), Device ID (P2P only). All encrypted in transit.

## Repository Structure

- **`app/`** — Single-module Android app (Kotlin, Compose, Hilt)
- **`marketing/`** — Static marketing site deployed to Vercel at `conectx.app`. Privacy policy, support, waitlist API, blog, sitemap.
- **`supabase/`** — Supabase Edge Function (`/verify-activation`) for paid-pass activation code verification
- **`functions/`** — Firebase Cloud Functions (TypeScript)
- **`docs/`** — Project documentation, GTM memos, pena outreach tracking

## External Services

- **Firebase:** Anonymous auth, Realtime Database (squad sync + invites), Cloud Messaging (push), App Distribution (beta), Crashlytics
- **Supabase:** Activation code verification via Edge Function
- **Stripe:** Payment Links for paid passes, webhook generates activation codes in Supabase
- **Marketing site:** `conectx.app` (source in `/marketing/`, deployed to Vercel)

## Future Work (not in scope for current release)

- **Nearby discovery / tap-to-connect** — let two physically-near users add each other to a squad without typing codes or scanning QRs. Uses existing BLE + WiFi Aware advertising; adds UX surface and `RecordType.JOIN_REQUEST`/`JOIN_ACCEPT` handshake.
- **Squad join/share polish** — big invite code, QR display, WhatsApp share intent, deep links (`conectx.app/join/MEX-4K7`).
- **iOS visual parity** — unified look-and-feel across Android + iOS; users should feel one product.
- **1:1 E2E direct messaging** — return with fresh libsignal integration against current Kotlin 2.x compatible release, coordinated with iOS.
- **Location / match schedule / stadium info** — return as separate, focused releases when the core comms loop is solid.
