package app.conectx.sync

import android.util.Log
import app.conectx.data.local.db.dao.SyncRecordDao
import app.conectx.data.local.db.entity.SyncRecordEntity
import app.conectx.domain.model.Peer
import app.conectx.domain.model.RecordType
import app.conectx.domain.model.SyncRecord
import app.conectx.transport.TransportManager
import java.util.UUID

/**
 * Represents a sync handshake with a single peer.
 *
 * When a new peer connects, the SyncEngine creates a session and calls
 * [sendOffer]. This broadcasts a SYNC_OFFER containing our latest
 * Lamport clock for every squad we know about, plus our Ed25519 public
 * key so peers can verify our future messages.
 *
 * This class is NOT a singleton — one instance per peer connection.
 */
class SyncSession(
    private val peer: Peer,
    private val lamportClock: LamportClock,
    private val syncRecordDao: SyncRecordDao,
    private val transportManager: TransportManager,
    private val localUserId: String,
    private val cryptoManager: CryptoManager? = null
) {
    companion object {
        private const val TAG = "SyncSession"
    }

    /**
     * Sends a SYNC_OFFER to the mesh. Our latest clock per squad is
     * encoded in the payload so peers know what we already have.
     * Our Ed25519 public key is included so peers can verify our signatures.
     */
    suspend fun sendOffer() {
        val squadClocks = lamportClock.allClocks()
        if (squadClocks.isEmpty()) {
            Log.d(TAG, "No squads to sync — skipping offer to ${peer.displayName}")
            return
        }

        val publicKey = cryptoManager?.getPublicKeyBytes() ?: ByteArray(0)

        val offer = SyncRecord(
            id = UUID.randomUUID().toString(),
            squadId = "_sync",       // special: not tied to one squad
            authorId = localUserId,
            lamportClock = 0,        // not meaningful for offers
            timestamp = System.currentTimeMillis(),
            type = RecordType.SYNC_OFFER,
            payload = PayloadCodec.encodeSyncOffer(squadClocks, publicKey),
            signature = ByteArray(0) // SYNC_OFFER itself doesn't need signing
        )

        transportManager.send(offer)
        Log.d(TAG, "Sent SYNC_OFFER to mesh (${squadClocks.size} squads, pubkey=${publicKey.size}B) for ${peer.displayName}")
    }

    /**
     * Handles a received SYNC_OFFER. For each squad where the remote peer's
     * clock is behind ours, we broadcast the records they're missing.
     */
    suspend fun handleOffer(offer: SyncRecord) {
        val offerPayload = PayloadCodec.decodeSyncOffer(offer.payload)
        val remoteClocks = offerPayload.squadClocks
        Log.d(TAG, "Received SYNC_OFFER from ${offer.authorId}: $remoteClocks")

        for ((squadId, remoteClock) in remoteClocks) {
            val localClock = lamportClock.current(squadId)
            if (localClock > remoteClock) {
                // We have records they don't — send them
                val missing = syncRecordDao.getRecordsAfter(squadId, remoteClock)
                Log.d(TAG, "Sending ${missing.size} missing records for squad $squadId (their clock=$remoteClock, ours=$localClock)")
                for (entity in missing) {
                    val record = entity.toDomain()
                    transportManager.send(record)
                }
            }
        }
    }

    private fun SyncRecordEntity.toDomain() = SyncRecord(
        id = id,
        squadId = squadId,
        authorId = authorId,
        lamportClock = lamportClock,
        timestamp = timestamp,
        type = RecordType.valueOf(type),
        payload = payload,
        signature = signature
    )
}
