package app.conectx.transport

import android.util.Log
import app.conectx.domain.model.SyncRecord
import app.conectx.transport.ble.BleGattPlugin
import app.conectx.transport.firebase.FirebasePlugin
import app.conectx.transport.nearby.NearbyPlugin
import app.conectx.transport.nearby.SyncRecordSerializer
import app.conectx.transport.wifiaware.WifiAwarePlugin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates transport selection and lifecycle.
 *
 * All transports run simultaneously so messages arrive via whichever
 * path is fastest. Dedup happens in the sync layer (MeshRouter + ConflictResolver).
 *
 * Priority order (each runs in parallel when available):
 * 1. WiFi Aware — preferred cross-platform (Android + iOS 26+ with hardware support), high bandwidth
 * 2. BLE GATT — cross-platform fallback (shipping iOS CoreBluetooth + Android devices without WiFi Aware)
 * 3. Nearby Connections (BT + WiFi) — Android-to-Android mesh
 * 4. Firebase Realtime Database — internet fallback (runs in parallel, non-blocking)
 */
@Singleton
class TransportManager @Inject constructor(
    val nearbyPlugin: NearbyPlugin,
    val bleGattPlugin: BleGattPlugin,
    val firebasePlugin: FirebasePlugin,
    val wifiAwarePlugin: WifiAwarePlugin
) {
    data class SendResult(
        val accepted: Boolean,
        val sentViaFirebase: Boolean
    )

    companion object {
        private const val TAG = "TransportManager"
    }

    private val plugins: List<TransportPlugin> =
        listOf(wifiAwarePlugin, bleGattPlugin, nearbyPlugin, firebasePlugin)

    val isAnyTransportAvailable: Boolean
        get() = plugins.any { it.isAvailable }

    /**
     * Starts all transports. Call from MeshService when the foreground
     * service is created.
     */
    suspend fun startAll() {
        for (plugin in plugins) {
            try {
                plugin.start()
                Log.d(TAG, "Started ${plugin::class.simpleName}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start ${plugin::class.simpleName}", e)
            }
        }
    }

    /**
     * Stops all transports. Call from MeshService.onDestroy().
     */
    suspend fun stopAll() {
        for (plugin in plugins) {
            try {
                plugin.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop ${plugin::class.simpleName}", e)
            }
        }
    }

    /**
     * Merges the incoming message flows from ALL active transports into
     * a single stream. The sync engine subscribes to this one flow.
     */
    fun incomingMessages(): Flow<SyncRecord> {
        return plugins.map { it.onMessageReceived() }.merge()
    }

    /**
     * Sends a record via all available transports.
     * WiFi Aware broadcasts to discovered peers (cross-platform high-bandwidth).
     * BLE GATT broadcasts to connected BLE peers (iOS interop + low-end Android).
     * Nearby broadcasts to all connected peers via mesh (Android-to-Android).
     * Firebase pushes to RTDB for online squad members.
     * Returns true if at least one transport accepted the record.
     */
    suspend fun send(record: SyncRecord): SendResult {
        var sent = false
        var sentViaFirebase = false

        // WiFi Aware: broadcast to all discovered peers (cross-platform primary)
        if (wifiAwarePlugin.isAvailable) {
            wifiAwarePlugin.broadcast(SyncRecordSerializer.serialize(record))
            sent = true
        }

        // BLE GATT: broadcast to all connected BLE peers (iOS + Android without WiFi Aware)
        if (bleGattPlugin.isAvailable) {
            bleGattPlugin.broadcast(record)
            sent = true
        }

        // Nearby: broadcast to all connected peers (Android-to-Android)
        if (nearbyPlugin.isAvailable) {
            nearbyPlugin.broadcast(record)
            sent = true
        }

        // Firebase: push to RTDB (runs in parallel, doesn't block mesh)
        if (firebasePlugin.isAvailable) {
            try {
                if (firebasePlugin.broadcastToFirebase(record)) {
                    sent = true
                    sentViaFirebase = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Firebase send failed", e)
            }
        }

        return SendResult(
            accepted = sent,
            sentViaFirebase = sentViaFirebase
        )
    }

    /**
     * Sends raw Envelope bytes to all discovered WiFi Aware peers.
     * Used for 1:1 E2E encrypted messages.
     */
    fun sendEnvelopeToAll(envelopeBytes: ByteArray) {
        if (wifiAwarePlugin.isAvailable) {
            // Prefix with envelope type byte
            val prefixed = ByteArray(envelopeBytes.size + 1)
            prefixed[0] = 0x02 // PREFIX_ENVELOPE
            System.arraycopy(envelopeBytes, 0, prefixed, 1, envelopeBytes.size)
            wifiAwarePlugin.broadcast(prefixed)
        }
    }

    /**
     * Subscribe the Firebase plugin to a squad's RTDB records.
     * Call when the user creates or joins a squad.
     */
    fun subscribeFirebaseToSquad(squadId: String) {
        firebasePlugin.subscribeToSquad(squadId)
    }

    /**
     * Unsubscribe Firebase from a squad's RTDB records.
     */
    fun unsubscribeFirebaseFromSquad(squadId: String) {
        firebasePlugin.unsubscribeFromSquad(squadId)
    }
}
