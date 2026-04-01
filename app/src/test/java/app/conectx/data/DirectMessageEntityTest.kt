package app.conectx.data

import app.conectx.data.local.db.entity.DirectMessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectMessageEntityTest {

    @Test
    fun `entity preserves all fields`() {
        val entity = DirectMessageEntity(
            id = "msg-001",
            peerId = "peer-abc",
            text = "Hello",
            isOutgoing = true,
            timestamp = 1234567890L,
            status = "sent"
        )

        assertEquals("msg-001", entity.id)
        assertEquals("peer-abc", entity.peerId)
        assertEquals("Hello", entity.text)
        assertTrue(entity.isOutgoing)
        assertEquals(1234567890L, entity.timestamp)
        assertEquals("sent", entity.status)
    }

    @Test
    fun `default status is sent`() {
        val entity = DirectMessageEntity(
            id = "msg-002",
            peerId = "peer-xyz",
            text = "Test",
            isOutgoing = false,
            timestamp = 0L
        )

        assertEquals("sent", entity.status)
    }

    @Test
    fun `incoming message has isOutgoing false`() {
        val entity = DirectMessageEntity(
            id = "msg-003",
            peerId = "peer-123",
            text = "Incoming",
            isOutgoing = false,
            timestamp = System.currentTimeMillis()
        )

        assertFalse(entity.isOutgoing)
    }

    @Test
    fun `status can be delivered or read`() {
        val delivered = DirectMessageEntity(
            id = "msg-004",
            peerId = "peer-456",
            text = "Check",
            isOutgoing = true,
            timestamp = 0L,
            status = "delivered"
        )
        assertEquals("delivered", delivered.status)

        val read = delivered.copy(status = "read")
        assertEquals("read", read.status)
    }

    @Test
    fun `copy changes only specified fields`() {
        val original = DirectMessageEntity(
            id = "msg-005",
            peerId = "peer-789",
            text = "Original",
            isOutgoing = true,
            timestamp = 100L,
            status = "sent"
        )

        val updated = original.copy(status = "delivered")
        assertEquals("msg-005", updated.id)
        assertEquals("peer-789", updated.peerId)
        assertEquals("Original", updated.text)
        assertEquals("delivered", updated.status)
    }
}
