package app.conectx.sync

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import app.conectx.data.local.preferences.UserPreferences
import app.conectx.domain.model.SyncRecord
import com.google.crypto.tink.BinaryKeysetReader
import com.google.crypto.tink.BinaryKeysetWriter
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.JsonKeysetReader
import com.google.crypto.tink.JsonKeysetWriter
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.PublicKeyVerify
import com.google.crypto.tink.config.TinkConfig
import com.google.crypto.tink.signature.SignatureKeyTemplates
import kotlinx.coroutines.flow.first
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Ed25519 signing keys for SyncRecord authentication.
 *
 * - Generates a keypair on first use, persists in DataStore
 * - Signs outgoing records so peers can verify authorship
 * - Verifies incoming records using cached peer public keys
 *
 * Companion functions (signableBytes, verify) are pure and testable
 * without Android dependencies.
 */
@Singleton
class CryptoManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private const val TAG = "CryptoManager"

        init {
            TinkConfig.register()
        }

        /**
         * Canonical byte representation for signing.
         * Includes all SyncRecord fields EXCEPT signature itself.
         * Deterministic: same record always produces same bytes.
         */
        fun signableBytes(record: SyncRecord): ByteArray {
            val baos = ByteArrayOutputStream()
            DataOutputStream(baos).use { out ->
                out.writeUTF(record.id)
                out.writeUTF(record.squadId)
                out.writeUTF(record.authorId)
                out.writeLong(record.lamportClock)
                out.writeLong(record.timestamp)
                out.writeUTF(record.type.name)
                out.writeInt(record.payload.size)
                out.write(record.payload)
            }
            return baos.toByteArray()
        }

        /**
         * Verifies an Ed25519 signature using serialized Tink public keyset bytes.
         * Returns false on any error (bad key, bad signature, tampered data).
         */
        fun verify(data: ByteArray, signature: ByteArray, publicKeyBytes: ByteArray): Boolean {
            return try {
                val publicHandle = KeysetHandle.readNoSecret(
                    BinaryKeysetReader.withBytes(publicKeyBytes)
                )
                val verifier = publicHandle.getPrimitive(PublicKeyVerify::class.java)
                verifier.verify(signature, data)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    @Volatile
    private var cachedHandle: KeysetHandle? = null

    /**
     * Signs data with our Ed25519 private key.
     * Generates the key on first call if none exists.
     */
    suspend fun sign(data: ByteArray): ByteArray {
        val handle = getOrCreateKeyset()
        val signer = handle.getPrimitive(PublicKeySign::class.java)
        return signer.sign(data)
    }

    /**
     * Returns our public key as serialized Tink keyset bytes.
     * Peers use these bytes with [verify] to authenticate our records.
     */
    suspend fun getPublicKeyBytes(): ByteArray {
        val handle = getOrCreateKeyset()
        val baos = ByteArrayOutputStream()
        handle.publicKeysetHandle.writeNoSecret(BinaryKeysetWriter.withOutputStream(baos))
        return baos.toByteArray()
    }

    private suspend fun getOrCreateKeyset(): KeysetHandle {
        cachedHandle?.let { return it }

        val prefs = dataStore.data.first()
        val existingJson = prefs[UserPreferences.SIGNING_KEYSET]

        val handle = if (existingJson != null) {
            CleartextKeysetHandle.read(JsonKeysetReader.withString(existingJson))
        } else {
            val newHandle = KeysetHandle.generateNew(SignatureKeyTemplates.ED25519)
            val jsonBaos = ByteArrayOutputStream()
            CleartextKeysetHandle.write(newHandle, JsonKeysetWriter.withOutputStream(jsonBaos))
            val json = jsonBaos.toString("UTF-8")
            dataStore.edit { it[UserPreferences.SIGNING_KEYSET] = json }
            Log.d(TAG, "Generated new Ed25519 signing key")
            newHandle
        }

        cachedHandle = handle
        return handle
    }
}
