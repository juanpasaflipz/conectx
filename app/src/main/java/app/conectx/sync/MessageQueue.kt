package app.conectx.sync

import app.conectx.domain.model.SyncRecord
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline queue for outgoing records. When no transport is available
 * (all plugins report unavailable), records are parked here.
 *
 * The SyncEngine flushes this queue whenever a peer connection
 * is established (detected via PeerTracker).
 */
@Singleton
class MessageQueue @Inject constructor() {

    private val pending = ConcurrentLinkedQueue<SyncRecord>()

    val hasPending: Boolean get() = pending.isNotEmpty()

    fun enqueue(record: SyncRecord) {
        pending.add(record)
    }

    /**
     * Atomically drains all pending records and returns them.
     * After this call the queue is empty.
     */
    fun drainAll(): List<SyncRecord> {
        val drained = mutableListOf<SyncRecord>()
        while (true) {
            val record = pending.poll() ?: break
            drained.add(record)
        }
        return drained
    }
}
