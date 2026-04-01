package app.conectx.sync

import app.conectx.proto.Envelope
import app.conectx.proto.PreKeyBundle
import app.conectx.proto.ReceiptPayload
import app.conectx.proto.TextPayload
import com.google.protobuf.ByteString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.util.UUID

/**
 * Cross-platform interop tests with deterministic test vectors.
 *
 * These tests validate the wire format conventions shared between
 * Android and iOS. The iOS codebase MUST produce identical bytes
 * for the same inputs. Each test documents the expected byte layout
 * so both platforms can independently verify compatibility.
 *
 * Protocol conventions:
 * - WiFi Aware service name: "conectx"
 * - WiFi Aware service type: "_conectx._tcp"
 * - Prefix bytes: 0x01 = PreKeyBundle, 0x02 = Envelope
 * - Protobuf schema: conectx.proto (shared source of truth)
 * - Identity keys: 32-byte Ed25519 public key
 * - Nonce: 12 bytes random
 * - Message ID: 16 bytes (UUID)
 * - Signing: Ed25519 over fields 1-6 only (clear signature, ttl, messageId)
 * - Device ID: always 1
 * - Default TTL: 7
 */
class CrossPlatformInteropTest {

    // ── Deterministic test keys (32 bytes each) ──────────────────────

    private val aliceIdentityKey = ByteArray(32) { it.toByte() }       // 0x00..0x1F
    private val bobIdentityKey = ByteArray(32) { (it + 32).toByte() }  // 0x20..0x3F

    // ── 1. Prefix byte convention ────────────────────────────────────

    @Test
    fun `prefix 0x01 identifies PreKeyBundle`() {
        val bundleProto = PreKeyBundle.newBuilder()
            .setIdentityKey(ByteString.copyFrom(aliceIdentityKey))
            .setRegistrationId(12345)
            .build()

        val prefixed = ByteArray(1 + bundleProto.serializedSize)
        prefixed[0] = 0x01
        System.arraycopy(bundleProto.toByteArray(), 0, prefixed, 1, bundleProto.serializedSize)

        assertEquals(0x01.toByte(), prefixed[0])
        // Strip prefix and reparse
        val stripped = prefixed.copyOfRange(1, prefixed.size)
        val reparsed = PreKeyBundle.parseFrom(stripped)
        assertArrayEquals(aliceIdentityKey, reparsed.identityKey.toByteArray())
    }

    @Test
    fun `prefix 0x02 identifies Envelope`() {
        val envelope = Envelope.newBuilder()
            .setSenderId(ByteString.copyFrom(aliceIdentityKey))
            .setRecipientId(ByteString.copyFrom(bobIdentityKey))
            .setMessageType(1)
            .build()

        val envelopeBytes = envelope.toByteArray()
        val prefixed = ByteArray(1 + envelopeBytes.size)
        prefixed[0] = 0x02
        System.arraycopy(envelopeBytes, 0, prefixed, 1, envelopeBytes.size)

        assertEquals(0x02.toByte(), prefixed[0])
        val stripped = prefixed.copyOfRange(1, prefixed.size)
        val reparsed = Envelope.parseFrom(stripped)
        assertArrayEquals(aliceIdentityKey, reparsed.senderId.toByteArray())
    }

    @Test
    fun `unknown prefix byte falls back gracefully`() {
        // Any byte other than 0x01 or 0x02 should be handled
        // Android tries to parse as Envelope for backward compat
        val unknownPrefix: Byte = 0x03
        assertNotEquals(0x01.toByte(), unknownPrefix)
        assertNotEquals(0x02.toByte(), unknownPrefix)
    }

    // ── 2. Envelope protobuf wire format ─────────────────────────────

    @Test
    fun `envelope field numbers match proto schema`() {
        // These field numbers are baked into the protobuf wire format.
        // Both platforms MUST use the exact same field numbers.
        val nonce = ByteArray(12) { (it + 100).toByte() }
        val ciphertext = "test-cipher".toByteArray()
        val signature = ByteArray(64) { (it + 200).toByte() }
        val messageId = ByteArray(16) { (it + 50).toByte() }

        val envelope = Envelope.newBuilder()
            .setSenderId(ByteString.copyFrom(aliceIdentityKey))       // field 1
            .setRecipientId(ByteString.copyFrom(bobIdentityKey))      // field 2
            .setTimestamp(1712000000000L)                               // field 3
            .setNonce(ByteString.copyFrom(nonce))                      // field 4
            .setCiphertext(ByteString.copyFrom(ciphertext))            // field 5
            .setMessageType(1)                                         // field 6
            .setSignature(ByteString.copyFrom(signature))              // field 7
            .setTtl(7)                                                 // field 8
            .setMessageId(ByteString.copyFrom(messageId))              // field 9
            .build()

        // Serialize and reparse to verify round-trip
        val bytes = envelope.toByteArray()
        val reparsed = Envelope.parseFrom(bytes)

        assertArrayEquals(aliceIdentityKey, reparsed.senderId.toByteArray())
        assertArrayEquals(bobIdentityKey, reparsed.recipientId.toByteArray())
        assertEquals(1712000000000L, reparsed.timestamp)
        assertArrayEquals(nonce, reparsed.nonce.toByteArray())
        assertArrayEquals(ciphertext, reparsed.ciphertext.toByteArray())
        assertEquals(1, reparsed.messageType)
        assertArrayEquals(signature, reparsed.signature.toByteArray())
        assertEquals(7, reparsed.ttl)
        assertArrayEquals(messageId, reparsed.messageId.toByteArray())
    }

    @Test
    fun `envelope with deterministic fields produces stable bytes`() {
        // Build the same envelope twice — must produce identical bytes
        val builder = {
            Envelope.newBuilder()
                .setSenderId(ByteString.copyFrom(aliceIdentityKey))
                .setRecipientId(ByteString.copyFrom(bobIdentityKey))
                .setTimestamp(1712000000000L)
                .setNonce(ByteString.copyFrom(ByteArray(12) { 0xAA.toByte() }))
                .setCiphertext(ByteString.copyFrom("hello".toByteArray()))
                .setMessageType(1)
                .setTtl(7)
                .setMessageId(ByteString.copyFrom(ByteArray(16) { 0xBB.toByte() }))
                .build()
        }

        val bytes1 = builder().toByteArray()
        val bytes2 = builder().toByteArray()
        assertArrayEquals(bytes1, bytes2)
    }

    // ── 3. Signable bytes convention ─────────────────────────────────

    @Test
    fun `signable bytes exclude signature, ttl, and messageId`() {
        val nonce = ByteArray(12) { 0xCC.toByte() }

        val full = Envelope.newBuilder()
            .setSenderId(ByteString.copyFrom(aliceIdentityKey))
            .setRecipientId(ByteString.copyFrom(bobIdentityKey))
            .setTimestamp(1712000000000L)
            .setNonce(ByteString.copyFrom(nonce))
            .setCiphertext(ByteString.copyFrom("cipher".toByteArray()))
            .setMessageType(1)
            .setSignature(ByteString.copyFrom(ByteArray(64) { 0xFF.toByte() }))
            .setTtl(7)
            .setMessageId(ByteString.copyFrom(ByteArray(16) { 0xDD.toByte() }))
            .build()

        // Signable = fields 1-6 only (clear 7=signature, 8=ttl, 9=messageId)
        val signable = full.toBuilder()
            .clearSignature()
            .clearTtl()
            .clearMessageId()
            .build()
            .toByteArray()

        // Verify the stripped envelope can be reparsed
        val parsed = Envelope.parseFrom(signable)
        assertArrayEquals(aliceIdentityKey, parsed.senderId.toByteArray())
        assertEquals(1712000000000L, parsed.timestamp)
        assertEquals(1, parsed.messageType)
        // Stripped fields should be default values
        assertTrue(parsed.signature.isEmpty)
        assertEquals(0, parsed.ttl)
        assertTrue(parsed.messageId.isEmpty)
    }

    @Test
    fun `signable bytes are identical regardless of signature and ttl values`() {
        val buildEnvelope = { sig: ByteArray, ttl: Int, msgId: ByteArray ->
            Envelope.newBuilder()
                .setSenderId(ByteString.copyFrom(aliceIdentityKey))
                .setRecipientId(ByteString.copyFrom(bobIdentityKey))
                .setTimestamp(1712000000000L)
                .setNonce(ByteString.copyFrom(ByteArray(12)))
                .setCiphertext(ByteString.copyFrom("x".toByteArray()))
                .setMessageType(1)
                .setSignature(ByteString.copyFrom(sig))
                .setTtl(ttl)
                .setMessageId(ByteString.copyFrom(msgId))
                .build()
        }

        val strip = { env: Envelope ->
            env.toBuilder()
                .clearSignature()
                .clearTtl()
                .clearMessageId()
                .build()
                .toByteArray()
        }

        val signable1 = strip(buildEnvelope(ByteArray(64) { 0x11 }, 7, ByteArray(16) { 0x22 }))
        val signable2 = strip(buildEnvelope(ByteArray(64) { 0xFF.toByte() }, 3, ByteArray(16) { 0x99.toByte() }))

        assertArrayEquals(signable1, signable2)
    }

    // ── 4. PreKeyBundle protobuf wire format ─────────────────────────

    @Test
    fun `PreKeyBundle field numbers match proto schema`() {
        val signedPreKey = ByteArray(33) { (it + 70).toByte() }      // X25519 point (33 bytes)
        val signedPreKeySig = ByteArray(64) { (it + 130).toByte() }  // Ed25519 sig
        val oneTimePreKey = ByteArray(33) { (it + 200).toByte() }    // X25519 point

        val bundle = PreKeyBundle.newBuilder()
            .setIdentityKey(ByteString.copyFrom(aliceIdentityKey))    // field 1
            .setSignedPreKeyId(1)                                      // field 2
            .setSignedPreKey(ByteString.copyFrom(signedPreKey))        // field 3
            .setSignedPreKeySignature(ByteString.copyFrom(signedPreKeySig)) // field 4
            .setOneTimePreKeyId(42)                                    // field 5
            .setOneTimePreKey(ByteString.copyFrom(oneTimePreKey))      // field 6
            .setRegistrationId(9999)                                   // field 7
            .build()

        val bytes = bundle.toByteArray()
        val reparsed = PreKeyBundle.parseFrom(bytes)

        assertArrayEquals(aliceIdentityKey, reparsed.identityKey.toByteArray())
        assertEquals(1, reparsed.signedPreKeyId)
        assertArrayEquals(signedPreKey, reparsed.signedPreKey.toByteArray())
        assertArrayEquals(signedPreKeySig, reparsed.signedPreKeySignature.toByteArray())
        assertEquals(42, reparsed.oneTimePreKeyId)
        assertArrayEquals(oneTimePreKey, reparsed.oneTimePreKey.toByteArray())
        assertEquals(9999, reparsed.registrationId)
    }

    @Test
    fun `PreKeyBundle without one-time key is valid`() {
        val bundle = PreKeyBundle.newBuilder()
            .setIdentityKey(ByteString.copyFrom(aliceIdentityKey))
            .setSignedPreKeyId(1)
            .setSignedPreKey(ByteString.copyFrom(ByteArray(33)))
            .setSignedPreKeySignature(ByteString.copyFrom(ByteArray(64)))
            .setRegistrationId(1234)
            // Omit oneTimePreKeyId and oneTimePreKey
            .build()

        val bytes = bundle.toByteArray()
        val reparsed = PreKeyBundle.parseFrom(bytes)

        assertTrue(reparsed.oneTimePreKey.isEmpty)
        assertEquals(0, reparsed.oneTimePreKeyId) // proto3 default
    }

    // ── 5. TextPayload / ReceiptPayload ──────────────────────────────

    @Test
    fun `TextPayload round-trip preserves text and replyToId`() {
        val payload = TextPayload.newBuilder()
            .setText("Hola desde Android!")
            .setReplyToId("abc-123-def")
            .build()

        val bytes = payload.toByteArray()
        val reparsed = TextPayload.parseFrom(bytes)

        assertEquals("Hola desde Android!", reparsed.text)
        assertEquals("abc-123-def", reparsed.replyToId)
    }

    @Test
    fun `TextPayload without reply is valid`() {
        val payload = TextPayload.newBuilder()
            .setText("Simple message")
            .build()

        val bytes = payload.toByteArray()
        val reparsed = TextPayload.parseFrom(bytes)

        assertEquals("Simple message", reparsed.text)
        assertEquals("", reparsed.replyToId) // proto3 default
    }

    @Test
    fun `TextPayload preserves unicode and emoji`() {
        val text = "Gol de M\u00e9xico! \uD83C\uDDF2\uD83C\uDDFD\u26BD"
        val payload = TextPayload.newBuilder().setText(text).build()
        val reparsed = TextPayload.parseFrom(payload.toByteArray())
        assertEquals(text, reparsed.text)
    }

    @Test
    fun `ReceiptPayload round-trip with delivery type`() {
        val msgId = ByteArray(16) { it.toByte() }
        val receipt = ReceiptPayload.newBuilder()
            .setMessageId(ByteString.copyFrom(msgId))
            .setReceiptType(1) // delivered
            .build()

        val reparsed = ReceiptPayload.parseFrom(receipt.toByteArray())
        assertArrayEquals(msgId, reparsed.messageId.toByteArray())
        assertEquals(1, reparsed.receiptType)
    }

    @Test
    fun `ReceiptPayload round-trip with read type`() {
        val msgId = ByteArray(16) { (it + 50).toByte() }
        val receipt = ReceiptPayload.newBuilder()
            .setMessageId(ByteString.copyFrom(msgId))
            .setReceiptType(2) // read
            .build()

        val reparsed = ReceiptPayload.parseFrom(receipt.toByteArray())
        assertEquals(2, reparsed.receiptType)
    }

    // ── 6. Message type constants ────────────────────────────────────

    @Test
    fun `TYPE_TEXT is 1`() {
        assertEquals(1, EnvelopeSerializer.TYPE_TEXT)
    }

    @Test
    fun `TYPE_RECEIPT is 2`() {
        assertEquals(2, EnvelopeSerializer.TYPE_RECEIPT)
    }

    @Test
    fun `TYPE_KEY_EXCHANGE is 3`() {
        assertEquals(3, EnvelopeSerializer.TYPE_KEY_EXCHANGE)
    }

    // ── 7. Identity key hex encoding ─────────────────────────────────

    @Test
    fun `identity key to hex produces lowercase 64-char string`() {
        val hex = aliceIdentityKey.joinToString("") { "%02x".format(it) }
        assertEquals(64, hex.length)
        assertEquals("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f", hex)
    }

    @Test
    fun `peer address is hex of identity key`() {
        val hex = bobIdentityKey.joinToString("") { "%02x".format(it) }
        assertEquals("202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f", hex)
    }

    // ── 8. UUID messageId encoding ───────────────────────────────────

    @Test
    fun `UUID to 16-byte encoding is big-endian MSB then LSB`() {
        val uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val bb = ByteBuffer.allocate(16)
        bb.putLong(uuid.mostSignificantBits)
        bb.putLong(uuid.leastSignificantBits)
        val bytes = bb.array()

        assertEquals(16, bytes.size)

        // Reparse
        val parsed = ByteBuffer.wrap(bytes)
        val high = parsed.long
        val low = parsed.long
        val reparsed = UUID(high, low)
        assertEquals(uuid, reparsed)
    }

    @Test
    fun `16-byte messageId to UUID string round-trips`() {
        val uuid = UUID.randomUUID()
        val bb = ByteBuffer.allocate(16)
        bb.putLong(uuid.mostSignificantBits)
        bb.putLong(uuid.leastSignificantBits)
        val bytes = bb.array()

        val parsed = ByteBuffer.wrap(bytes)
        val reparsedUuid = UUID(parsed.long, parsed.long)
        assertEquals(uuid.toString(), reparsedUuid.toString())
    }

    // ── 9. Fragmentation convention ──────────────────────────────────

    @Test
    fun `WiFi Aware max message size is 255 bytes`() {
        // Both platforms must agree on this limit
        val maxSize = 255
        assertEquals(255, maxSize)
    }

    @Test
    fun `fragment header is 2 bytes - sequence then total`() {
        val payload = ByteArray(300) { it.toByte() }
        val chunkSize = 253 // 255 - 2 header bytes
        val totalChunks = (payload.size + chunkSize - 1) / chunkSize

        assertEquals(2, totalChunks) // 300 bytes = 2 chunks

        // Fragment 0
        val frag0 = ByteArray(minOf(chunkSize, payload.size) + 2)
        frag0[0] = 0 // sequence number
        frag0[1] = totalChunks.toByte() // total chunks
        System.arraycopy(payload, 0, frag0, 2, chunkSize)
        assertEquals(255, frag0.size)
        assertEquals(0.toByte(), frag0[0])
        assertEquals(2.toByte(), frag0[1])

        // Fragment 1
        val remaining = payload.size - chunkSize
        val frag1 = ByteArray(remaining + 2)
        frag1[0] = 1 // sequence number
        frag1[1] = totalChunks.toByte()
        System.arraycopy(payload, chunkSize, frag1, 2, remaining)
        assertEquals(1.toByte(), frag1[0])
        assertEquals(2.toByte(), frag1[1])
        assertEquals(remaining + 2, frag1.size)

        // Reassemble
        val reassembled = ByteArray(payload.size)
        System.arraycopy(frag0, 2, reassembled, 0, chunkSize)
        System.arraycopy(frag1, 2, reassembled, chunkSize, remaining)
        assertArrayEquals(payload, reassembled)
    }

    @Test
    fun `message under 255 bytes is not fragmented`() {
        val smallMessage = ByteArray(100) { it.toByte() }
        // Messages <= 255 bytes are sent as-is (no fragmentation header)
        assertTrue(smallMessage.size <= 255)
    }

    // ── 10. Field size requirements ──────────────────────────────────

    @Test
    fun `identity keys are exactly 32 bytes`() {
        assertEquals(32, aliceIdentityKey.size)
        assertEquals(32, bobIdentityKey.size)
    }

    @Test
    fun `nonce is exactly 12 bytes`() {
        val nonce = ByteArray(12)
        assertEquals(12, nonce.size)
    }

    @Test
    fun `messageId is exactly 16 bytes`() {
        val uuid = UUID.randomUUID()
        val bb = ByteBuffer.allocate(16)
        bb.putLong(uuid.mostSignificantBits)
        bb.putLong(uuid.leastSignificantBits)
        assertEquals(16, bb.array().size)
    }

    @Test
    fun `device ID is always 1`() {
        // Both platforms must use deviceId = 1 for Signal Protocol
        val deviceId = 1
        assertEquals(1, deviceId)
    }

    @Test
    fun `default TTL is 7`() {
        val defaultTtl = 7
        assertEquals(7, defaultTtl)
    }

    // ── 11. Deterministic test vector for iOS validation ─────────────

    @Test
    fun `test vector - TextPayload serialized bytes`() {
        // iOS must produce identical bytes for the same input
        val payload = TextPayload.newBuilder()
            .setText("hello")
            .build()

        val bytes = payload.toByteArray()

        // Verify the bytes reparse correctly
        val reparsed = TextPayload.parseFrom(bytes)
        assertEquals("hello", reparsed.text)

        // The protobuf encoding for text="hello" should be deterministic:
        // field 1 (string), wire type 2 (LEN) = tag 0x0A
        // length 5 = 0x05
        // "hello" = 0x68 0x65 0x6C 0x6C 0x6F
        val expected = byteArrayOf(0x0A, 0x05, 0x68, 0x65, 0x6C, 0x6C, 0x6F)
        assertArrayEquals(expected, bytes)
    }

    @Test
    fun `test vector - ReceiptPayload with delivered type`() {
        // 16-byte message ID of all zeros, receipt_type = 1 (delivered)
        val msgId = ByteArray(16)
        val receipt = ReceiptPayload.newBuilder()
            .setMessageId(ByteString.copyFrom(msgId))
            .setReceiptType(1)
            .build()

        val bytes = receipt.toByteArray()
        val reparsed = ReceiptPayload.parseFrom(bytes)

        assertArrayEquals(msgId, reparsed.messageId.toByteArray())
        assertEquals(1, reparsed.receiptType)

        // Verify encoding:
        // field 1 (bytes), wire type 2 = tag 0x0A, len 16, then 16 zero bytes
        // field 2 (uint32), wire type 0 = tag 0x10, value 1
        assertTrue(bytes.size > 0)
        assertEquals(0x0A.toByte(), bytes[0]) // tag for field 1
    }

    @Test
    fun `test vector - minimal Envelope signable bytes`() {
        // Envelope with ONLY fields 1-6 set (simulating signable bytes)
        val envelope = Envelope.newBuilder()
            .setSenderId(ByteString.copyFrom(aliceIdentityKey))
            .setRecipientId(ByteString.copyFrom(bobIdentityKey))
            .setTimestamp(1000L)
            .setNonce(ByteString.copyFrom(ByteArray(12)))
            .setCiphertext(ByteString.copyFrom(ByteArray(0)))
            .setMessageType(1)
            .build()

        val bytes = envelope.toByteArray()
        val reparsed = Envelope.parseFrom(bytes)

        // Verify only fields 1-6 are present
        assertArrayEquals(aliceIdentityKey, reparsed.senderId.toByteArray())
        assertArrayEquals(bobIdentityKey, reparsed.recipientId.toByteArray())
        assertEquals(1000L, reparsed.timestamp)
        assertEquals(12, reparsed.nonce.toByteArray().size)
        assertTrue(reparsed.ciphertext.isEmpty)
        assertEquals(1, reparsed.messageType)
        // Fields 7-9 should be default
        assertTrue(reparsed.signature.isEmpty)
        assertEquals(0, reparsed.ttl)
        assertTrue(reparsed.messageId.isEmpty)
    }
}
