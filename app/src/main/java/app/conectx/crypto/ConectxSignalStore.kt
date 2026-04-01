package app.conectx.crypto

import android.util.Log
import app.conectx.data.local.db.dao.SignalDao
import app.conectx.data.local.db.entity.SignalIdentityEntity
import app.conectx.data.local.db.entity.SignalPreKeyEntity
import app.conectx.data.local.db.entity.SignalSessionEntity
import app.conectx.data.local.db.entity.SignalSignedPreKeyEntity
import kotlinx.coroutines.runBlocking
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyStore
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SessionStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyStore
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements all four Signal Protocol storage interfaces backed by Room.
 *
 * libsignal's Java API is synchronous, so we use [runBlocking] to bridge
 * to Room's suspend functions. This is safe because libsignal calls these
 * from its own threads (not the main thread).
 */
@Singleton
class ConectxSignalStore @Inject constructor(
    private val signalDao: SignalDao
) : IdentityKeyStore, PreKeyStore, SignedPreKeyStore, SessionStore {

    companion object {
        private const val TAG = "ConectxSignalStore"
    }

    // Set during initialization by SignalSessionManager
    lateinit var identityKeyPair: IdentityKeyPair
    var localRegistrationId: Int = 0

    // ── IdentityKeyStore ─────────────────────────────────────────────

    override fun getIdentityKeyPair(): IdentityKeyPair = identityKeyPair

    override fun getLocalRegistrationId(): Int = localRegistrationId

    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): Boolean {
        val existing = runBlocking { signalDao.getIdentity(address.name) }
        runBlocking {
            signalDao.saveIdentity(
                SignalIdentityEntity(
                    address = address.name,
                    identityKey = identityKey.serialize(),
                    trusted = true
                )
            )
        }
        // Return true if identity key changed (key rotation)
        return existing != null && !existing.identityKey.contentEquals(identityKey.serialize())
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction
    ): Boolean {
        val existing = runBlocking { signalDao.getIdentity(address.name) } ?: return true
        return existing.identityKey.contentEquals(identityKey.serialize())
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? {
        val entity = runBlocking { signalDao.getIdentity(address.name) } ?: return null
        return IdentityKey(entity.identityKey)
    }

    // ── PreKeyStore ──────────────────────────────────────────────────

    override fun loadPreKey(preKeyId: Int): PreKeyRecord {
        val entity = runBlocking { signalDao.getPreKey(preKeyId) }
            ?: throw InvalidKeyIdException("No pre-key $preKeyId")
        return PreKeyRecord(entity.record)
    }

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
        runBlocking {
            signalDao.storePreKey(SignalPreKeyEntity(preKeyId, record.serialize()))
        }
    }

    override fun containsPreKey(preKeyId: Int): Boolean {
        return runBlocking { signalDao.containsPreKey(preKeyId) }
    }

    override fun removePreKey(preKeyId: Int) {
        runBlocking { signalDao.removePreKey(preKeyId) }
    }

    // ── SignedPreKeyStore ─────────────────────────────────────────────

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
        val entity = runBlocking { signalDao.getSignedPreKey(signedPreKeyId) }
            ?: throw InvalidKeyIdException("No signed pre-key $signedPreKeyId")
        return SignedPreKeyRecord(entity.record)
    }

    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> {
        return runBlocking {
            signalDao.getAllSignedPreKeys().map { SignedPreKeyRecord(it.record) }
        }
    }

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        runBlocking {
            signalDao.storeSignedPreKey(
                SignalSignedPreKeyEntity(signedPreKeyId, record.serialize())
            )
        }
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean {
        return runBlocking { signalDao.containsSignedPreKey(signedPreKeyId) }
    }

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        runBlocking { signalDao.removeSignedPreKey(signedPreKeyId) }
    }

    // ── SessionStore ─────────────────────────────────────────────────

    override fun loadSession(address: SignalProtocolAddress): SessionRecord {
        val entity = runBlocking { signalDao.getSession(address.name, address.deviceId) }
            ?: return SessionRecord()
        return SessionRecord(entity.record)
    }

    override fun loadExistingSessions(addresses: List<SignalProtocolAddress>): List<SessionRecord> {
        return addresses.map { address ->
            val entity = runBlocking { signalDao.getSession(address.name, address.deviceId) }
                ?: throw NoSessionException("No session for $address")
            SessionRecord(entity.record)
        }
    }

    override fun getSubDeviceSessions(name: String): List<Int> {
        return runBlocking { signalDao.getSubDeviceSessions(name) }
    }

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        runBlocking {
            signalDao.storeSession(
                SignalSessionEntity(
                    address = address.name,
                    deviceId = address.deviceId,
                    record = record.serialize()
                )
            )
        }
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean {
        return runBlocking { signalDao.containsSession(address.name, address.deviceId) }
    }

    override fun deleteSession(address: SignalProtocolAddress) {
        runBlocking { signalDao.deleteSession(address.name, address.deviceId) }
    }

    override fun deleteAllSessions(name: String) {
        runBlocking { signalDao.deleteAllSessions(name) }
    }
}
