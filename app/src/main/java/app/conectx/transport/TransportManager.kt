package app.conectx.transport

import android.util.Log
import app.conectx.domain.model.SyncRecord
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
 * Priority order (first available wins for sending):
 * 1. WiFi Aware — cross-platform (Android + iOS), primary for interop
 * 2. Nearby Connections (BT + WiFi mesh) — Android-to-Android fallback
 * 3. Firebase Realtime Database — internet fallback
 *
 * All transports run simultaneously so messages arrive via whichever
 * path is fastest. Dedup happens in the sync layer.
 */
@Singleton
class TransportManager @Inject constructor(
    val nearbyPlugin: NearbyPlugin,
    val firebasePlugin: FirebasePlugin,
    val wifiAwarePlugin: WifiAwarePlugin
) {
    companion object {
        private const val TAG = "TransportManager"
    }

    private val plugins: List<TransportPlugin> = listOf(wifiAwarePlugin, nearbyPlugin, firebasePlugin)

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
     * WiFi Aware broadcasts to discovered peers (cross-platform).
     * Nearby broadcasts to all connected peers via mesh.
     * Firebase pushes to RTDB for online squad members.
     * Returns true if at least one transport accepted the record.
     */
    suspend fun send(record: SyncRecord): Boolean {
        var sent = false

        // WiFi Aware: broadcast to all discovered peers (cross-platform primary)
        if (wifiAwarePlugin.isAvailable) {
            wifiAwarePlugin.broadcast(SyncRecordSerializer.serialize(record))
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
                firebasePlugin.broadcastToFirebase(record)
                sent = true
            } catch (e: Exception) {
                Log.e(TAG, "Firebase send failed", e)
            }
        }

        return sent
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
