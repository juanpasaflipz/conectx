package app.conectx.transport.firebase

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import app.conectx.data.remote.firebase.FirebaseAuthSource
import app.conectx.domain.model.Peer
import app.conectx.domain.model.RecordType
import app.conectx.domain.model.SyncRecord
import app.conectx.transport.Connection
import app.conectx.transport.TransportPlugin
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase Realtime Database transport — fallback when internet is available.
 *
 * RTDB structure:
 *   /squads/{squadId}/records/{recordId} → {
 *     id, squadId, authorId, lamportClock, timestamp, type,
 *     payload (Base64), signature (Base64)
 *   }
 *
 * Each squad the user belongs to gets a child event listener.
 * Incoming records are emitted via the onMessageReceived() Flow.
 * The SyncEngine's ConflictResolver handles dedup.
 *
 * discoverPeers() and connectToPeer() are no-ops — Firebase doesn't
 * use peer-to-peer connections.
 */
@Singleton
class FirebasePlugin @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: FirebaseDatabase,
    private val authSource: FirebaseAuthSource
) : TransportPlugin {

    companion object {
        private const val TAG = "FirebasePlugin"
        private const val SQUADS_REF = "squads"
        private const val RECORDS_REF = "records"
    }

    private val _receivedMessages = MutableSharedFlow<SyncRecord>(extraBufferCapacity = 256)
    private var isRunning = false

    // Track active listeners so we can detach on stop()
    private val activeListeners = ConcurrentHashMap<String, ChildEventListener>()

    // Squad IDs this device is a member of — set externally before start()
    private val subscribedSquads = ConcurrentHashMap.newKeySet<String>()

    override val isAvailable: Boolean
        get() = isRunning && hasInternet()

    override suspend fun start() {
        if (isRunning) return

        // Firebase transport needs auth
        val result = authSource.ensureSignedIn()
        if (result.isFailure) {
            Log.w(TAG, "Cannot start — auth failed: ${result.exceptionOrNull()?.message}")
            return
        }

        isRunning = true

        // Attach listeners for all known squads
        for (squadId in subscribedSquads) {
            attachListener(squadId)
        }

        Log.d(TAG, "Started — listening to ${subscribedSquads.size} squad(s)")
    }

    override suspend fun stop() {
        if (!isRunning) return
        isRunning = false

        // Detach all RTDB listeners
        for ((squadId, listener) in activeListeners) {
            database.reference
                .child(SQUADS_REF)
                .child(squadId)
                .child(RECORDS_REF)
                .removeEventListener(listener)
        }
        activeListeners.clear()

        Log.d(TAG, "Stopped — all listeners detached")
    }

    override suspend fun discoverPeers(): Flow<Peer> = emptyFlow()

    override suspend fun connectToPeer(peer: Peer): Connection {
        // Firebase doesn't use per-peer connections
        return Connection(peer = peer, endpointId = "firebase")
    }

    override suspend fun sendMessage(connection: Connection, record: SyncRecord) {
        broadcastToFirebase(record)
    }

    override fun onMessageReceived(): Flow<SyncRecord> = _receivedMessages.asSharedFlow()

    // ── Public API ──────────────────────────────────────────────────

    /**
     * Subscribe to a squad's RTDB records. Call when the user joins a squad.
     * If the plugin is already running, immediately attaches a listener.
     */
    fun subscribeToSquad(squadId: String) {
        subscribedSquads.add(squadId)
        if (isRunning) {
            attachListener(squadId)
        }
    }

    /**
     * Unsubscribe from a squad. Detaches the listener if running.
     */
    fun unsubscribeFromSquad(squadId: String) {
        subscribedSquads.remove(squadId)
        detachListener(squadId)
    }

    /**
     * Push a SyncRecord to Firebase RTDB. Called by TransportManager
     * when Nearby isn't available, or by SyncEngine for squad metadata
     * that should be available pre-match.
     */
    suspend fun broadcastToFirebase(record: SyncRecord) {
        if (!hasInternet()) return

        try {
            val ref = database.reference
                .child(SQUADS_REF)
                .child(record.squadId)
                .child(RECORDS_REF)
                .child(record.id)

            val data = mapOf(
                "id" to record.id,
                "squadId" to record.squadId,
                "authorId" to record.authorId,
                "lamportClock" to record.lamportClock,
                "timestamp" to record.timestamp,
                "type" to record.type.name,
                "payload" to android.util.Base64.encodeToString(record.payload, android.util.Base64.NO_WRAP),
                "signature" to android.util.Base64.encodeToString(record.signature, android.util.Base64.NO_WRAP)
            )

            ref.setValue(data).await()
            Log.d(TAG, "Pushed ${record.type} record ${record.id} to RTDB")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push record ${record.id}", e)
        }
    }

    // ── Internal: RTDB listeners ────────────────────────────────────

    private fun attachListener(squadId: String) {
        if (activeListeners.containsKey(squadId)) return

        val ref = database.reference
            .child(SQUADS_REF)
            .child(squadId)
            .child(RECORDS_REF)

        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val record = snapshotToRecord(snapshot) ?: return
                // Don't emit our own records back — dedup handles it but
                // we can skip the work here
                if (record.authorId == authSource.uid) return
                _receivedMessages.tryEmit(record)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Listener for $squadId cancelled: ${error.message}")
            }
        }

        ref.addChildEventListener(listener)
        activeListeners[squadId] = listener
        Log.d(TAG, "Attached listener for squad $squadId")
    }

    private fun detachListener(squadId: String) {
        val listener = activeListeners.remove(squadId) ?: return
        database.reference
            .child(SQUADS_REF)
            .child(squadId)
            .child(RECORDS_REF)
            .removeEventListener(listener)
        Log.d(TAG, "Detached listener for squad $squadId")
    }

    private fun snapshotToRecord(snapshot: DataSnapshot): SyncRecord? {
        return try {
            SyncRecord(
                id = snapshot.child("id").getValue(String::class.java) ?: return null,
                squadId = snapshot.child("squadId").getValue(String::class.java) ?: return null,
                authorId = snapshot.child("authorId").getValue(String::class.java) ?: return null,
                lamportClock = snapshot.child("lamportClock").getValue(Long::class.java) ?: 0L,
                timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L,
                type = RecordType.valueOf(
                    snapshot.child("type").getValue(String::class.java) ?: return null
                ),
                payload = android.util.Base64.decode(
                    snapshot.child("payload").getValue(String::class.java) ?: "",
                    android.util.Base64.NO_WRAP
                ),
                signature = android.util.Base64.decode(
                    snapshot.child("signature").getValue(String::class.java) ?: "",
                    android.util.Base64.NO_WRAP
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse record from snapshot ${snapshot.key}", e)
            null
        }
    }

    // ── Internal: Connectivity check ────────────────────────────────

    private fun hasInternet(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
