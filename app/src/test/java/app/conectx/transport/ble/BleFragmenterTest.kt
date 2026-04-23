package app.conectx.transport.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class BleFragmenterTest {

    private lateinit var reassembler: BleFragmenter.Reassembler

    @Before
    fun setUp() {
        reassembler = BleFragmenter.Reassembler()
    }

    // ── fragment() tests ──────────────────────────────────────────────

    @Test
    fun `single fragment when data fits within MTU`() {
        val data = ByteArray(10) { it.toByte() }
        val fragments = BleFragmenter.fragment(data, 20) // 20 - 2 header = 18 bytes for data

        assertEquals(1, fragments.size)
        // Last-fragment flag should be set
        assertEquals(0x80.toByte(), (fragments[0][0].toInt() and 0x80).toByte())
        // Seq should be 0
        assertEquals(0.toByte(), fragments[0][1])
        // Payload should match original data
        assertArrayEquals(data, fragments[0].copyOfRange(2, fragments[0].size))
    }

    @Test
    fun `multiple fragments when data exceeds MTU`() {
        val data = ByteArray(50) { it.toByte() }
        val mtu = 12 // 12 - 2 header = 10 bytes per chunk → 5 fragments

        val fragments = BleFragmenter.fragment(data, mtu)

        assertEquals(5, fragments.size)

        // First 4 fragments: no last flag, sequential seq numbers
        for (i in 0 until 4) {
            assertEquals(0.toByte(), (fragments[i][0].toInt() and 0x80).toByte())
            assertEquals(i.toByte(), fragments[i][1])
            assertEquals(12, fragments[i].size) // full MTU
        }

        // Last fragment: last flag set, seq 4
        assertEquals(0x80.toByte(), (fragments[4][0].toInt() and 0x80).toByte())
        assertEquals(4.toByte(), fragments[4][1])
        assertEquals(12, fragments[4].size) // exactly 10 bytes remaining + 2 header
    }

    @Test
    fun `exact MTU boundary produces single fragment`() {
        val chunkSize = 18 // MTU 20 - 2 header
        val data = ByteArray(chunkSize) { 0xAB.toByte() }

        val fragments = BleFragmenter.fragment(data, 20)

        assertEquals(1, fragments.size)
        assertEquals(0x80.toByte(), (fragments[0][0].toInt() and 0x80).toByte())
    }

    @Test
    fun `one byte over MTU boundary produces two fragments`() {
        val chunkSize = 18
        val data = ByteArray(chunkSize + 1) { 0xCD.toByte() }

        val fragments = BleFragmenter.fragment(data, 20)

        assertEquals(2, fragments.size)
        // First fragment: 18 data bytes
        assertEquals(0.toByte(), (fragments[0][0].toInt() and 0x80).toByte())
        assertEquals(20, fragments[0].size)
        // Second fragment: 1 data byte
        assertEquals(0x80.toByte(), (fragments[1][0].toInt() and 0x80).toByte())
        assertEquals(3, fragments[1].size) // 2 header + 1 data
    }

    @Test
    fun `empty data produces single empty fragment`() {
        val data = ByteArray(0)
        val fragments = BleFragmenter.fragment(data, 20)

        assertEquals(1, fragments.size)
        assertEquals(2, fragments[0].size) // header only
        assertEquals(0x80.toByte(), (fragments[0][0].toInt() and 0x80).toByte())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `MTU too small throws`() {
        BleFragmenter.fragment(ByteArray(10), BleFragmenter.HEADER_SIZE)
    }

    // ── Reassembler tests ─────────────────────────────────────────────

    @Test
    fun `reassemble single fragment`() {
        val data = ByteArray(10) { it.toByte() }
        val fragments = BleFragmenter.fragment(data, 20)

        val result = reassembler.receive("AA:BB:CC:DD:EE:FF", fragments[0])

        assertNotNull(result)
        assertArrayEquals(data, result)
    }

    @Test
    fun `reassemble multiple fragments`() {
        val data = ByteArray(50) { (it * 3).toByte() }
        val fragments = BleFragmenter.fragment(data, 12)

        val device = "11:22:33:44:55:66"

        // First 4 fragments return null (more expected)
        for (i in 0 until fragments.size - 1) {
            val result = reassembler.receive(device, fragments[i])
            assertNull("Fragment $i should not complete reassembly", result)
        }

        // Last fragment completes reassembly
        val result = reassembler.receive(device, fragments.last())
        assertNotNull(result)
        assertArrayEquals(data, result)
    }

    @Test
    fun `round-trip fragment and reassemble`() {
        val original = "Hello from Conectx mesh!".toByteArray(Charsets.UTF_8)

        // Fragment with a small MTU to force multiple chunks
        val fragments = BleFragmenter.fragment(original, 8)
        assert(fragments.size > 1) { "Expected multiple fragments" }

        val device = "AA:BB:CC:DD:EE:01"
        var result: ByteArray? = null
        for (frag in fragments) {
            result = reassembler.receive(device, frag)
        }

        assertNotNull(result)
        assertArrayEquals(original, result)
        assertEquals("Hello from Conectx mesh!", String(result!!, Charsets.UTF_8))
    }

    @Test
    fun `interleaved fragments from different devices`() {
        val dataA = ByteArray(30) { 0xAA.toByte() }
        val dataB = ByteArray(30) { 0xBB.toByte() }
        val fragsA = BleFragmenter.fragment(dataA, 12)
        val fragsB = BleFragmenter.fragment(dataB, 12)

        // Interleave: A0, B0, A1, B1, A2(last), B2(last)
        assertNull(reassembler.receive("deviceA", fragsA[0]))
        assertNull(reassembler.receive("deviceB", fragsB[0]))
        assertNull(reassembler.receive("deviceA", fragsA[1]))
        assertNull(reassembler.receive("deviceB", fragsB[1]))

        val resultA = reassembler.receive("deviceA", fragsA[2])
        val resultB = reassembler.receive("deviceB", fragsB[2])

        assertNotNull(resultA)
        assertNotNull(resultB)
        assertArrayEquals(dataA, resultA)
        assertArrayEquals(dataB, resultB)
    }

    @Test
    fun `out-of-order fragment resets buffer`() {
        val data = ByteArray(30) { it.toByte() }
        val fragments = BleFragmenter.fragment(data, 12)
        val device = "test-device"

        // Send fragment 0
        assertNull(reassembler.receive(device, fragments[0]))

        // Skip to fragment 2 (seq mismatch) — should reset and discard
        val result = reassembler.receive(device, fragments[2])
        assertNull(result)
    }

    @Test
    fun `clear removes all buffers`() {
        val data = ByteArray(30) { it.toByte() }
        val fragments = BleFragmenter.fragment(data, 12)

        // Start receiving fragments
        reassembler.receive("device1", fragments[0])

        // Clear everything
        reassembler.clear()

        // Sending remaining fragments after clear should not produce a result
        // because the buffer was cleared and seq 1 doesn't match expected 0
        val result = reassembler.receive("device1", fragments[1])
        assertNull(result)
    }

    @Test
    fun `large message round-trip with minimum MTU`() {
        // Simulate worst-case: 20-byte usable payload (23 MTU - 3 ATT header)
        val data = ByteArray(512) { (it % 256).toByte() }
        val mtu = BleConstants.DEFAULT_PAYLOAD_SIZE // 20 bytes

        val fragments = BleFragmenter.fragment(data, mtu)
        // 20 - 2 header = 18 bytes per chunk → ceil(512/18) = 29 fragments
        assertEquals(29, fragments.size)

        val device = "low-mtu-device"
        var result: ByteArray? = null
        for (frag in fragments) {
            result = reassembler.receive(device, frag)
        }

        assertNotNull(result)
        assertArrayEquals(data, result)
    }
}
