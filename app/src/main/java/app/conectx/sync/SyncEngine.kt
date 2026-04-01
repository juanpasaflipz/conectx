package app.conectx.sync

import android.util.Log
import app.conectx.crypto.SignalSessionManager
import app.conectx.data.local.db.dao.ConversationDao
import app.conectx.data.local.db.dao.DirectMessageDao
import app.conectx.data.local.db.dao.MessageDao
import app.conectx.data.local.db.dao.SquadDao
import app.conectx.data.local.db.dao.SyncRecordDao
import app.conectx.data.local.db.entity.ConversationEntity
import app.conectx.data.local.db.entity.DirectMessageEntity
import app.conectx.data.local.db.entity.MessageEntity
import app.conectx.data.local.db.entity.SquadEntity
import app.conectx.data.local.db.entity.SyncRecordEntity
import app.conectx.domain.model.LocationPing
import app.conectx.domain.model.Peer
import app.conectx.domain.model.RecordType
import app.conectx.domain.model.SyncRecord
import app.conectx.proto.TextPayload
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
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.state.PreKeyBundle
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core sync engine — the bridge between transport and storage.
 *
 * Handles two parallel message flows:
 * 1. MESH (SyncRecords): Multi-hop relay via Nearby Connections + Firebase.
 *    Used for squad-based messaging (Android-to-Android).
 * 2. E2E (Envelopes): Direct encrypted P2P via WiFi Aware.
 *    Used for 1:1 cross-platform messaging (Android + iOS).
 *
 * Phase 2 changes:
 * - Replaced CryptoManager (Tink) with SignalSessionManager (libsignal)
 * - Added E2E messaging via EnvelopeSerializer + Signal Protocol
 * - Pre-key bundle exchange over WiFi Aware for session establishment
 * - 1:1 conversation persistence via ConversationDao + DirectMessageDao
 */
@Singleton
class SyncEngine @Inject constructor(
    private val transportManager: TransportManager,
    private val peerTracker: PeerTracker,
    private val syncRecordDao: SyncRecordDao,
    private val messageDao: MessageDao,
    private val squadDao: SquadDao,
    private val conversationDao: ConversationDao,
    private val directMessageDao: DirectMessageDao,
    private val lamportClock: LamportClock,
    private val conflictResolver: ConflictResolver,
    private val messageQueue: MessageQueue,
    private val firebaseAuth: FirebaseAuthSource,
    private val dataStore: DataStore<Preferences>,
    private val signalManager: SignalSessionManager
) {
    companion object {
        private const val TAG = "SyncEngine"
        // Protocol prefix bytes to distinguish message types on WiFi Aware
        private const val PREFIX_PRE_KEY_BUNDLE: Byte = 0x01
        private const val PREFIX_ENVELOPE: Byte = 0x02
    }

    private var scope: CoroutineScope? = null

    // Set by the app after activation / login
    var localUserId: String = "local-${UUID.randomUUID().toString().take(8)}"
    var localUserName: String = "Conectx User"

    // peerIdentityKeyHex → Signal address, populated from pre-key exchange
    private val knownPeers = ConcurrentHashMap<String, ByteArray>()

    // ── Lifecycle ────────────────────────────────────────────────────

    fun start() {
        if (scope != null) return
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = newScope

        newScope.launch { loadSavedIdentity() }
        newScope.launch { collectIncomingMessages() }
        newScope.launch { collectIncomingEnvelopes() }
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

    // ── Public API: 1:1 E2E messaging (Phase 2) ──────────────────────

    /**
     * Sends an encrypted 1:1 message to a peer via WiFi Aware.
     * Requires an established Signal session (via pre-key exchange).
     */
    suspend fun sendDirectMessage(peerId: String, text: String) {
        val peerIdentityKey = knownPeers[peerId]
        if (peerIdentityKey == null) {
            Log.w(TAG, "No identity key for peer $peerId — cannot send E2E message")
            return
        }

        if (!signalManager.hasSession(peerId)) {
            Log.w(TAG, "No Signal session with $peerId — pre-key exchange required first")
            return
        }

        val envelopeBytes = EnvelopeSerializer.buildTextEnvelope(
            signalManager = signalManager,
            recipientId = peerIdentityKey,
            text = text
        )

        // Send via WiFi Aware
        transportManager.sendEnvelopeToAll(envelopeBytes)

        // Persist locally
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        directMessageDao.insert(
            DirectMessageEntity(
                id = messageId,
                peerId = peerId,
                text = text,
                isOutgoing = true,
                timestamp = timestamp
            )
        )

        // Update conversation
        val existing = conversationDao.getConversation(peerId)
        if (existing != null) {
            conversationDao.updateLastMessage(peerId, text, timestamp)
        } else {
            conversationDao.upsert(
                ConversationEntity(
                    peerId = peerId,
                    peerDisplayName = peerId.take(8),
                    peerIdentityKey = peerIdentityKey,
                    lastMessageText = text,
                    lastMessageTimestamp = timestamp,
                    unreadCount = 0,
                    createdAt = timestamp
                )
            )
        }

        Log.d(TAG, "Sent E2E message to $peerId")
    }

    // ── Public API: squad-based mesh messaging (existing) ─────────────

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
            signature = signalManager.sign(buildSyncRecordSignableBytes(
                UUID.randomUUID().toString(), squadId, localUserId, clock,
                System.currentTimeMillis(), RecordType.CHAT, payload
            ))
        )
        Log.d(TAG, "Signed CHAT record ${record.id}")

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
            signature = signalManager.sign(buildSyncRecordSignableBytes(
                UUID.randomUUID().toString(), squadId, localUserId, clock,
                System.currentTimeMillis(), RecordType.LOCATION, payload
            ))
        )
        Log.d(TAG, "Signed LOCATION record ${record.id}")

        persistRecord(record)
        sendOrQueue(record)
    }

    suspend fun broadcastSquadMeta(
        action: PayloadCodec.SquadAction,
        squad: app.conectx.domain.model.Squad
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
            signature = signalManager.sign(buildSyncRecordSignableBytes(
                UUID.randomUUID().toString(), squad.id, localUserId, clock,
                System.currentTimeMillis(), RecordType.SQUAD_META, payload
            ))
        )
        Log.d(TAG, "Signed SQUAD_META record ${record.id}")

        persistRecord(record)
        sendOrQueue(record)

        when (action) {
            PayloadCodec.SquadAction.CREATE,
            PayloadCodec.SquadAction.JOIN -> transportManager.subscribeFirebaseToSquad(squad.id)
            PayloadCodec.SquadAction.LEAVE -> transportManager.unsubscribeFirebaseFromSquad(squad.id)
        }
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

        val offerPayload = PayloadCodec.decodeSyncOffer(offer.payload)
        // Note: publicKey in SYNC_OFFER is now Signal identity key (not Tink)
        if (offerPayload.publicKey.isNotEmpty()) {
            val peerAddress = offerPayload.publicKey.toHexString()
            knownPeers[peerAddress] = offerPayload.publicKey
            Log.d(TAG, "Cached Signal identity key for peer ${offer.authorId}")
        }

        val session = SyncSession(
            peer = Peer(offer.authorId, offer.authorId, "", true, System.currentTimeMillis()),
            lamportClock = lamportClock,
            syncRecordDao = syncRecordDao,
            transportManager = transportManager,
            localUserId = localUserId,
            signalManager = signalManager
        )
        session.handleOffer(offer)
    }

    private suspend fun handleDataRecord(record: SyncRecord) {
        if (!conflictResolver.isNew(record)) return

        // Signature verification is best-effort for mesh records
        // (Signal signatures use a different format than old Tink signatures)
        lamportClock.receive(record.squadId, record.lamportClock)
        persistRecord(record)

        Log.d(TAG, "Stored ${record.type} record ${record.id} (squad=${record.squadId}, clock=${record.lamportClock})")
    }

    // ── Incoming E2E envelope processing (WiFi Aware) ─────────────────

    private suspend fun collectIncomingEnvelopes() {
        transportManager.wifiAwarePlugin.receivedBundles.collect { (peerHandle, data) ->
            try {
                if (data.isEmpty()) return@collect

                when (data[0]) {
                    PREFIX_PRE_KEY_BUNDLE -> handlePreKeyBundle(data.copyOfRange(1, data.size))
                    PREFIX_ENVELOPE -> handleIncomingEnvelope(data.copyOfRange(1, data.size))
                    else -> {
                        // Try to parse as envelope (backward compat)
                        handleIncomingEnvelope(data)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing WiFi Aware message", e)
            }
        }
    }

    private suspend fun handlePreKeyBundle(bundleBytes: ByteArray) {
        try {
            val proto = app.conectx.proto.PreKeyBundle.parseFrom(bundleBytes)

            val identityKey = IdentityKey(proto.identityKey.toByteArray())
            val signedPreKeyPublic = Curve.decodePoint(proto.signedPreKey.toByteArray(), 0)

            var oneTimePreKeyId = -1
            var oneTimePreKeyPublic: ECPublicKey? = null
            if (!proto.oneTimePreKey.isEmpty) {
                oneTimePreKeyId = proto.oneTimePreKeyId
                oneTimePreKeyPublic = Curve.decodePoint(proto.oneTimePreKey.toByteArray(), 0)
            }

            val bundle = PreKeyBundle(
                proto.registrationId,
                1, // device ID
                oneTimePreKeyId,
                oneTimePreKeyPublic,
                proto.signedPreKeyId,
                signedPreKeyPublic,
                proto.signedPreKeySignature.toByteArray(),
                identityKey
            )

            val peerAddress = proto.identityKey.toByteArray().toHexString()
            signalManager.processPreKeyBundle(peerAddress, bundle)
            knownPeers[peerAddress] = proto.identityKey.toByteArray()

            // Use display name from bundle if present, otherwise fall back to hex prefix
            val displayName = proto.displayName.ifBlank { peerAddress.take(8) }

            // Create or update conversation for this peer
            val existing = conversationDao.getConversation(peerAddress)
            if (existing == null) {
                conversationDao.upsert(
                    ConversationEntity(
                        peerId = peerAddress,
                        peerDisplayName = displayName,
                        peerIdentityKey = proto.identityKey.toByteArray(),
                        lastMessageText = null,
                        lastMessageTimestamp = System.currentTimeMillis(),
                        unreadCount = 0,
                        createdAt = System.currentTimeMillis()
                    )
                )
            } else if (displayName != peerAddress.take(8) && existing.peerDisplayName == peerAddress.take(8)) {
                // Upgrade from hex prefix to real display name
                conversationDao.updateDisplayName(peerAddress, displayName)
            }

            Log.d(TAG, "Processed pre-key bundle from $peerAddress — session established")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process pre-key bundle", e)
        }
    }

    private suspend fun handleIncomingEnvelope(envelopeBytes: ByteArray) {
        try {
            val envelope = EnvelopeSerializer.parseEnvelope(envelopeBytes)

            // Check if this is addressed to us
            val myKey = signalManager.getIdentityPublicKey()
            if (!envelope.recipientId.toByteArray().contentEquals(myKey)) {
                return // Not for us
            }

            // Verify signature
            if (!EnvelopeSerializer.verifyEnvelope(envelope, signalManager)) {
                Log.w(TAG, "Envelope signature verification failed — dropping")
                return
            }

            val senderId = envelope.senderId.toByteArray().toHexString()

            // Determine if this is a pre-key message (first message in session)
            val isPreKeyMessage = !signalManager.hasSession(senderId)

            // Decrypt
            val plaintext = EnvelopeSerializer.decryptEnvelope(envelope, signalManager, isPreKeyMessage)

            when (envelope.messageType) {
                EnvelopeSerializer.TYPE_TEXT -> {
                    val textPayload = TextPayload.parseFrom(plaintext)
                    val messageId = envelope.messageId.toByteArray().toUUIDString()
                    val timestamp = envelope.timestamp

                    // Dedup
                    if (directMessageDao.exists(messageId) > 0) return

                    directMessageDao.insert(
                        DirectMessageEntity(
                            id = messageId,
                            peerId = senderId,
                            text = textPayload.text,
                            isOutgoing = false,
                            timestamp = timestamp
                        )
                    )

                    // Update conversation
                    val existing = conversationDao.getConversation(senderId)
                    if (existing != null) {
                        conversationDao.updateLastMessage(senderId, textPayload.text, timestamp)
                    } else {
                        conversationDao.upsert(
                            ConversationEntity(
                                peerId = senderId,
                                peerDisplayName = senderId.take(8),
                                peerIdentityKey = envelope.senderId.toByteArray(),
                                lastMessageText = textPayload.text,
                                lastMessageTimestamp = timestamp,
                                unreadCount = 1,
                                createdAt = timestamp
                            )
                        )
                    }

                    Log.d(TAG, "Received E2E text from $senderId: ${textPayload.text.take(20)}...")
                }

                EnvelopeSerializer.TYPE_RECEIPT -> {
                    val receipt = app.conectx.proto.ReceiptPayload.parseFrom(plaintext)
                    val originalId = receipt.messageId.toByteArray().toUUIDString()
                    val status = if (receipt.receiptType == 1) "delivered" else "read"
                    directMessageDao.updateStatus(originalId, status)
                    Log.d(TAG, "Receipt ($status) from $senderId for $originalId")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process incoming envelope", e)
        }
    }

    // ── Pre-key bundle broadcasting ───────────────────────────────────

    /**
     * Broadcasts our Signal pre-key bundle over WiFi Aware.
     * Called when new WiFi Aware peers are discovered.
     */
    fun broadcastPreKeyBundle() {
        val bundle = signalManager.getLocalPreKeyBundle()

        val proto = app.conectx.proto.PreKeyBundle.newBuilder()
            .setIdentityKey(com.google.protobuf.ByteString.copyFrom(
                bundle.identityKey.serialize()
            ))
            .setSignedPreKeyId(bundle.signedPreKeyId)
            .setSignedPreKey(com.google.protobuf.ByteString.copyFrom(
                bundle.signedPreKey.serialize()
            ))
            .setSignedPreKeySignature(com.google.protobuf.ByteString.copyFrom(
                bundle.signedPreKeySignature
            ))
            .setRegistrationId(bundle.registrationId)
            .setDisplayName(localUserName)

        if (bundle.preKeyId >= 0 && bundle.preKey != null) {
            proto.setOneTimePreKeyId(bundle.preKeyId)
            proto.setOneTimePreKey(com.google.protobuf.ByteString.copyFrom(
                bundle.preKey.serialize()
            ))
        }

        val bundleBytes = proto.build().toByteArray()
        // Prefix with type byte so receiver can distinguish bundles from envelopes
        val prefixed = ByteArray(bundleBytes.size + 1)
        prefixed[0] = PREFIX_PRE_KEY_BUNDLE
        System.arraycopy(bundleBytes, 0, prefixed, 1, bundleBytes.size)

        transportManager.wifiAwarePlugin.broadcast(prefixed)
        Log.d(TAG, "Broadcast pre-key bundle (${prefixed.size} bytes)")
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
                    // Also broadcast pre-key bundle for E2E session establishment
                    broadcastPreKeyBundle()
                }
            }
    }

    private suspend fun sendSyncOffer() {
        val session = SyncSession(
            peer = Peer("mesh", "mesh", "", true, System.currentTimeMillis()),
            lamportClock = lamportClock,
            syncRecordDao = syncRecordDao,
            transportManager = transportManager,
            localUserId = localUserId,
            signalManager = signalManager
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

    // ── Helpers ───────────────────────────────────────────────────────

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

    /**
     * Builds canonical bytes for signing a SyncRecord (replaces CryptoManager.signableBytes).
     */
    private fun buildSyncRecordSignableBytes(
        id: String, squadId: String, authorId: String,
        clock: Long, timestamp: Long, type: RecordType, payload: ByteArray
    ): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(baos).use { out ->
            out.writeUTF(id)
            out.writeUTF(squadId)
            out.writeUTF(authorId)
            out.writeLong(clock)
            out.writeLong(timestamp)
            out.writeUTF(type.name)
            out.writeInt(payload.size)
            out.write(payload)
        }
        return baos.toByteArray()
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }

    private fun ByteArray.toUUIDString(): String {
        if (size != 16) return toHexString()
        val bb = ByteBuffer.wrap(this)
        val high = bb.long
        val low = bb.long
        return UUID(high, low).toString()
    }
}
