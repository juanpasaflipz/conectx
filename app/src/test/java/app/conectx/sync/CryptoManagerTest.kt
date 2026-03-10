package app.conectx.sync

import app.conectx.domain.model.RecordType
import app.conectx.domain.model.SyncRecord
import com.google.crypto.tink.BinaryKeysetWriter
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.config.TinkConfig
import com.google.crypto.tink.signature.SignatureKeyTemplates
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Tests Ed25519 crypto operations using Tink directly.
 * No Android / DataStore dependencies needed — these test the pure crypto logic.
 */
class CryptoManagerTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun initTink() {
            TinkConfig.register()
        }

        /** Helper: serialize a public keyset to bytes (same format CryptoManager uses) */
        private fun publicKeyBytes(handle: KeysetHandle): ByteArray {
            val baos = ByteArrayOutputStream()
            handle.publicKeysetHandle.writeNoSecret(BinaryKeysetWriter.withOutputStream(baos))
            return baos.toByteArray()
        }
    }

    @Test
    fun `key generation produces valid keypair`() {
        val handle = KeysetHandle.generateNew(SignatureKeyTemplates.ED25519)
        val signer = handle.getPrimitive(PublicKeySign::class.java)

        val data = "test data".toByteArray()
        val signature = signer.sign(data)

        // Verify with the public key — should succeed
        val pubBytes = publicKeyBytes(handle)
        assertTrue(CryptoManager.verify(data, signature, pubBytes))
    }

    @Test
    fun `sign and verify round-trip succeeds`() {
        val handle = KeysetHandle.generateNew(SignatureKeyTemplates.ED25519)
        val signer = handle.getPrimitive(PublicKeySign::class.java)
        val pubBytes = publicKeyBytes(handle)

        val data = "hello mesh network".toByteArray()
        val signature = signer.sign(data)

        assertTrue(CryptoManager.verify(data, signature, pubBytes))
    }

    @Test
    fun `verify with wrong key fails`() {
        val handle1 = KeysetHandle.generateNew(SignatureKeyTemplates.ED25519)
        val handle2 = KeysetHandle.generateNew(SignatureKeyTemplates.ED25519)

        val signer1 = handle1.getPrimitive(PublicKeySign::class.java)
        val pubBytes2 = publicKeyBytes(handle2) // wrong key

        val data = "signed by key 1".toByteArray()
        val signature = signer1.sign(data)

        assertFalse(CryptoManager.verify(data, signature, pubBytes2))
    }

    @Test
    fun `verify with tampered data fails`() {
        val handle = KeysetHandle.generateNew(SignatureKeyTemplates.ED25519)
        val signer = handle.getPrimitive(PublicKeySign::class.java)
        val pubBytes = publicKeyBytes(handle)

        val data = "original message".toByteArray()
        val signature = signer.sign(data)

        val tampered = "tampered message".toByteArray()
        assertFalse(CryptoManager.verify(tampered, signature, pubBytes))
    }

    @Test
    fun `verify with garbage public key returns false`() {
        val garbage = byteArrayOf(0, 1, 2, 3, 4)
        assertFalse(CryptoManager.verify("data".toByteArray(), ByteArray(64), garbage))
    }

    @Test
    fun `signableBytes is deterministic for same record`() {
        val record = SyncRecord(
            id = "deterministic-test",
            squadId = "squad-42",
            authorId = "author-7",
            lamportClock = 99L,
            timestamp = 1717000000000L,
            type = RecordType.CHAT,
            payload = "hello".toByteArray(),
            signature = ByteArray(0)
        )

        val bytes1 = CryptoManager.signableBytes(record)
        val bytes2 = CryptoManager.signableBytes(record)

        assertArrayEquals(bytes1, bytes2)
    }

    @Test
    fun `signableBytes differs for different records`() {
        val record1 = SyncRecord(
            id = "id-1",
            squadId = "squad-1",
            authorId = "author-1",
            lamportClock = 1L,
            timestamp = 1000L,
            type = RecordType.CHAT,
            payload = "hello".toByteArray(),
            signature = ByteArray(0)
        )

        val record2 = record1.copy(id = "id-2")

        val bytes1 = CryptoManager.signableBytes(record1)
        val bytes2 = CryptoManager.signableBytes(record2)

        assertFalse(bytes1.contentEquals(bytes2))
    }

    @Test
    fun `signableBytes ignores signature field`() {
        val record = SyncRecord(
            id = "sig-test",
            squadId = "s1",
            authorId = "a1",
            lamportClock = 1L,
            timestamp = 1000L,
            type = RecordType.CHAT,
            payload = "data".toByteArray(),
            signature = ByteArray(0)
        )

        val withSig = record.copy(signature = ByteArray(64) { 0xFF.toByte() })

        // signableBytes should produce same output regardless of signature value
        assertArrayEquals(
            CryptoManager.signableBytes(record),
            CryptoManager.signableBytes(withSig)
        )
    }
}
