package app.conectx.sync

import app.conectx.data.local.db.dao.SyncRecordDao
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Lamport clocks per squad.
 *
 * Each squad has its own logical clock counter. The rules:
 * - On send:    clock = localClock + 1
 * - On receive: localClock = max(localClock, receivedClock) + 1
 *
 * Clocks are cached in memory and bootstrapped from Room on first access.
 * The authoritative value is always the max across all stored records for
 * that squad — so even if the app is killed, the clock is recovered.
 */
@Singleton
class LamportClock @Inject constructor(
    private val syncRecordDao: SyncRecordDao
) {
    // squadId → current clock value
    private val clocks = ConcurrentHashMap<String, Long>()

    /**
     * Increments and returns the next clock tick for a squad.
     * Used when creating a new local record.
     */
    suspend fun tick(squadId: String): Long {
        val current = getOrLoad(squadId)
        val next = current + 1
        clocks[squadId] = next
        return next
    }

    /**
     * Updates the local clock after receiving a remote record.
     * Returns the new local clock value.
     */
    suspend fun receive(squadId: String, remoteClock: Long): Long {
        val current = getOrLoad(squadId)
        val next = maxOf(current, remoteClock) + 1
        clocks[squadId] = next
        return next
    }

    /**
     * Returns the current clock value for a squad without incrementing.
     * Used when building SYNC_OFFERs to tell peers how far we've synced.
     */
    suspend fun current(squadId: String): Long = getOrLoad(squadId)

    /**
     * Returns a snapshot of all known squad clocks.
     * Used to build the SYNC_OFFER payload.
     */
    suspend fun allClocks(): Map<String, Long> {
        val squadIds = syncRecordDao.getAllSquadIds()
        return squadIds.associateWith { getOrLoad(it) }
    }

    private suspend fun getOrLoad(squadId: String): Long {
        return clocks.getOrPut(squadId) {
            syncRecordDao.getLatestClock(squadId) ?: 0L
        }
    }
}
