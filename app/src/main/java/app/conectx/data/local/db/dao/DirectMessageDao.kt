package app.conectx.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.conectx.data.local.db.entity.DirectMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DirectMessageDao {

    @Query("SELECT * FROM direct_messages WHERE peerId = :peerId ORDER BY timestamp ASC")
    fun getMessagesForPeer(peerId: String): Flow<List<DirectMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: DirectMessageEntity)

    @Query("SELECT COUNT(*) FROM direct_messages WHERE id = :id")
    suspend fun exists(id: String): Int

    @Query("UPDATE direct_messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)
}
