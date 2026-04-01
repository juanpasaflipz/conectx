# Claude Code Agent Prompt — Conectx MVP

## Project Context

You are building **Conectx**, a cross-platform P2P messaging app that keeps friends connected inside stadiums during World Cup 2026, even when cell service is unavailable. The app uses peer-to-peer networking (WiFi Aware for cross-platform + Nearby Connections for Android-to-Android + Firebase fallback) with Signal Protocol E2E encryption.

**Target launch:** June 2026 (World Cup opens June 11 at Estadio Azteca, Mexico City)
**Platforms:** Android (Kotlin) + iOS (Swift) — must interoperate via shared protocol
**Target market:** Mexico — 3 host cities (CDMX, Guadalajara, Monterrey)

## Market & Privacy Context

Mexico's mandatory SIM registration (biometric CURP) has a June 30 deadline — mid-World Cup. Public trust in carriers is low after the Chronus hack (36.5M citizens' data leaked, Jan 2026). Conectx is positioned as a stadium utility that doesn't depend on carrier infrastructure. This is a feature, not a political statement.

**Minimum data policy (CRITICAL — enforce throughout the codebase):**
- Free tier requires ONLY: a username. Paid upgrade requires email + Stripe payment.
- NO phone number collection or verification. Ever.
- NO real name required. Username is user-chosen.
- NO CURP, INE, or government ID
- NO contacts/address book access
- NO GPS location tracking (stadium location is manual: section + row + note)
- NO analytics that could identify individual users
- All user-facing strings should reflect this: "solo necesitas un email"

**Pricing (freemium, not subscription):**
- Free ($0): 1:1 messaging, location sharing, mesh networking
- Pase Mundial: $199 MXN — group messaging, no ads, voice notes, priority mesh (valid from purchase date through July 19, 2026)
- Pase por Partido: $39 MXN — same as Mundial, single match day (6am to midnight)
- Paid passes are one-time Stripe payments, not recurring subscriptions

## Architecture

### Cross-Platform Protocol (v0.4.0+)

The canonical protocol is defined in the shared `conectx.proto` schema:
- **Wire format:** Protocol Buffers (`Envelope`, `TextPayload`, `PreKeyBundle`, `ReceiptPayload`)
- **Encryption:** Signal Protocol via libsignal (X3DH key exchange + Double Ratchet)
- **Transport:** WiFi Aware (cross-platform primary) + Nearby Connections (Android fallback) + Firebase (internet fallback)
- **App model:** 1:1 encrypted messaging (MVP), group messaging deferred

### Android App (this repo)
- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **DI:** Hilt
- **Local DB:** Room (SQLite)
- **Navigation:** Compose Navigation
- **Architecture pattern:** MVVM with clean architecture layers (data / domain / presentation)

### Package Structure
```
app.conectx/
├── di/                          # Hilt modules
├── crypto/
│   ├── SignalSessionManager.kt  # Signal Protocol session management (replaces CryptoManager)
│   └── ConectxSignalStore.kt    # libsignal storage backed by Room
├── data/
│   ├── local/
│   │   ├── db/                  # Room database, DAOs, entities (incl. Signal key stores)
│   │   └── preferences/        # DataStore preferences
│   ├── remote/
│   │   ├── firebase/            # Firebase Auth, RTDB, FCM
│   │   └── supabase/            # Account verification API
│   └── repository/              # Repository implementations
├── domain/
│   ├── model/                   # Domain models (Message, Conversation, Peer, LocationPing)
│   ├── repository/              # Repository interfaces
│   └── usecase/                 # Business logic use cases
├── transport/
│   ├── TransportManager.kt      # Orchestrates transport selection
│   ├── wifiaware/               # WiFi Aware transport (cross-platform primary)
│   │   └── WifiAwarePlugin.kt  # Publish/subscribe, message passing
│   ├── nearby/                  # Nearby Connections implementation (Android-to-Android)
│   │   ├── NearbyPlugin.kt     # Discovery, connection, messaging
│   │   ├── MeshRouter.kt       # Gossip-based message relay
│   │   └── PeerTracker.kt      # Track connected peers
│   ├── firebase/                # Firebase RTDB transport
│   │   └── FirebasePlugin.kt
│   └── TransportPlugin.kt      # Common interface for all transports
├── sync/
│   ├── SyncEngine.kt           # Incremental sync using Lamport clocks
│   ├── EnvelopeSerializer.kt   # Protobuf Envelope builder/parser
│   ├── SyncSession.kt          # Per-peer sync session
│   ├── MessageQueue.kt         # Offline queue with retry
│   └── ConflictResolver.kt     # Dedup + ordering
├── presentation/
│   ├── theme/                   # Material 3 theme, colors, typography
│   ├── activation/              # Activation code entry screen
│   ├── chat/                    # 1:1 chat screen
│   ├── conversations/           # Conversation list screen
│   ├── location/                # Location sharing screen
│   └── settings/                # App settings
├── proto/
│   └── conectx.proto            # Shared Protobuf schema (also used by iOS)
└── service/
    ├── MeshService.kt           # Foreground service for P2P networking
    └── SyncWorker.kt            # WorkManager for background sync
```

### Transport Layer Design

The transport layer is **pluggable**, inspired by Briar's Bramble architecture.

**Transport selection priority:**
1. WiFi Aware — cross-platform (Android + iOS), primary for interop
2. Nearby Connections (P2P_CLUSTER strategy) — Android-to-Android fallback
3. Firebase Realtime Database — internet fallback
4. Local queue — when nothing works, store and retry

### Encryption (Signal Protocol)

Full E2E encryption using libsignal-android:
- **Identity keys:** Ed25519 (long-lived, stored in DataStore)
- **Signed pre-keys:** X25519 (rotated weekly)
- **One-time pre-keys:** X25519 (batch of 100, consumed by X3DH)
- **Key exchange:** Serverless X3DH over BLE/WiFi Aware
- **Session encryption:** Double Ratchet with AES-256-GCM

### Wire Format (Protocol Buffers)

All messages use the shared `conectx.proto` schema:
```protobuf
message Envelope {
  bytes sender_id = 1;        // Ed25519 identity public key
  bytes recipient_id = 2;     // Ed25519 identity public key
  uint64 timestamp = 3;
  bytes nonce = 4;
  bytes ciphertext = 5;       // Signal Protocol encrypted payload
  uint32 message_type = 6;    // 1=text, 2=receipt, 3=keyExchange
  bytes signature = 7;        // Ed25519 signature over fields 1-6
  uint32 ttl = 8;
  bytes message_id = 9;       // UUID for deduplication
}
```

### Location Sharing

No GPS inside stadiums — too imprecise under roofs. Location is manual:
```kotlin
data class LocationPing(
    val section: String,      // "214"
    val row: String?,         // "F"
    val seat: String?,        // "23"
    val note: String?,        // "Near the beer stand"
    val battery: Int          // show battery so friends know if you'll go dark
)
```

## Key Dependencies

```toml
[versions]
kotlin = "1.9.22"
compose-bom = "2024.02.00"
hilt = "2.50"
room = "2.6.1"
nearby = "19.1.0"
firebase-bom = "32.7.0"
protobuf = "3.25.0"
libsignal = "0.86.5"

[libraries]
protobuf-javalite = { group = "com.google.protobuf", name = "protobuf-javalite" }
libsignal-android = { group = "org.signal", name = "libsignal-android" }
nearby-connections = { group = "com.google.android.gms", name = "play-services-nearby" }
```

## Coding Standards

- **Kotlin** — use coroutines and Flow for all async work, never raw threads
- **Compose** — single-activity architecture, all UI in composables
- **Room** — all DB access through DAOs, never raw SQL in business logic
- **Testing** — write unit tests for sync engine, transport selection, and message ordering. Use Turbine for Flow testing
- **Error handling** — use `Result<T>` or sealed classes, never throw from suspend functions
- **Naming** — English code, Spanish user-facing strings (with EN translations in `strings.xml`)
- **Comments** — explain WHY, not WHAT. Transport and sync code gets thorough comments since the P2P logic is non-obvious
- **Permissions** — request Bluetooth, WiFi, and nearby devices permissions gracefully with rationale dialogs

## Build Configuration

- **Min SDK:** 26 (Android 8.0 — covers 95%+ of Mexican Android users, required for WiFi Aware)
- **Target SDK:** 35
- **Gradle:** Kotlin DSL (build.gradle.kts)
- **Dependencies managed via** version catalog (libs.versions.toml)
- **Protobuf:** protoc plugin generates Java lite classes from `app/src/main/proto/conectx.proto`

## Non-Obvious Gotchas

- **WiFi Aware requires API 26+** but hardware support varies. Always check `PackageManager.hasSystemFeature("android.hardware.wifi.aware")` before attempting to use it. Fall back to Nearby Connections gracefully.
- **Nearby Connections requires `ACCESS_FINE_LOCATION` on Android 12+** even for BLE. Show a rationale dialog explaining this is for device discovery, not GPS tracking.
- **Foreground service is required** for P2P networking when the app is backgrounded. Use `FOREGROUND_SERVICE_CONNECTED_DEVICE` type.
- **WiFi Aware message size limit is 255 bytes.** Pre-key bundles fit, but Envelopes need fragmentation for larger messages.
- **libsignal Java API is synchronous.** The ConectxSignalStore uses `runBlocking` to bridge to Room's suspend functions. This is safe because libsignal calls from its own threads.
- **Room + Flow** — use `@Query` with `Flow<List<T>>` return types so the UI auto-updates on DB changes.
- **Lamport clocks are NOT wall clocks.** Display messages using Lamport order but show the wall-clock timestamp for the user. If clocks diverge, Lamport order wins.
- **Protobuf schema is shared with iOS.** Any changes to `conectx.proto` MUST be coordinated with the iOS engineer.
