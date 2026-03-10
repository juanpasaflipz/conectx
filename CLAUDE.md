# Claude Code Agent Prompt — Conectx MVP

## Project Context

You are building **Conectx**, an Android app that keeps groups of friends connected inside stadiums during World Cup 2026, even when cell service is unavailable. The app uses peer-to-peer mesh networking (Bluetooth + WiFi via Google's Nearby Connections API) for offline chat and location sharing, with Firebase as a fallback when internet is available.

**Target launch:** June 2026 (World Cup opens June 11 at Estadio Azteca, Mexico City)
**Platform:** Android only (Kotlin)
**Target market:** Mexico — 3 host cities (CDMX, Guadalajara, Monterrey)

## Market & Privacy Context

Mexico's mandatory SIM registration (biometric CURP) has a June 30 deadline — mid-World Cup. Public trust in carriers is low after the Chronus hack (36.5M citizens' data leaked, Jan 2026). Conectx is positioned as a stadium utility that doesn't depend on carrier infrastructure. This is a feature, not a political statement.

**Minimum data policy (CRITICAL — enforce throughout the codebase):**
- Account creation requires ONLY: email + Stripe payment
- NO phone number collection or verification. Ever.
- NO real name required. Username is user-chosen.
- NO CURP, INE, or government ID
- NO contacts/address book access
- NO GPS location tracking (stadium location is manual: section + row + note)
- NO analytics that could identify individual users
- All user-facing strings should reflect this: "solo necesitas un email"

**Pricing (event-based, not subscription):**
- Pase Mundial: $249 MXN — full World Cup access (June 11 - July 19, 2026)
- Pase por Partido: $39 MXN — single match day (6am to midnight)
- Both are one-time Stripe payments, not recurring subscriptions

## Architecture

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
├── data/
│   ├── local/
│   │   ├── db/                  # Room database, DAOs, entities
│   │   └── preferences/        # DataStore preferences
│   ├── remote/
│   │   ├── firebase/            # Firebase Auth, RTDB, FCM
│   │   └── supabase/            # Account verification API
│   └── repository/              # Repository implementations
├── domain/
│   ├── model/                   # Domain models (Message, Squad, Peer, LocationPing)
│   ├── repository/              # Repository interfaces
│   └── usecase/                 # Business logic use cases
├── transport/
│   ├── TransportManager.kt      # Orchestrates transport selection
│   ├── nearby/                  # Nearby Connections implementation
│   │   ├── NearbyPlugin.kt     # Discovery, connection, messaging
│   │   ├── MeshRouter.kt       # Gossip-based message relay
│   │   └── PeerTracker.kt      # Track connected peers
│   ├── firebase/                # Firebase RTDB transport
│   │   └── FirebasePlugin.kt
│   └── TransportPlugin.kt      # Common interface for all transports
├── sync/
│   ├── SyncEngine.kt           # Incremental sync using Lamport clocks
│   ├── SyncSession.kt          # Per-peer sync session
│   ├── MessageQueue.kt         # Offline queue with retry
│   └── ConflictResolver.kt     # Dedup + ordering
├── presentation/
│   ├── theme/                   # Material 3 theme, colors, typography
│   ├── activation/              # Activation code entry screen
│   ├── squad/                   # Squad creation, join, member list
│   ├── chat/                    # Group chat screen
│   ├── location/                # Location sharing screen
│   └── settings/                # App settings
└── service/
    ├── MeshService.kt           # Foreground service for P2P networking
    └── SyncWorker.kt            # WorkManager for background sync
```

### Transport Layer Design

The transport layer is **pluggable**, inspired by Briar's Bramble architecture. Each transport implements a common interface:

```kotlin
interface TransportPlugin {
    val isAvailable: Boolean
    suspend fun start()
    suspend fun stop()
    suspend fun discoverPeers(): Flow<Peer>
    suspend fun connectToPeer(peer: Peer): Connection
    suspend fun sendMessage(connection: Connection, record: SyncRecord)
    fun onMessageReceived(): Flow<SyncRecord>
}
```

**Transport selection priority:**
1. Nearby Connections (P2P_CLUSTER strategy) — BT + WiFi mesh — primary for stadium
2. WiFi Aware (Android 12+) — bonus direct connections
3. Firebase Realtime Database — when internet is available
4. Local queue — when nothing works, store and retry

The `TransportManager` auto-selects the best available transport and switches transparently.

### Sync Protocol

Adapted from Briar's Bramble Synchronisation Protocol (BSP), simplified for group chat:

**SyncRecord** — the atomic unit:
```kotlin
data class SyncRecord(
    val id: String,           // UUID
    val squadId: String,      // which squad/group
    val authorId: String,     // who sent it
    val lamportClock: Long,   // causal ordering
    val timestamp: Long,      // wall clock (display only, not for ordering)
    val type: RecordType,     // CHAT, LOCATION, PING, SQUAD_META
    val payload: ByteArray,   // protobuf-encoded content
    val signature: ByteArray  // Ed25519 signature
)
```

**Sync flow between two peers:**
1. Connect → exchange latest Lamport clock per squad
2. Each side sends records the other hasn't seen
3. Receiver validates signature, deduplicates by UUID, updates local DB
4. Lamport clock = max(local, received) + 1

**Gossip relay:** When device A receives a message from B, it relays to all other connected peers (C, D, ...) that haven't seen it. This creates a mesh where messages propagate without every device needing direct connectivity.

### Squad System

A "squad" is a group of friends attending a match together:
- Created pre-match via Firebase (internet required for setup)
- 6-character invite code (e.g., `AZT-7K3`)
- All squad data + member list cached locally in Room
- Once in the stadium, squad operates entirely over mesh
- Squad members advertise their squad ID via Nearby Connections service ID

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

Users update their location manually via a quick-access UI. Location pings are SyncRecords that propagate through the mesh like chat messages.

### Firebase Backend

Used for **pre-match setup** and **fallback** when internet works:
- **Firebase Auth:** Anonymous auth + link to Supabase account
- **Firebase Realtime Database:** Squad creation, member lists, message sync (when online)
- **Firebase Cloud Messaging:** Push notifications for squad invites, pre-match coordination
- **Firebase App Distribution:** Beta testing

### Web + Payments (separate repo)

- Next.js landing page at `conectx.app`
- Stripe Payment Links: Pase Mundial ($249 MXN one-time) + Pase por Partido ($39 MXN one-time)
- Supabase for account management
- Generates activation codes with pass type + expiry that the Android app verifies

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

- **Min SDK:** 26 (Android 8.0 — covers 95%+ of Mexican Android users)
- **Target SDK:** 34
- **Gradle:** Kotlin DSL (build.gradle.kts)
- **Dependencies managed via** version catalog (libs.versions.toml)

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

[libraries]
# Compose
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-navigation = { group = "androidx.navigation", name = "navigation-compose" }

# DI
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }

# Local DB
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# P2P Transport
nearby-connections = { group = "com.google.android.gms", name = "play-services-nearby", version.ref = "nearby" }

# Firebase
firebase-bom = { group = "com.google.firebase", name = "firebase-bom", version.ref = "firebase-bom" }
firebase-auth = { group = "com.google.firebase", name = "firebase-auth-ktx" }
firebase-database = { group = "com.google.firebase", name = "firebase-database-ktx" }
firebase-messaging = { group = "com.google.firebase", name = "firebase-messaging-ktx" }

# Sync
protobuf-kotlin = { group = "com.google.protobuf", name = "protobuf-kotlin-lite", version.ref = "protobuf" }

# Crypto
tink = { group = "com.google.crypto.tink", name = "tink-android", version = "1.12.0" }

# Testing
junit = { group = "junit", name = "junit", version = "4.13.2" }
turbine = { group = "app.cash.turbine", name = "turbine", version = "1.0.0" }
mockk = { group = "io.mockk", name = "mockk", version = "1.13.9" }
```

## Priority Order for Implementation

Build in this order — each layer depends on the one before it:

1. **Project scaffold** — Android project, Hilt setup, Room DB schema, package structure
2. **Transport layer** — Nearby Connections plugin with P2P_CLUSTER, discover + connect + send/receive
3. **Sync engine** — Lamport clock sync, incremental record exchange, gossip relay
4. **Squad system** — Create/join squads, Room storage, member list
5. **Chat UI** — Compose chat screen, message list, send box
6. **Location sharing** — Section/row picker, location pings as SyncRecords
7. **Firebase integration** — Auth, RTDB sync, FCM, transport fallback
8. **Activation flow** — Supabase verification, activation code entry
9. **Foreground service** — MeshService for background P2P networking
10. **Polish** — Battery optimization, permission flows, error states, Spanish strings

## Non-Obvious Gotchas

- **Nearby Connections requires `ACCESS_FINE_LOCATION` on Android 12+** even for BLE. Show a rationale dialog explaining this is for device discovery, not GPS tracking.
- **Foreground service is required** for Nearby Connections to work when the app is backgrounded. Use `FOREGROUND_SERVICE_CONNECTED_DEVICE` type.
- **P2P_CLUSTER topology has variable bandwidth.** Do NOT send images or large payloads. Text + small protobuf records only.
- **BLE advertising has a 31-byte limit.** Squad IDs must be short. Use the squad's 6-char code as the Nearby Connections service ID.
- **Room + Flow** — use `@Query` with `Flow<List<Message>>` return types so the UI auto-updates on DB changes.
- **Lamport clocks are NOT wall clocks.** Display messages using Lamport order but show the wall-clock timestamp for the user. If clocks diverge, Lamport order wins.
- **Firebase Realtime Database has a 10MB/s free tier limit.** Sufficient for text chat at scale, but don't sync media through it.
- **Stripe Payment Links generate a `checkout.session.completed` webhook.** The web backend listens for this to create the account in Supabase.