package app.conectx.sync

import app.conectx.crypto.SignalSessionManager
import app.conectx.proto.Envelope
import app.conectx.proto.ReceiptPayload
import app.conectx.proto.TextPayload
import com.google.protobuf.ByteString
import java.security.SecureRandom
import java.util.UUID

/**
 * Builds and parses Protobuf [Envelope] messages per the canonical
 * conectx.proto schema (CON-2 §4).
 *
 * Each Envelope wraps an encrypted payload (Signal Protocol ciphertext)
 * plus routing metadata (sender_id, recipient_id, message_type, ttl).
 * The signature covers fields 1-6 using the sender's Ed25519 identity key.
 */
object EnvelopeSerializer {

    /** Message type constants matching conectx.proto */
    const val TYPE_TEXT = 1
    const val TYPE_RECEIPT = 2
    const val TYPE_KEY_EXCHANGE = 3

    private val random = SecureRandom()

    /**
     * Builds a text message envelope.
     * The text is first serialized to Protobuf, then encrypted with Signal Protocol.
     */
    fun buildTextEnvelope(
        signalManager: SignalSessionManager,
        recipientId: ByteArray,
        text: String,
        replyToId: String? = null,
        ttl: Int = 7
    ): ByteArray {
        val textPayload = TextPayload.newBuilder()
            .setText(text)
            .also { if (replyToId != null) it.setReplyToId(replyToId) }
            .build()

        val recipientAddress = recipientId.toHexString()
        val ciphertext = signalManager.encrypt(recipientAddress, textPayload.toByteArray())

        val nonce = ByteArray(12).also { random.nextBytes(it) }
        val messageId = UUID.randomUUID().toByteArray()

        val envelope = Envelope.newBuilder()
            .setSenderId(ByteString.copyFrom(signalManager.getIdentityPublicKey()))
            .setRecipientId(ByteString.copyFrom(recipientId))
            .setTimestamp(System.currentTimeMillis().toULong().toLong())
            .setNonce(ByteString.copyFrom(nonce))
            .setCiphertext(ByteString.copyFrom(ciphertext))
            .setMessageType(TYPE_TEXT)
            .setTtl(ttl)
            .setMessageId(ByteString.copyFrom(messageId))

        // Sign fields 1-6
        val signable = signableBytes(envelope.build())
        val signature = signalManager.sign(signable)
        envelope.setSignature(ByteString.copyFrom(signature))

        return envelope.build().toByteArray()
    }

    /**
     * Builds a delivery/read receipt envelope.
     */
    fun buildReceiptEnvelope(
        signalManager: SignalSessionManager,
        recipientId: ByteArray,
        originalMessageId: ByteArray,
        receiptType: Int
    ): ByteArray {
        val receiptPayload = ReceiptPayload.newBuilder()
            .setMessageId(ByteString.copyFrom(originalMessageId))
            .setReceiptType(receiptType)
            .build()

        val recipientAddress = recipientId.toHexString()
        val ciphertext = signalManager.encrypt(recipientAddress, receiptPayload.toByteArray())

        val nonce = ByteArray(12).also { random.nextBytes(it) }
        val messageId = UUID.randomUUID().toByteArray()

        val envelope = Envelope.newBuilder()
            .setSenderId(ByteString.copyFrom(signalManager.getIdentityPublicKey()))
            .setRecipientId(ByteString.copyFrom(recipientId))
            .setTimestamp(System.currentTimeMillis().toULong().toLong())
            .setNonce(ByteString.copyFrom(nonce))
            .setCiphertext(ByteString.copyFrom(ciphertext))
            .setMessageType(TYPE_RECEIPT)
            .setTtl(7)
            .setMessageId(ByteString.copyFrom(messageId))

        val signable = signableBytes(envelope.build())
        val signature = signalManager.sign(signable)
        envelope.setSignature(ByteString.copyFrom(signature))

        return envelope.build().toByteArray()
    }

    /**
     * Parses an incoming Envelope from raw bytes.
     */
    fun parseEnvelope(bytes: ByteArray): Envelope {
        return Envelope.parseFrom(bytes)
    }

    /**
     * Verifies the Ed25519 signature on an envelope.
     */
    fun verifyEnvelope(envelope: Envelope, signalManager: SignalSessionManager): Boolean {
        if (envelope.signature.isEmpty) return false
        val signable = signableBytes(envelope)
        return signalManager.verify(
            envelope.senderId.toByteArray(),
            signable,
            envelope.signature.toByteArray()
        )
    }

    /**
     * Decrypts the ciphertext in an envelope and returns the plaintext bytes.
     * The caller must know the message_type to deserialize the inner payload.
     */
    fun decryptEnvelope(
        envelope: Envelope,
        signalManager: SignalSessionManager,
        isPreKeyMessage: Boolean
    ): ByteArray {
        val senderAddress = envelope.senderId.toByteArray().toHexString()
        return signalManager.decrypt(senderAddress, envelope.ciphertext.toByteArray(), isPreKeyMessage)
    }

    /**
     * Canonical bytes for signing: fields 1-6 concatenated.
     * Must match iOS implementation exactly.
     */
    private fun signableBytes(envelope: Envelope): ByteArray {
        val stripped = envelope.toBuilder()
            .clearSignature()
            .clearTtl()
            .clearMessageId()
            .build()
        return stripped.toByteArray()
    }

    // ── Utility ──────────────────────────────────────────────────────

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }

    private fun UUID.toByteArray(): ByteArray {
        val bb = java.nio.ByteBuffer.allocate(16)
        bb.putLong(mostSignificantBits)
        bb.putLong(leastSignificantBits)
        return bb.array()
    }
}
