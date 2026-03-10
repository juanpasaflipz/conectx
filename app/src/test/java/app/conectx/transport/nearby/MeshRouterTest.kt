package app.conectx.transport.nearby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MeshRouterTest {

    private lateinit var router: MeshRouter

    @Before
    fun setup() {
        router = MeshRouter()
    }

    @Test
    fun `markSeen returns true first time`() {
        assertTrue(router.markSeen("record-1"))
    }

    @Test
    fun `markSeen returns false second time`() {
        router.markSeen("record-1")
        assertFalse(router.markSeen("record-1"))
    }

    @Test
    fun `markSeen tracks different IDs independently`() {
        assertTrue(router.markSeen("record-1"))
        assertTrue(router.markSeen("record-2"))
        assertFalse(router.markSeen("record-1"))
        assertFalse(router.markSeen("record-2"))
    }

    @Test
    fun `relayTargets excludes sender`() {
        val allConnected = setOf("A", "B", "C", "D")
        val targets = router.relayTargets("B", allConnected)

        assertEquals(setOf("A", "C", "D"), targets)
    }

    @Test
    fun `relayTargets with single connected returns empty when sender is only peer`() {
        val targets = router.relayTargets("A", setOf("A"))
        assertTrue(targets.isEmpty())
    }

    @Test
    fun `relayTargets with empty connected set`() {
        val targets = router.relayTargets("A", emptySet())
        assertTrue(targets.isEmpty())
    }

    @Test
    fun `eviction at MAX_SEEN clears set and accepts again`() {
        // Fill up to MAX_SEEN (10,000)
        for (i in 0 until 10_000) {
            router.markSeen("record-$i")
        }

        // record-0 was seen but after eviction at capacity it should be accepted again
        // The 10,001st call triggers clear, then adds successfully
        assertTrue(router.markSeen("record-overflow"))

        // After clear, previously seen IDs are accepted again
        assertTrue(router.markSeen("record-0"))
    }
}
