package app.conectx.transport.ble

/**
 * Fragments and reassembles BLE messages for low-MTU connections.
 *
 * When the negotiated MTU is large enough (≥ message size + header), the
 * message is sent as a single fragment. Otherwise it's split into chunks
 * that fit within the ATT payload limit.
 *
 * Header format (2 bytes):
 *   Byte 0 — flags:
 *     bit 7 (0x80): 1 = last fragment, 0 = more fragments follow
 *     bits 0-6: reserved (0)
 *   Byte 1 — sequence number (0-255, wraps)
 */
object BleFragmenter {

    const val HEADER_SIZE = 2
    private const val FLAG_LAST: Byte = 0x80.toByte()

    /**
     * Splits [data] into fragments that each fit within [mtu] bytes
     * (including the 2-byte fragment header).
     *
     * @param data The full message bytes to fragment.
     * @param mtu  The usable ATT payload size (negotiated MTU minus ATT header).
     * @return List of fragment byte arrays, each ≤ [mtu] bytes.
     */
    fun fragment(data: ByteArray, mtu: Int): List<ByteArray> {
        require(mtu > HEADER_SIZE) { "MTU must be > $HEADER_SIZE bytes" }

        val chunkSize = mtu - HEADER_SIZE
        if (data.size <= chunkSize) {
            // Single fragment — set last-fragment flag, seq 0
            val fragment = ByteArray(HEADER_SIZE + data.size)
            fragment[0] = FLAG_LAST
            fragment[1] = 0
            data.copyInto(fragment, HEADER_SIZE)
            return listOf(fragment)
        }

        val fragments = mutableListOf<ByteArray>()
        var offset = 0
        var seq = 0

        while (offset < data.size) {
            val remaining = data.size - offset
            val thisChunk = minOf(remaining, chunkSize)
            val isLast = offset + thisChunk >= data.size

            val fragment = ByteArray(HEADER_SIZE + thisChunk)
            fragment[0] = if (isLast) FLAG_LAST else 0
            fragment[1] = (seq and 0xFF).toByte()
            data.copyInto(fragment, HEADER_SIZE, offset, offset + thisChunk)

            fragments.add(fragment)
            offset += thisChunk
            seq++
        }

        return fragments
    }

    /**
     * Accumulates incoming fragments per device and returns the complete
     * reassembled message when the last fragment arrives.
     *
     * Each device address gets its own reassembly buffer. Buffers that
     * haven't received a fragment within [BleConstants.FRAGMENT_TIMEOUT_MS]
     * are purged on the next [receive] call.
     */
    class Reassembler {

        private data class Buffer(
            val fragments: MutableList<ByteArray> = mutableListOf(),
            var expectedSeq: Int = 0,
            var lastUpdated: Long = System.currentTimeMillis()
        )

        private val buffers = HashMap<String, Buffer>()

        /**
         * Feeds a raw fragment received from [deviceAddress].
         *
         * @return The fully reassembled message bytes if this was the last
         *         fragment, or null if more fragments are expected.
         */
        fun receive(deviceAddress: String, fragment: ByteArray): ByteArray? {
            if (fragment.size < HEADER_SIZE) return null

            purgeStale()

            val flags = fragment[0]
            val seq = fragment[1].toInt() and 0xFF
            val isLast = (flags.toInt() and 0x80) != 0
            val payload = fragment.copyOfRange(HEADER_SIZE, fragment.size)

            val buffer = buffers.getOrPut(deviceAddress) { Buffer() }

            // If seq doesn't match expected, reset buffer (lost fragment or new message)
            if (seq != buffer.expectedSeq) {
                buffer.fragments.clear()
                buffer.expectedSeq = 0
                // If this is seq 0, treat as start of new message; otherwise discard
                if (seq != 0) return null
            }

            buffer.fragments.add(payload)
            buffer.expectedSeq = seq + 1
            buffer.lastUpdated = System.currentTimeMillis()

            if (isLast) {
                buffers.remove(deviceAddress)
                return reassemble(buffer.fragments)
            }

            return null
        }

        /** Removes all partial buffers (e.g. on stop()). */
        fun clear() {
            buffers.clear()
        }

        private fun reassemble(fragments: List<ByteArray>): ByteArray {
            val totalSize = fragments.sumOf { it.size }
            val result = ByteArray(totalSize)
            var offset = 0
            for (frag in fragments) {
                frag.copyInto(result, offset)
                offset += frag.size
            }
            return result
        }

        private fun purgeStale() {
            val now = System.currentTimeMillis()
            buffers.entries.removeAll { (_, buf) ->
                now - buf.lastUpdated > BleConstants.FRAGMENT_TIMEOUT_MS
            }
        }
    }
}
