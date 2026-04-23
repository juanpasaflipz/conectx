package app.conectx.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.conectx.data.local.db.entity.SquadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SquadDao {

    @Query("SELECT * FROM squads")
    fun getAll(): Flow<List<SquadEntity>>

    @Query("SELECT * FROM squads WHERE id = :id")
    suspend fun getById(id: String): SquadEntity?

    @Query("SELECT * FROM squads WHERE inviteCode = :code")
    suspend fun getByInviteCode(code: String): SquadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(squad: SquadEntity)

    @Query("DELETE FROM squads WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM squads")
    suspend fun getAllOnce(): List<SquadEntity>

    @Query("SELECT COUNT(*) FROM squads")
    suspend fun count(): Int

    @Query("DELETE FROM squads")
    suspend fun deleteAll()
}
