package app.conectx.sync

import android.util.Log
import app.conectx.data.local.db.dao.MessageDao
import app.conectx.data.local.db.dao.SquadDao
import app.conectx.data.local.db.dao.SyncRecordDao
import app.conectx.data.local.db.entity.MessageEntity
import app.conectx.data.local.db.entity.SquadEntity
import app.conectx.data.local.db.entity.SyncRecordEntity
import app.conectx.domain.model.LocationPing
import app.conectx.domain.model.Peer
import app.conectx.domain.model.RecordType
import app.conectx.domain.model.Squad
import app.conectx.domain.model.SyncRecord
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.conectx.data.local.preferences.UserPreferences
import app.conectx.data.remote.firebase.FirebaseAuthSource
import app.conectx.transport.TransportManager
import app.conectx.transport.nearby.PeerTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core sync engine — the bridge between transport and storage.
 *
 * Responsibilities:
 * 1. INGEST:  Receive SyncRecords from the mesh → dedup → store in Room
 *             → update Lamport clock. CHAT records also produce a MessageEntity.
 *             SQUAD_META records create/update squads in Room.
 * 2. PRODUCE: Create SyncRecords from local user actions → assign clock →
 *             store → broadcast via TransportManager (or queue if offline).
 * 3. SYNC:    When a new peer connects, exchange SYNC_OFFERs so both sides
 *             fill any gaps caused by prior disconnection.
 *
 * The engine runs its own CoroutineScope, started/stopped by MeshService.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val transportManager: TransportManager,
    private val peerTracker: PeerTracker,
    private val syncRecordDao: SyncRecordDao,
    private val messageDao: MessageDao,
    private val squadDao: SquadDao,
    private val lamportClock: LamportClock,
    private val conflictResolver: ConflictResolver,
    private val messageQueue: MessageQueue,
    private val firebaseAuth: FirebaseAuthSource,
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private const val TAG = "SyncEngine"
    }

    private var scope: CoroutineScope? = null

    // Set by the app after activation / login
    var localUserId: String = "local-${UUID.randomUUID().toString().take(8)}"
    var localUserName: String = "Conectx User"

    // ── Lifecycle ────────────────────────────────────────────────────

    fun start() {
        if (scope != null) return
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = newScope

        newScope.launch { loadSavedIdentity() }
        newScope.launch { collectIncomingMessages() }
        newScope.launch { watchPeerConnections() }
        newScope.launch { initFirebase() }

        Log.d(TAG, "Started")
    }

    /**
     * Restores user identity from DataStore so it survives app restarts.
     * Falls back to the random default if not yet activated.
     */
    private suspend fun loadSavedIdentity() {
        val prefs = dataStore.data.first()
        prefs[UserPreferences.USER_ID]?.let { localUserId = it }
        prefs[UserPreferences.USERNAME]?.let {
            localUserName = it
            transportManager.nearbyPlugin.configure(it)
        }
        Log.d(TAG, "Identity loaded: $localUserName ($localUserId)")
    }

    /**
     * Signs into Firebase anonymously and subscribes to all known squads
     * so the RTDB transport can push/receive records while online.
     */
    private suspend fun initFirebase() {
        firebaseAuth.ensureSignedIn()

        // Subscribe Firebase to every squad in the local DB
        val squadIds = syncRecordDao.getAllSquadIds()
        for (id in squadIds) {
            transportManager.subscribeFirebaseToSquad(id)
        }
        Log.d(TAG, "Firebase: subscribed to ${squadIds.size} squad(s)")
    }

    fun stop() {
        scope?.cancel()
        scope = null
        Log.d(TAG, "Stopped")
    }

    // ── Public API: send messages ────────────────────────────────────

    suspend fun sendChat(squadId: String, text: String) {
        val clock = lamportClock.tick(squadId)
        val payload = PayloadCodec.encodeChat(localUserName, text)

        val record = SyncRecord(
            id = UUID.randomUUID().toString(),
            squadId = squadId,
            authorId = localUserId,
            lamportClock = clock,
            timestamp = System.currentTimeMillis(),
            type = RecordType.CHAT,
            payload = payload,
            signature = ByteArray(0)
        )

        persistRecord(record)
        sendOrQueue(record)
    }

    suspend fun sendLocationPing(squadId: String, ping: LocationPing) {
        val clock = lamportClock.tick(squadId)
        val payload = PayloadCodec.encodeLocation(localUserName, ping)

        val record = SyncRecord(
            id = UUID.randomUUID().toString(),
            squadId = squadId,
            authorId = localUserId,
            lamportClock = clock,
            timestamp = System.currentTimeMillis(),
            type = RecordType.LOCATION,
            payload = payload,
            signature = ByteArray(0)
        )

        persistRecord(record)
        sendOrQueue(record)
    }

    /**
     * Broadcasts a squad lifecycle event (create, join, leave) to the mesh.
     * Other devices receive this and update their local squad data.
     */
    suspend fun broadcastSquadMeta(
        action: PayloadCodec.SquadAction,
        squad: Squad
    ) {
        val clock = lamportClock.tick(squad.id)
        val payload = PayloadCodec.encodeSquadMeta(
            PayloadCodec.SquadMetaPayload(
                action = action,
                squadName = squad.name,
                inviteCode = squad.inviteCode,
                memberName = localUserName
            )
        )

        val record = SyncRecord(
            id = UUID.randomUUID().toString(),
            squadId = squad.id,
            authorId = localUserId,
            lamportClock = clock,
            timestamp = System.currentTimeMillis(),
            type = RecordType.SQUAD_META,
            payload = payload,
            signature = ByteArray(0)
        )

        persistRecord(record)
        sendOrQueue(record)

        // Keep Firebase transport in sync with squad membership
        when (action) {
            PayloadCodec.SquadAction.CREATE,
            PayloadCodec.SquadAction.JOIN -> transportManager.subscribeFirebaseToSquad(squad.id)
            PayloadCodec.SquadAction.LEAVE -> transportManager.unsubscribeFirebaseFromSquad(squad.id)
        }
    }

    // ── Incoming message processing ──────────────────────────────────

    private suspend fun collectIncomingMessages() {
        transportManager.incomingMessages().collect { record ->
            try {
                processIncoming(record)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing incoming record ${record.id}", e)
            }
        }
    }

    private suspend fun processIncoming(record: SyncRecord) {
        when (record.type) {
            RecordType.SYNC_OFFER -> handleSyncOffer(record)
            else -> handleDataRecord(record)
        }
    }

    private suspend fun handleSyncOffer(offer: SyncRecord) {
        if (offer.authorId == localUserId) return

        val session = SyncSession(
            peer = Peer(offer.authorId, offer.authorId, "", true, System.currentTimeMillis()),
            lamportClock = lamportClock,
            syncRecordDao = syncRecordDao,
            transportManager = transportManager,
            localUserId = localUserId
        )
        session.handleOffer(offer)
    }

    private suspend fun handleDataRecord(record: SyncRecord) {
        if (!conflictResolver.isNew(record)) return

        lamportClock.receive(record.squadId, record.lamportClock)
        persistRecord(record)

        Log.d(TAG, "Stored ${record.type} record ${record.id} (squad=${record.squadId}, clock=${record.lamportClock})")
    }

    // ── Peer connection watching ─────────────────────────────────────

    private suspend fun watchPeerConnections() {
        var previousIds = emptySet<String>()

        peerTracker.connectedPeers
            .map { peers -> peers.map { it.id }.toSet() }
            .distinctUntilChanged()
            .collect { currentIds ->
                val newIds = currentIds - previousIds
                previousIds = currentIds

                if (newIds.isNotEmpty()) {
                    Log.d(TAG, "New peers connected: $newIds")
                    flushQueue()
                    sendSyncOffer()
                }
            }
    }

    private suspend fun sendSyncOffer() {
        val session = SyncSession(
            peer = Peer("mesh", "mesh", "", true, System.currentTimeMillis()),
            lamportClock = lamportClock,
            syncRecordDao = syncRecordDao,
            transportManager = transportManager,
            localUserId = localUserId
        )
        session.sendOffer()
    }

    private suspend fun flushQueue() {
        if (!messageQueue.hasPending) return
        val queued = messageQueue.drainAll()
        Log.d(TAG, "Flushing ${queued.size} queued records")
        for (record in queued) {
            if (!transportManager.send(record)) {
                messageQueue.enqueue(record)
                break
            }
        }
    }

    // ── Send / Queue ─────────────────────────────────────────────────

    private suspend fun sendOrQueue(record: SyncRecord) {
        if (!transportManager.send(record)) {
            messageQueue.enqueue(record)
            Log.d(TAG, "No transport — ${record.type} queued")
        }
    }

    // ── Persistence ──────────────────────────────────────────────────

    private suspend fun persistRecord(record: SyncRecord) {
        syncRecordDao.insert(record.toEntity())

        when (record.type) {
            RecordType.CHAT -> persistChatMessage(record)
            RecordType.SQUAD_META -> persistSquadMeta(record)
            else -> { /* LOCATION, PING stored as sync records only */ }
        }
    }

    private suspend fun persistChatMessage(record: SyncRecord) {
        val chat = PayloadCodec.decodeChat(record.payload)
        messageDao.insert(
            MessageEntity(
                id = record.id,
                squadId = record.squadId,
                authorId = record.authorId,
                authorName = chat.authorName,
                text = chat.text,
                lamportClock = record.lamportClock,
                timestamp = record.timestamp
            )
        )
    }

    /**
     * Applies a SQUAD_META record to the local squads table.
     * This is how devices learn about squads created on other devices.
     */
    private suspend fun persistSquadMeta(record: SyncRecord) {
        val meta = PayloadCodec.decodeSquadMeta(record.payload)

        when (meta.action) {
            PayloadCodec.SquadAction.CREATE -> {
                // Only insert if we don't already have this squad
                if (squadDao.getById(record.squadId) == null) {
                    squadDao.insert(
                        SquadEntity(
                            id = record.squadId,
                            name = meta.squadName,
                            inviteCode = meta.inviteCode,
                            memberIds = record.authorId,
                            createdAt = record.timestamp
                        )
                    )
                    Log.d(TAG, "Learned about squad '${meta.squadName}' (${meta.inviteCode}) via mesh")
                }
            }

            PayloadCodec.SquadAction.JOIN -> {
                val entity = squadDao.getById(record.squadId) ?: return
                val members = entity.memberIds.split(",").filter { it.isNotBlank() }
                if (record.authorId !in members) {
                    val updated = (members + record.authorId).joinToString(",")
                    squadDao.insert(entity.copy(memberIds = updated))
                    Log.d(TAG, "${meta.memberName} joined squad ${record.squadId}")
                }
            }

            PayloadCodec.SquadAction.LEAVE -> {
                val entity = squadDao.getById(record.squadId) ?: return
                val members = entity.memberIds.split(",").filter { it.isNotBlank() }
                if (record.authorId in members) {
                    val updated = (members - record.authorId).joinToString(",")
                    squadDao.insert(entity.copy(memberIds = updated))
                    Log.d(TAG, "${meta.memberName} left squad ${record.squadId}")
                }
            }
        }
    }

    private fun SyncRecord.toEntity() = SyncRecordEntity(
        id = id,
        squadId = squadId,
        authorId = authorId,
        lamportClock = lamportClock,
        timestamp = timestamp,
        type = type.name,
        payload = payload,
        signature = signature
    )
}
