package app.conectx.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.conectx.data.local.db.entity.SyncRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncRecordDao {

    @Query("SELECT * FROM sync_records WHERE squadId = :squadId ORDER BY lamportClock ASC")
    suspend fun getRecordsForSquad(squadId: String): List<SyncRecordEntity>

    @Query("SELECT * FROM sync_records WHERE squadId = :squadId AND lamportClock > :afterClock ORDER BY lamportClock ASC")
    suspend fun getRecordsAfter(squadId: String, afterClock: Long): List<SyncRecordEntity>

    @Query("SELECT MAX(lamportClock) FROM sync_records WHERE squadId = :squadId")
    suspend fun getLatestClock(squadId: String): Long?

    @Query("SELECT DISTINCT squadId FROM sync_records")
    suspend fun getAllSquadIds(): List<String>

    @Query(
        """
        SELECT * FROM sync_records
        WHERE firebaseSyncedAt IS NULL
          AND type != 'SYNC_OFFER'
        ORDER BY timestamp ASC
        LIMIT :limit
        """
    )
    suspend fun getPendingFirebaseRecords(limit: Int): List<SyncRecordEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: SyncRecordEntity)

    @Query("UPDATE sync_records SET firebaseSyncedAt = :syncedAt WHERE id = :id")
    suspend fun markFirebaseSynced(id: String, syncedAt: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM sync_records WHERE id = :id)")
    suspend fun exists(id: String): Boolean

    @Query("DELETE FROM sync_records")
    suspend fun deleteAll()
}
