package app.conectx.sync

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests the WiFi Aware protocol prefix byte convention.
 *
 * WiFi Aware messages are prefixed with a type byte:
 * - 0x01 = PreKeyBundle (Protobuf)
 * - 0x02 = Envelope (Protobuf)
 *
 * Both Android and iOS must use identical prefix bytes for interop.
 */
class ProtocolPrefixTest {

    companion object {
        // These must match SyncEngine companion constants
        private const val PREFIX_PRE_KEY_BUNDLE: Byte = 0x01
        private const val PREFIX_ENVELOPE: Byte = 0x02
    }

    @Test
    fun `prefix bytes are distinct`() {
        assert(PREFIX_PRE_KEY_BUNDLE != PREFIX_ENVELOPE)
    }

    @Test
    fun `pre-key bundle prefix is 0x01`() {
        assertEquals(0x01.toByte(), PREFIX_PRE_KEY_BUNDLE)
    }

    @Test
    fun `envelope prefix is 0x02`() {
        assertEquals(0x02.toByte(), PREFIX_ENVELOPE)
    }

    @Test
    fun `prefixed message preserves payload after stripping prefix`() {
        val payload = byteArrayOf(10, 20, 30, 40, 50)
        val prefixed = ByteArray(payload.size + 1)
        prefixed[0] = PREFIX_ENVELOPE
        System.arraycopy(payload, 0, prefixed, 1, payload.size)

        // Verify prefix
        assertEquals(PREFIX_ENVELOPE, prefixed[0])

        // Extract payload
        val extracted = prefixed.copyOfRange(1, prefixed.size)
        assertEquals(payload.size, extracted.size)
        payload.forEachIndexed { i, b ->
            assertEquals(b, extracted[i])
        }
    }

    @Test
    fun `empty payload with prefix is single byte`() {
        val prefixed = byteArrayOf(PREFIX_PRE_KEY_BUNDLE)
        assertEquals(1, prefixed.size)
        assertEquals(PREFIX_PRE_KEY_BUNDLE, prefixed[0])

        val extracted = prefixed.copyOfRange(1, prefixed.size)
        assertEquals(0, extracted.size)
    }
}
