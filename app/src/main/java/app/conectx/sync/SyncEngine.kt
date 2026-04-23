package app.conectx.sync

import android.util.Log
import app.conectx.data.local.db.dao.MessageDao
import app.conectx.data.local.db.dao.SquadDao
import app.conectx.data.local.db.dao.SyncRecordDao
import app.conectx.data.local.db.entity.MessageEntity
import app.conectx.data.local.db.entity.SquadEntity
import app.conectx.data.local.db.entity.SyncRecordEntity
import app.conectx.domain.model.Peer
import app.conectx.domain.model.RecordType
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
 * Core sync engine — bridge between transport and storage.
 *
 * Handles squad-based group chat: SyncRecords relayed via all available
 * transports (WiFi Aware, BLE, Nearby, Firebase). Dedup at the record-UUID
 * level (MeshRouter + ConflictResolver).
 *
 * Record signatures are empty bytes in v1 — signature verification is
 * log-only. Trust is at the squad-invite layer.
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

    private suspend fun loadSavedIdentity() {
        val prefs = dataStore.data.first()
        prefs[UserPreferences.USER_ID]?.let { localUserId = it }
        prefs[UserPreferences.USERNAME]?.let {
            localUserName = it
            transportManager.nearbyPlugin.configure(it)
            transportManager.bleGattPlugin.configure(it)
        }
        Log.d(TAG, "Identity loaded: $localUserName ($localUserId)")
    }

    private suspend fun initFirebase() {
        firebaseAuth.ensureSignedIn()
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

    // ── Public API: squad-based mesh messaging ────────────────────────

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

    suspend fun broadcastSquadMeta(
        action: PayloadCodec.SquadAction,
        squad: app.conectx.domain.model.Squad
    ) {
        // Firebase records are writable only after this device is registered
        // as a squad member, so register membership before JOIN broadcasts.
        when (action) {
            PayloadCodec.SquadAction.CREATE,
            PayloadCodec.SquadAction.JOIN -> transportManager.subscribeFirebaseToSquad(squad.id)
            PayloadCodec.SquadAction.LEAVE -> transportManager.unsubscribeFirebaseFromSquad(squad.id)
        }

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
    }

    // ── Incoming mesh message processing ──────────────────────────────

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
            val result = transportManager.send(record)
            if (result.sentViaFirebase) {
                syncRecordDao.markFirebaseSynced(record.id, System.currentTimeMillis())
            }
            if (!result.accepted) {
                messageQueue.enqueue(record)
                break
            }
        }
    }

    // ── Send / Queue ─────────────────────────────────────────────────

    private suspend fun sendOrQueue(record: SyncRecord) {
        val result = transportManager.send(record)
        if (result.sentViaFirebase) {
            syncRecordDao.markFirebaseSynced(record.id, System.currentTimeMillis())
        }
        if (!result.accepted) {
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
            RecordType.SYNC_OFFER -> { /* offers not persisted as messages */ }
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

    private suspend fun persistSquadMeta(record: SyncRecord) {
        val meta = PayloadCodec.decodeSquadMeta(record.payload)

        when (meta.action) {
            PayloadCodec.SquadAction.CREATE -> {
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
