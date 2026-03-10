package app.conectx.sync

import app.conectx.data.local.db.dao.SyncRecordDao
import app.conectx.domain.model.RecordType
import app.conectx.domain.model.SyncRecord
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConflictResolverTest {

    private lateinit var dao: SyncRecordDao
    private lateinit var resolver: ConflictResolver

    @Before
    fun setup() {
        dao = mockk()
        resolver = ConflictResolver(dao)
    }

    @Test
    fun `isNew returns true for unseen record`() = runBlocking {
        coEvery { dao.exists("new-id") } returns false

        val record = makeRecord("new-id")
        assertTrue(resolver.isNew(record))
    }

    @Test
    fun `isNew returns false for existing record`() = runBlocking {
        coEvery { dao.exists("seen-id") } returns true

        val record = makeRecord("seen-id")
        assertFalse(resolver.isNew(record))
    }

    @Test
    fun `sortByLamport orders by clock ascending`() {
        val r1 = makeRecord("a", lamportClock = 3)
        val r2 = makeRecord("b", lamportClock = 1)
        val r3 = makeRecord("c", lamportClock = 2)

        val sorted = resolver.sortByLamport(listOf(r1, r2, r3))

        assertEquals(1L, sorted[0].lamportClock)
        assertEquals(2L, sorted[1].lamportClock)
        assertEquals(3L, sorted[2].lamportClock)
    }

    @Test
    fun `sortByLamport breaks ties by UUID`() {
        val r1 = makeRecord("bbb", lamportClock = 5)
        val r2 = makeRecord("aaa", lamportClock = 5)
        val r3 = makeRecord("ccc", lamportClock = 5)

        val sorted = resolver.sortByLamport(listOf(r1, r2, r3))

        assertEquals("aaa", sorted[0].id)
        assertEquals("bbb", sorted[1].id)
        assertEquals("ccc", sorted[2].id)
    }

    @Test
    fun `sortByLamport with empty list`() {
        val sorted = resolver.sortByLamport(emptyList())
        assertTrue(sorted.isEmpty())
    }

    private fun makeRecord(id: String, lamportClock: Long = 0L) = SyncRecord(
        id = id,
        squadId = "squad-1",
        authorId = "author-1",
        lamportClock = lamportClock,
        timestamp = 1000L,
        type = RecordType.CHAT,
        payload = ByteArray(0),
        signature = ByteArray(0)
    )
}
