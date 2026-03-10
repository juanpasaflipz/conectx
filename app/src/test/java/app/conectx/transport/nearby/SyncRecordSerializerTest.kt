package app.conectx.transport.nearby

import app.conectx.domain.model.RecordType
import app.conectx.domain.model.SyncRecord
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncRecordSerializerTest {

    @Test
    fun `round-trip with all fields populated`() {
        val original = SyncRecord(
            id = "abc-123",
            squadId = "squad-42",
            authorId = "author-7",
            lamportClock = 99L,
            timestamp = 1717000000000L,
            type = RecordType.CHAT,
            payload = "hello world".toByteArray(),
            signature = byteArrayOf(10, 20, 30, 40, 50)
        )

        val bytes = SyncRecordSerializer.serialize(original)
        val restored = SyncRecordSerializer.deserialize(bytes)

        assertEquals(original.id, restored.id)
        assertEquals(original.squadId, restored.squadId)
        assertEquals(original.authorId, restored.authorId)
        assertEquals(original.lamportClock, restored.lamportClock)
        assertEquals(original.timestamp, restored.timestamp)
        assertEquals(original.type, restored.type)
        assertArrayEquals(original.payload, restored.payload)
        assertArrayEquals(original.signature, restored.signature)
    }

    @Test
    fun `round-trip with empty payload and signature`() {
        val original = SyncRecord(
            id = "empty-record",
            squadId = "_sync",
            authorId = "peer-1",
            lamportClock = 0L,
            timestamp = 1717000000000L,
            type = RecordType.SYNC_OFFER,
            payload = ByteArray(0),
            signature = ByteArray(0)
        )

        val bytes = SyncRecordSerializer.serialize(original)
        val restored = SyncRecordSerializer.deserialize(bytes)

        assertEquals(original.id, restored.id)
        assertEquals(original.type, restored.type)
        assertEquals(0, restored.payload.size)
        assertEquals(0, restored.signature.size)
    }

    @Test
    fun `round-trip with large signature bytes`() {
        // Simulate a real Ed25519 signature (64 bytes)
        val sig = ByteArray(64) { it.toByte() }
        val original = SyncRecord(
            id = "signed-msg",
            squadId = "squad-1",
            authorId = "author-1",
            lamportClock = 42L,
            timestamp = 1717000000000L,
            type = RecordType.CHAT,
            payload = "signed message".toByteArray(),
            signature = sig
        )

        val bytes = SyncRecordSerializer.serialize(original)
        val restored = SyncRecordSerializer.deserialize(bytes)

        assertArrayEquals(sig, restored.signature)
        assertArrayEquals(original.payload, restored.payload)
    }

    @Test
    fun `round-trip preserves all RecordTypes`() {
        for (type in RecordType.entries) {
            val original = SyncRecord(
                id = "type-test-${type.name}",
                squadId = "s1",
                authorId = "a1",
                lamportClock = 1L,
                timestamp = 1000L,
                type = type,
                payload = ByteArray(0),
                signature = ByteArray(0)
            )

            val restored = SyncRecordSerializer.deserialize(
                SyncRecordSerializer.serialize(original)
            )
            assertEquals(type, restored.type)
        }
    }
}
