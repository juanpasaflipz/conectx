package app.conectx.sync

import app.conectx.data.local.db.dao.SyncRecordDao
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LamportClockTest {

    private lateinit var dao: SyncRecordDao
    private lateinit var clock: LamportClock

    @Before
    fun setup() {
        dao = mockk()
        // Default: no existing records in DB
        coEvery { dao.getLatestClock(any()) } returns null
        coEvery { dao.getAllSquadIds() } returns emptyList()
        clock = LamportClock(dao)
    }

    @Test
    fun `tick increments monotonically`() = runBlocking {
        val t1 = clock.tick("squad-1")
        val t2 = clock.tick("squad-1")
        val t3 = clock.tick("squad-1")

        assertEquals(1L, t1)
        assertEquals(2L, t2)
        assertEquals(3L, t3)
    }

    @Test
    fun `current does not increment`() = runBlocking {
        clock.tick("squad-1") // → 1
        val c1 = clock.current("squad-1")
        val c2 = clock.current("squad-1")

        assertEquals(1L, c1)
        assertEquals(1L, c2)
    }

    @Test
    fun `receive with remote greater than local`() = runBlocking {
        clock.tick("squad-1") // local = 1
        val result = clock.receive("squad-1", 10L) // max(1, 10) + 1 = 11

        assertEquals(11L, result)
        assertEquals(11L, clock.current("squad-1"))
    }

    @Test
    fun `receive with remote less than local`() = runBlocking {
        // Build up local clock to 5
        repeat(5) { clock.tick("squad-1") }
        assertEquals(5L, clock.current("squad-1"))

        val result = clock.receive("squad-1", 2L) // max(5, 2) + 1 = 6

        assertEquals(6L, result)
    }

    @Test
    fun `receive with remote equal to local`() = runBlocking {
        repeat(3) { clock.tick("squad-1") } // local = 3
        val result = clock.receive("squad-1", 3L) // max(3, 3) + 1 = 4

        assertEquals(4L, result)
    }

    @Test
    fun `separate squads have independent clocks`() = runBlocking {
        clock.tick("squad-A") // A = 1
        clock.tick("squad-A") // A = 2
        clock.tick("squad-B") // B = 1

        assertEquals(2L, clock.current("squad-A"))
        assertEquals(1L, clock.current("squad-B"))
    }

    @Test
    fun `bootstrap from DAO on first access`() = runBlocking {
        coEvery { dao.getLatestClock("squad-1") } returns 42L

        val fresh = LamportClock(dao)
        val current = fresh.current("squad-1")

        assertEquals(42L, current)
    }

    @Test
    fun `tick after bootstrap continues from DAO value`() = runBlocking {
        coEvery { dao.getLatestClock("squad-1") } returns 100L

        val fresh = LamportClock(dao)
        val next = fresh.tick("squad-1")

        assertEquals(101L, next)
    }

    @Test
    fun `allClocks returns snapshot of all squads`() = runBlocking {
        coEvery { dao.getAllSquadIds() } returns listOf("s1", "s2")
        coEvery { dao.getLatestClock("s1") } returns 5L
        coEvery { dao.getLatestClock("s2") } returns 10L

        val fresh = LamportClock(dao)
        val clocks = fresh.allClocks()

        assertEquals(2, clocks.size)
        assertEquals(5L, clocks["s1"])
        assertEquals(10L, clocks["s2"])
    }
}
