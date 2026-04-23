# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Context

**Conectx** is a cross-platform P2P messaging app that keeps groups of friends connected inside stadiums during World Cup 2026, even when cell service is unavailable. Peer-to-peer networking (WiFi Aware + BLE GATT + Nearby Connections) with Firebase as an online fallback, and Signal Protocol E2E encryption for 1:1 messaging.

There is a companion iOS app at `/Users/juan/Developer/conectx-ios/` (native Swift, CoreBluetooth BLE mesh). Both apps share the Protobuf `conectx.proto` schema and the legacy `SyncRecordSerializer`/`PayloadCodec` wire format for group mesh.

**Target launch:** June 2026 (World Cup opens June 11 at Estadio Azteca, Mexico City)
**Platforms:** Android (Kotlin) + iOS (Swift) — must interoperate
**Target market:** Mexico — 3 host cities (CDMX, Guadalajara, Monterrey)

## Build & Test Commands

```bash
# IMPORTANT: Set JAVA_HOME to Android Studio's bundled JDK
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

# Debug build
./gradlew assembleDebug

# Release APK (requires keystore.properties)
./gradlew assembleRelease

# Release AAB for Google Play Console upload
./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab

# Run all unit tests
./gradlew testDebugUnitTest

# Run a specific test class
./gradlew testDebugUnitTest --tests "app.conectx.transport.ble.BleFragmenterTest"

# Clean build (if incremental cache corrupts)
./gradlew --stop && rm -rf app/build && ./gradlew assembleDebug
```

**Build config:** compileSdk 35, minSdk 26, targetSdk 35, Kotlin 1.9.22, Compose BOM 2024.02.00, Java 17. Single module (`:app`). Dependencies managed via `gradle/libs.versions.toml`.

**Versioning:** `versionCode` and `versionName` in `app/build.gradle.kts`. Bump `versionCode` for every Play Console upload. `BuildConfig.VERSION_NAME` is used in the Settings screen.

## Privacy Policy (CRITICAL — enforce throughout the codebase)

- Free tier requires ONLY: a username. Paid upgrade requires email + Stripe payment.
- NO phone number collection or verification. Ever.
- NO real name required. Username is user-chosen.
- NO CURP, INE, or government ID
- NO contacts/address book access
- NO GPS location tracking (stadium location is manual: section + row + note)
- NO analytics that could identify individual users
- All user-facing strings should reflect this: "solo necesitas un email"

**Pricing (one-time, not subscription):**
- Free ($0): 1 squad (max 8), text chat, location sharing, mesh networking
- Pase Mundial: $199 MXN — unlimited squads (up to 25 each), valid through July 19, 2026
- Pase por Partido: $39 MXN — same benefits, single match day (6am to midnight)

## Architecture

MVVM with clean architecture layers. Single-activity Compose app with Hilt DI.

### Transport Layer (pluggable, all run simultaneously)

Four transport plugins implement `TransportPlugin` and are bound into a Hilt multibind set via `TransportModule`. `TransportManager` starts all, merges incoming flows via `.merge()`, and broadcasts outgoing records to every available transport. Dedup happens in the sync layer (`MeshRouter.markSeen` at the record-UUID level + `ConflictResolver`).

1. **WifiAwarePlugin** — WiFi Neighbor Awareness Networking (API 26+, hardware-dependent). Preferred cross-platform transport for Android + iOS 26+. High bandwidth (100× BLE). Check `PackageManager.hasSystemFeature("android.hardware.wifi.aware")` before relying on it.
2. **BleGattPlugin** — Raw BLE GATT for cross-platform mesh with shipping iOS (CoreBluetooth). Dual-role: peripheral (GATT server + advertiser) and central (scanner + connector). Uses same service/characteristic UUIDs as iOS (`C0EC1001/1002/1003-0000-0000-0000-434F4E454354`). Universal hardware support — fallback for Android devices without WiFi Aware and only cross-platform path to iOS CoreBluetooth today.
3. **NearbyPlugin** — Google Nearby Connections (P2P_CLUSTER), BT + WiFi Direct. Android-to-Android only. Service ID: `"app.conectx"`.
4. **FirebasePlugin** — Firebase Realtime Database, online fallback. Non-blocking, runs in parallel with mesh.

`MeshRouter` handles gossip relay: marks seen record IDs, forwards unseen messages to all connected peers except sender.

### Encryption (Signal Protocol)

1:1 E2E encryption via `libsignal-android`:
- **Identity keys:** Ed25519 (long-lived, stored via Room in `SignalIdentityEntity`)
- **Signed pre-keys:** X25519 (rotated weekly, stored in `SignalSignedPreKeyEntity`)
- **One-time pre-keys:** X25519 (batch of 100, consumed by X3DH)
- **Key exchange:** Serverless X3DH over WiFi Aware or BLE
- **Session encryption:** Double Ratchet with AES-256-GCM (stored in `SignalSessionEntity`)

`SignalSessionManager` owns all crypto. `ConectxSignalStore` bridges libsignal's synchronous API to Room via `runBlocking` — safe because libsignal calls come from its own threads.

### Wire Formats (two coexist)

**1:1 encrypted messages — Protobuf Envelopes** (defined in `app/src/main/proto/conectx.proto`):
```protobuf
message Envelope {
  bytes sender_id = 1;        // Ed25519 identity public key
  bytes recipient_id = 2;
  uint64 timestamp = 3;
  bytes nonce = 4;
  bytes ciphertext = 5;       // Signal Protocol encrypted payload
  uint32 message_type = 6;    // 1=text, 2=receipt, 3=keyExchange
  bytes signature = 7;
  uint32 ttl = 8;
  bytes message_id = 9;       // UUID for deduplication
}
```
Built and parsed by `EnvelopeSerializer`. Prefixed with `0x02` when sent over WiFi Aware.

**Group mesh — SyncRecord serialization** (legacy, cross-platform with iOS):
`DataOutputStream`/`DataInputStream` big-endian format:
- Strings: 2-byte length prefix + UTF-8 bytes (Java `writeUTF` format)
- Int32: 4-byte big-endian, Int64: 8-byte big-endian
- `SyncRecordSerializer`: id | squadId | authorId | lamportClock | timestamp | type | payloadSize + payload | signatureSize + signature
- `PayloadCodec`: type-specific encoding inside payload (CHAT, LOCATION, SQUAD_META, SYNC_OFFER, REACTION, CHECK_IN, MEETUP)

**Changing any codec/schema format requires coordinating with the iOS engineer before merging.**

### Sync Engine

Bridge between transport and storage. Runs its own CoroutineScope, started/stopped by MeshService.

- **Ingest:** incoming bytes → dispatch by prefix (Envelope vs SyncRecord) → dedup → verify signature (log-only in v1) → persist to Room → update Lamport clock
- **Produce:** local user action → build SyncRecord or Envelope → sign via `signalManager.sign(...)` → persist → broadcast via `TransportManager`
- **Sync:** on new peer connection → exchange SYNC_OFFERs (squad clocks + Signal pre-key bundle) → fill gaps
- **Queue:** when no transport available, records go to `MessageQueue` (ConcurrentLinkedQueue), flushed when peers reconnect

Lamport clocks are per-squad, bootstrapped from DB on first access. Display uses Lamport order; wall-clock timestamp is for display only.

### RecordTypes

`CHAT`, `LOCATION`, `PING` (check-in), `SQUAD_META` (create/join/leave), `SYNC_OFFER`, `REACTION`, `MEETUP`

### Room Database

`conectx.db` v4, 12 entities:
- **Core:** Message, Squad, Peer, SyncRecord
- **Signal crypto:** SignalIdentity, SignalPreKey, SignalSignedPreKey, SignalSession
- **Direct messaging:** Conversation, DirectMessage
- **Product features:** Match, MeetupPoint

Uses `fallbackToDestructiveMigration()` (dev mode — acceptable while in closed testing). All DB access through DAOs, reactive queries via `Flow<List<T>>`.

### Services

- **MeshService** — Foreground service (`FOREGROUND_SERVICE_CONNECTED_DEVICE`), starts TransportManager + SyncEngine, shows live peer count in notification, 4-hour wake lock for match duration.
- **SyncWorker** — WorkManager periodic sync (15 min), network-required constraint.
- **BootReceiver** — Restarts MeshService after device reboot.

### Package Structure (key paths)

```
app.conectx/
├── crypto/                    # SignalSessionManager, ConectxSignalStore
├── data/local/db/             # Room entities + DAOs (incl. Signal stores)
├── proto/                     # Generated Protobuf classes (from conectx.proto)
├── sync/                      # SyncEngine, EnvelopeSerializer, PayloadCodec
├── transport/
│   ├── wifiaware/             # WifiAwarePlugin — primary cross-platform
│   ├── ble/                   # BleGattPlugin + BleFragmenter + BleConstants (iOS interop)
│   ├── nearby/                # NearbyPlugin, MeshRouter, PeerTracker, SyncRecordSerializer
│   └── firebase/              # FirebasePlugin (online fallback)
├── presentation/
│   ├── squad/                 # Squad list + chat + meetup
│   ├── conversation/          # 1:1 E2E conversation list + DirectChat
│   ├── schedule/              # Match calendar
│   ├── stadium/               # Stadium details + transport info
│   ├── more/                  # MoreScreen, Privacy, MeshStats
│   └── common/                # ProminentDisclosure, PermissionGate
└── service/                   # MeshService, SyncWorker, BootReceiver
```

## Coding Standards

- **Kotlin** — coroutines and Flow for all async, never raw threads
- **Compose** — single-activity, all UI in composables
- **Room** — all DB access through DAOs, never raw SQL in business logic
- **Error handling** — `Result<T>` or sealed classes, never throw from suspend functions
- **Naming** — English code, Spanish user-facing strings (with EN translations in `strings.xml`)
- **Comments** — explain WHY, not WHAT. Transport, sync, and crypto code gets thorough comments since the logic is non-obvious

## Non-Obvious Gotchas

- **WiFi Aware requires API 26+ AND hardware support.** Always check `PackageManager.hasSystemFeature("android.hardware.wifi.aware")` before relying on it. Many Mexican-market budget Androids lack it — BLE is the fallback.
- **WiFi Aware message size limit is 255 bytes.** Envelopes larger than that are fragmented with a 2-byte header (`[seq][total][chunk]`). Pre-key bundles fit unfragmented.
- **BLE scanning requires Location Services ON** on Android, even with all permissions granted. System-level requirement, not an app permission.
- **Nearby Connections requires `ACCESS_FINE_LOCATION`** on Android 12+ even for BLE. `neverForLocation` is set for scan permissions, but Nearby still needs fine location at runtime.
- **BLE GATT cross-platform: no fragmentation headers.** iOS CoreBluetooth and Android BLE stack handle ATT segmentation. Send raw `SyncRecordSerializer` bytes. Use `WRITE_TYPE_DEFAULT` (with response) for long writes.
- **iOS prepared writes:** When iOS sends messages > MTU, it uses BLE Prepare Write requests. Android GATT server must handle `onExecuteWrite` to reassemble.
- **Two Android devices may discover each other via multiple transports** (Nearby + BLE + WiFi Aware). `MeshRouter.markSeen()` deduplicates at the record UUID level.
- **P2P_CLUSTER has variable bandwidth.** Text + small binary records only. No images or large payloads over Nearby.
- **Lamport clocks are NOT wall clocks.** Lamport order wins for message ordering; wall-clock is display-only.
- **libsignal Java API is synchronous.** `ConectxSignalStore` uses `runBlocking` to bridge to Room's suspend functions.
- **Room + Flow** — use `@Query` with `Flow<List<T>>` so UI auto-updates on DB changes.
- **Signature verification is log-only in v1** — records accepted anyway for mesh reliability.
- **Firebase RTDB has 10MB/s free tier limit.** Sufficient for text chat; don't sync media through it.
- **Protobuf schema is shared with iOS.** Any changes to `conectx.proto` MUST be coordinated with the iOS engineer before merge.

## Google Play Compliance

The app is in closed testing. These policies are enforced and must not regress:

- **Prominent Disclosure:** `ProminentDisclosure.kt` is shown on first launch before any permission requests. It explains Bluetooth/WiFi, location, and data usage. Saved via `DISCLOSURE_ACCEPTED` in DataStore.
- **No `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`** — Google prohibits this unless core function requires it. The foreground service + wake lock are sufficient.
- **`neverForLocation`** flag on `BLUETOOTH_SCAN` and `NEARBY_WIFI_DEVICES` permissions in the manifest.
- **In-app privacy policy link** — accessible from both Settings and Privacy Dashboard, opens `conectx.app/privacidad` in browser.
- **Data Safety form** — Username (collected, required), Email (optional/paid), Location (P2P only), Messages (P2P only), Device ID (P2P only). All encrypted in transit.

## Repository Structure

- **`app/`** — Single-module Android app (Kotlin, Compose, Hilt)
- **`marketing/`** — Static marketing site deployed to Vercel at `conectx.app`. Contains `privacidad.html` (privacy policy), `soporte.html` (support), waitlist API, blog.
- **`supabase/`** — Supabase migrations and Edge Functions (activation code verification)
- **`functions/`** — Firebase Cloud Functions
- **`docs/`** — Project documentation, GTM memos, pena outreach tracking

## External Services

- **Firebase:** Anonymous auth, Realtime Database (squad sync + invites), Cloud Messaging (push), App Distribution (beta), Crashlytics
- **Supabase:** Activation code verification via Edge Function (`/verify-activation`)
- **Stripe:** Payment Links for paid passes, webhook generates activation codes in Supabase
- **Marketing site:** `conectx.app` (source in `/marketing/`, deployed to Vercel)
