package app.conectx.crypto

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.conectx.data.local.preferences.UserPreferences
import kotlinx.coroutines.flow.first
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.util.KeyHelper
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Signal Protocol sessions for E2E encrypted P2P messaging.
 *
 * Replaces the old Tink-based CryptoManager. Instead of simple signing,
 * we now get full X3DH key exchange + Double Ratchet forward secrecy.
 *
 * Key lifecycle:
 * 1. Identity key (Ed25519): generated once, stored in DataStore (long-lived)
 * 2. Signed pre-key (X25519): generated on first use, rotated weekly
 * 3. One-time pre-keys (X25519): batch of 100, consumed by X3DH
 */
@Singleton
class SignalSessionManager @Inject constructor(
    private val store: ConectxSignalStore,
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private const val TAG = "SignalSessionManager"
        private const val DEVICE_ID = 1
        private const val SIGNED_PRE_KEY_ID = 1
        private const val ONE_TIME_PRE_KEY_START = 1
        private const val ONE_TIME_PRE_KEY_COUNT = 100

        // DataStore keys for serialized identity key pair
        val IDENTITY_KEY_PAIR = stringPreferencesKey("signal_identity_key_pair")
        val REGISTRATION_ID = stringPreferencesKey("signal_registration_id")
    }

    private var initialized = false

    /**
     * Initialize the Signal store with our identity key pair.
     * Must be called once at app startup (from MeshService or ConectxApp).
     */
    suspend fun initialize() {
        if (initialized) return

        val prefs = dataStore.data.first()
        val existingKeyPair = prefs[IDENTITY_KEY_PAIR]
        val existingRegId = prefs[REGISTRATION_ID]

        if (existingKeyPair != null && existingRegId != null) {
            store.identityKeyPair = IdentityKeyPair(
                Base64.getDecoder().decode(existingKeyPair)
            )
            store.localRegistrationId = existingRegId.toInt()
            Log.d(TAG, "Loaded existing Signal identity")
        } else {
            val keyPair = IdentityKeyPair.generate()
            val registrationId = KeyHelper.generateRegistrationId(false)

            store.identityKeyPair = keyPair
            store.localRegistrationId = registrationId

            dataStore.edit {
                it[IDENTITY_KEY_PAIR] = Base64.getEncoder().encodeToString(keyPair.serialize())
                it[REGISTRATION_ID] = registrationId.toString()
            }
            Log.d(TAG, "Generated new Signal identity (regId=$registrationId)")

            // Generate initial signed pre-key and one-time pre-keys
            generateSignedPreKey()
            generateOneTimePreKeys()
        }

        initialized = true
    }

    /** Our 32-byte Ed25519 identity public key, used as sender_id in Envelope. */
    fun getIdentityPublicKey(): ByteArray {
        return store.identityKeyPair.publicKey.serialize()
    }

    /** Our registration ID for Signal protocol. */
    fun getRegistrationId(): Int {
        return store.localRegistrationId
    }

    /**
     * Builds a PreKeyBundle protobuf for exchange over BLE/WiFi Aware.
     * Peers use this to initiate X3DH key agreement with us.
     */
    fun getLocalPreKeyBundle(): PreKeyBundle {
        val signedPreKey = store.loadSignedPreKey(SIGNED_PRE_KEY_ID)

        // Try to find an available one-time pre-key
        var oneTimePreKeyId = -1
        var oneTimePreKeyPublic: org.signal.libsignal.protocol.ecc.ECPublicKey? = null
        for (id in ONE_TIME_PRE_KEY_START until ONE_TIME_PRE_KEY_START + ONE_TIME_PRE_KEY_COUNT) {
            if (store.containsPreKey(id)) {
                val otpk = store.loadPreKey(id)
                oneTimePreKeyId = id
                oneTimePreKeyPublic = otpk.keyPair.publicKey
                break
            }
        }

        return PreKeyBundle(
            store.localRegistrationId,
            DEVICE_ID,
            oneTimePreKeyId,
            oneTimePreKeyPublic,
            SIGNED_PRE_KEY_ID,
            signedPreKey.keyPair.publicKey,
            signedPreKey.signature,
            store.identityKeyPair.publicKey
        )
    }

    /**
     * Processes a remote peer's pre-key bundle to establish an outgoing session.
     * After this, [encrypt] can be called for this peer.
     */
    fun processPreKeyBundle(peerAddress: String, bundle: PreKeyBundle) {
        val address = SignalProtocolAddress(peerAddress, DEVICE_ID)
        val builder = SessionBuilder(store, store, store, store, address)
        builder.process(bundle)
        Log.d(TAG, "Session established with $peerAddress via X3DH")
    }

    /**
     * Returns true if we have an established session with the given peer.
     */
    fun hasSession(peerAddress: String): Boolean {
        val address = SignalProtocolAddress(peerAddress, DEVICE_ID)
        return store.containsSession(address)
    }

    /**
     * Encrypts plaintext for a specific peer using the Double Ratchet.
     * A session must have been established via [processPreKeyBundle] first.
     */
    fun encrypt(peerAddress: String, plaintext: ByteArray): ByteArray {
        val address = SignalProtocolAddress(peerAddress, DEVICE_ID)
        val cipher = SessionCipher(store, store, store, store, address)
        val ciphertext = cipher.encrypt(plaintext)
        return ciphertext.serialize()
    }

    /**
     * Decrypts ciphertext from a specific peer. Handles both pre-key messages
     * (first message in a session) and regular messages.
     */
    fun decrypt(peerAddress: String, ciphertext: ByteArray, isPreKeyMessage: Boolean): ByteArray {
        val address = SignalProtocolAddress(peerAddress, DEVICE_ID)
        val cipher = SessionCipher(store, store, store, store, address)

        return if (isPreKeyMessage) {
            val preKeyMsg = org.signal.libsignal.protocol.message.PreKeySignalMessage(ciphertext)
            cipher.decrypt(preKeyMsg)
        } else {
            val msg = org.signal.libsignal.protocol.message.SignalMessage(ciphertext)
            cipher.decrypt(msg)
        }
    }

    /**
     * Signs data with our Ed25519 identity key.
     * Used for the Envelope.signature field.
     */
    fun sign(data: ByteArray): ByteArray {
        return Curve.calculateSignature(store.identityKeyPair.privateKey, data)
    }

    /**
     * Verifies an Ed25519 signature from a peer.
     */
    fun verify(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean {
        return try {
            val identityKey = IdentityKey(publicKey)
            Curve.verifySignature(identityKey.publicKey, data, signature)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Signature verification failed", e)
            false
        }
    }

    // ── Key generation ────────────────────────────────────────────────

    private fun generateSignedPreKey() {
        val signedPreKey = KeyHelper.generateSignedPreKey(
            store.identityKeyPair,
            SIGNED_PRE_KEY_ID
        )
        store.storeSignedPreKey(SIGNED_PRE_KEY_ID, signedPreKey)
        Log.d(TAG, "Generated signed pre-key (id=$SIGNED_PRE_KEY_ID)")
    }

    private fun generateOneTimePreKeys() {
        val preKeys = KeyHelper.generatePreKeys(ONE_TIME_PRE_KEY_START, ONE_TIME_PRE_KEY_COUNT)
        for (preKey in preKeys) {
            store.storePreKey(preKey.id, preKey)
        }
        Log.d(TAG, "Generated $ONE_TIME_PRE_KEY_COUNT one-time pre-keys")
    }
}
