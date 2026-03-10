package app.conectx.transport

import android.util.Log
import app.conectx.domain.model.SyncRecord
import app.conectx.transport.firebase.FirebasePlugin
import app.conectx.transport.nearby.NearbyPlugin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates transport selection and lifecycle.
 *
 * Priority order (first available wins for sending):
 * 1. Nearby Connections (BT + WiFi mesh) — primary for stadium use
 * 2. Firebase Realtime Database — fallback when internet is available
 *
 * All transports run simultaneously so messages arrive via whichever
 * path is fastest. Dedup happens in the sync layer.
 */
@Singleton
class TransportManager @Inject constructor(
    val nearbyPlugin: NearbyPlugin,
    val firebasePlugin: FirebasePlugin
) {
    companion object {
        private const val TAG = "TransportManager"
    }

    private val plugins: List<TransportPlugin> = listOf(nearbyPlugin, firebasePlugin)

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
     * Nearby broadcasts to all connected peers via mesh.
     * Firebase pushes to RTDB for online squad members.
     * Returns true if at least one transport accepted the record.
     */
    suspend fun send(record: SyncRecord): Boolean {
        var sent = false

        // Nearby: broadcast to all connected peers
        if (nearbyPlugin.isAvailable) {
            nearbyPlugin.broadcast(record)
            sent = true
        }

        // Firebase: push to RTDB (runs in parallel, doesn't block Nearby)
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
