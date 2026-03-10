package app.conectx.sync

import app.conectx.data.local.db.dao.SyncRecordDao
import app.conectx.domain.model.SyncRecord
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deduplication and ordering for incoming sync records.
 *
 * - Dedup:  UUID-based. If a record with the same ID exists in Room, drop it.
 * - Order:  Lamport clock is the canonical ordering for display and conflict
 *           resolution. Wall-clock timestamps are shown to the user but never
 *           used for ordering decisions (clocks drift; Lamport clocks don't).
 */
@Singleton
class ConflictResolver @Inject constructor(
    private val syncRecordDao: SyncRecordDao
) {
    /**
     * Returns true if this record has NOT been seen before (i.e. is new).
     */
    suspend fun isNew(record: SyncRecord): Boolean {
        return !syncRecordDao.exists(record.id)
    }

    /**
     * Sorts records by Lamport clock (causal order).
     * Ties broken by UUID for deterministic ordering across devices.
     */
    fun sortByLamport(records: List<SyncRecord>): List<SyncRecord> {
        return records.sortedWith(compareBy<SyncRecord> { it.lamportClock }.thenBy { it.id })
    }
}
