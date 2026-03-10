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

    /** Reactive stream of LOCATION records for a squad — UI auto-updates on new pings. */
    @Query("SELECT * FROM sync_records WHERE squadId = :squadId AND type = 'LOCATION' ORDER BY lamportClock DESC")
    fun getLocationPingsForSquad(squadId: String): Flow<List<SyncRecordEntity>>

    @Query("SELECT * FROM sync_records WHERE squadId = :squadId AND lamportClock > :afterClock ORDER BY lamportClock ASC")
    suspend fun getRecordsAfter(squadId: String, afterClock: Long): List<SyncRecordEntity>

    @Query("SELECT MAX(lamportClock) FROM sync_records WHERE squadId = :squadId")
    suspend fun getLatestClock(squadId: String): Long?

    @Query("SELECT DISTINCT squadId FROM sync_records")
    suspend fun getAllSquadIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: SyncRecordEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM sync_records WHERE id = :id)")
    suspend fun exists(id: String): Boolean
}
