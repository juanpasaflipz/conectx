package app.conectx.sync

import app.conectx.crypto.SignalSessionManager
import app.conectx.proto.Envelope
import app.conectx.proto.TextPayload
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EnvelopeSerializerTest {

    private lateinit var signalManager: SignalSessionManager

    // Fake identity key (32 bytes)
    private val senderKey = ByteArray(32) { it.toByte() }
    private val recipientKey = ByteArray(32) { (it + 32).toByte() }
    // Fake ciphertext
    private val fakeCiphertext = "encrypted-payload".toByteArray()
    // Fake signature (64 bytes)
    private val fakeSignature = ByteArray(64) { (it + 100).toByte() }

    @Before
    fun setup() {
        signalManager = mockk()
        every { signalManager.getIdentityPublicKey() } returns senderKey
        every { signalManager.encrypt(any(), any()) } returns fakeCiphertext
        every { signalManager.sign(any()) } returns fakeSignature
    }

    @Test
    fun `buildTextEnvelope produces valid protobuf bytes`() {
        val envelopeBytes = EnvelopeSerializer.buildTextEnvelope(
            signalManager = signalManager,
            recipientId = recipientKey,
            text = "Hola mundo"
        )

        // Should parse as a valid Envelope protobuf
        val envelope = Envelope.parseFrom(envelopeBytes)

        assertArrayEquals(senderKey, envelope.senderId.toByteArray())
        assertArrayEquals(recipientKey, envelope.recipientId.toByteArray())
        assertEquals(EnvelopeSerializer.TYPE_TEXT, envelope.messageType)
        assertEquals(7, envelope.ttl)  // default TTL
        assertFalse(envelope.nonce.isEmpty)
        assertFalse(envelope.ciphertext.isEmpty)
        assertFalse(envelope.signature.isEmpty)
        assertFalse(envelope.messageId.isEmpty)
        assertTrue(envelope.timestamp > 0)
    }

    @Test
    fun `buildTextEnvelope with reply`() {
        val envelopeBytes = EnvelopeSerializer.buildTextEnvelope(
            signalManager = signalManager,
            recipientId = recipientKey,
            text = "reply text",
            replyToId = "original-msg-id"
        )

        val envelope = Envelope.parseFrom(envelopeBytes)
        assertEquals(EnvelopeSerializer.TYPE_TEXT, envelope.messageType)
    }

    @Test
    fun `buildReceiptEnvelope produces TYPE_RECEIPT`() {
        val originalMessageId = ByteArray(16) { it.toByte() }
        val envelopeBytes = EnvelopeSerializer.buildReceiptEnvelope(
            signalManager = signalManager,
            recipientId = recipientKey,
            originalMessageId = originalMessageId,
            receiptType = 1
        )

        val envelope = Envelope.parseFrom(envelopeBytes)
        assertEquals(EnvelopeSerializer.TYPE_RECEIPT, envelope.messageType)
    }

    @Test
    fun `parseEnvelope round-trips correctly`() {
        val envelopeBytes = EnvelopeSerializer.buildTextEnvelope(
            signalManager = signalManager,
            recipientId = recipientKey,
            text = "test message"
        )

        val parsed = EnvelopeSerializer.parseEnvelope(envelopeBytes)
        val reparsed = EnvelopeSerializer.parseEnvelope(parsed.toByteArray())

        assertEquals(parsed.messageType, reparsed.messageType)
        assertEquals(parsed.timestamp, reparsed.timestamp)
        assertArrayEquals(parsed.senderId.toByteArray(), reparsed.senderId.toByteArray())
    }

    @Test
    fun `verifyEnvelope returns true for valid signature`() {
        val envelopeBytes = EnvelopeSerializer.buildTextEnvelope(
            signalManager = signalManager,
            recipientId = recipientKey,
            text = "verified message"
        )

        val envelope = EnvelopeSerializer.parseEnvelope(envelopeBytes)

        // Mock verify to return true
        every { signalManager.verify(any(), any(), any()) } returns true

        assertTrue(EnvelopeSerializer.verifyEnvelope(envelope, signalManager))
    }

    @Test
    fun `verifyEnvelope returns false for empty signature`() {
        val envelope = Envelope.newBuilder()
            .setMessageType(EnvelopeSerializer.TYPE_TEXT)
            .build()

        // Empty signature should fail
        assertFalse(EnvelopeSerializer.verifyEnvelope(envelope, signalManager))
    }

    @Test
    fun `verifyEnvelope returns false for bad signature`() {
        val envelopeBytes = EnvelopeSerializer.buildTextEnvelope(
            signalManager = signalManager,
            recipientId = recipientKey,
            text = "tampered"
        )

        val envelope = EnvelopeSerializer.parseEnvelope(envelopeBytes)
        every { signalManager.verify(any(), any(), any()) } returns false

        assertFalse(EnvelopeSerializer.verifyEnvelope(envelope, signalManager))
    }

    @Test
    fun `message type constants match proto convention`() {
        assertEquals(1, EnvelopeSerializer.TYPE_TEXT)
        assertEquals(2, EnvelopeSerializer.TYPE_RECEIPT)
        assertEquals(3, EnvelopeSerializer.TYPE_KEY_EXCHANGE)
    }

    @Test
    fun `nonce is 12 bytes`() {
        val envelopeBytes = EnvelopeSerializer.buildTextEnvelope(
            signalManager = signalManager,
            recipientId = recipientKey,
            text = "nonce check"
        )

        val envelope = EnvelopeSerializer.parseEnvelope(envelopeBytes)
        assertEquals(12, envelope.nonce.toByteArray().size)
    }

    @Test
    fun `messageId is 16 bytes (UUID)`() {
        val envelopeBytes = EnvelopeSerializer.buildTextEnvelope(
            signalManager = signalManager,
            recipientId = recipientKey,
            text = "uuid check"
        )

        val envelope = EnvelopeSerializer.parseEnvelope(envelopeBytes)
        assertEquals(16, envelope.messageId.toByteArray().size)
    }
}
