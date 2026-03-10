package app.conectx.sync

import app.conectx.domain.model.RecordType
import app.conectx.domain.model.SyncRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MessageQueueTest {

    private lateinit var queue: MessageQueue

    @Before
    fun setup() {
        queue = MessageQueue()
    }

    @Test
    fun `hasPending is false initially`() {
        assertFalse(queue.hasPending)
    }

    @Test
    fun `enqueue makes hasPending true`() {
        queue.enqueue(makeRecord("r1"))
        assertTrue(queue.hasPending)
    }

    @Test
    fun `drainAll returns all in FIFO order`() {
        queue.enqueue(makeRecord("first"))
        queue.enqueue(makeRecord("second"))
        queue.enqueue(makeRecord("third"))

        val drained = queue.drainAll()

        assertEquals(3, drained.size)
        assertEquals("first", drained[0].id)
        assertEquals("second", drained[1].id)
        assertEquals("third", drained[2].id)
    }

    @Test
    fun `drainAll empties the queue`() {
        queue.enqueue(makeRecord("r1"))
        queue.enqueue(makeRecord("r2"))

        queue.drainAll()

        assertFalse(queue.hasPending)
        assertTrue(queue.drainAll().isEmpty())
    }

    @Test
    fun `drainAll on empty queue returns empty list`() {
        val drained = queue.drainAll()
        assertTrue(drained.isEmpty())
    }

    @Test
    fun `enqueue after drain works correctly`() {
        queue.enqueue(makeRecord("r1"))
        queue.drainAll()

        queue.enqueue(makeRecord("r2"))
        assertTrue(queue.hasPending)

        val drained = queue.drainAll()
        assertEquals(1, drained.size)
        assertEquals("r2", drained[0].id)
    }

    private fun makeRecord(id: String) = SyncRecord(
        id = id,
        squadId = "squad-1",
        authorId = "author-1",
        lamportClock = 1L,
        timestamp = 1000L,
        type = RecordType.CHAT,
        payload = ByteArray(0),
        signature = ByteArray(0)
    )
}
