package app.conectx.transport.nearby

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gossip-based message relay. Tracks which record IDs have been seen
 * to prevent infinite relay loops in the mesh.
 *
 * When device A receives a message from B, NearbyPlugin calls [markSeen].
 * If it returns true (first time), the message is relayed to all other
 * connected peers (C, D, ...). If false, it's a duplicate — drop it.
 *
 * Capped at [MAX_SEEN] entries. When full, the set is cleared.
 * This is safe because the storage layer (Room) deduplicates by UUID
 * anyway — worst case a message gets relayed twice after eviction.
 */
@Singleton
class MeshRouter @Inject constructor() {

    companion object {
        private const val MAX_SEEN = 10_000
    }

    private val seenIds: MutableSet<String> =
        ConcurrentHashMap.newKeySet(MAX_SEEN)

    /** Counter for messages relayed through this device */
    private val _relayedCount = AtomicLong(0)
    val relayedCount: Long get() = _relayedCount.get()

    /**
     * Returns true if this is the first time we've seen [recordId].
     * Marks it as seen for future calls.
     */
    fun markSeen(recordId: String): Boolean {
        if (seenIds.size >= MAX_SEEN) {
            seenIds.clear()
        }
        return seenIds.add(recordId)
    }

    /**
     * Returns the endpoint IDs that should receive a relayed message,
     * i.e. all connected endpoints except the one that sent it.
     */
    fun relayTargets(
        senderEndpointId: String,
        allConnected: Set<String>
    ): Set<String> {
        val targets = allConnected - senderEndpointId
        if (targets.isNotEmpty()) _relayedCount.incrementAndGet()
        return targets
    }
}
