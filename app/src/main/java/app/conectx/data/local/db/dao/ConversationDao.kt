package app.conectx.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.conectx.data.local.db.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations ORDER BY lastMessageTimestamp DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE peerId = :peerId")
    suspend fun getConversation(peerId: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ConversationEntity)

    @Query("""
        UPDATE conversations
        SET lastMessageText = :text, lastMessageTimestamp = :timestamp, unreadCount = unreadCount + 1
        WHERE peerId = :peerId
    """)
    suspend fun updateLastMessage(peerId: String, text: String, timestamp: Long)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE peerId = :peerId")
    suspend fun markRead(peerId: String)

    @Query("UPDATE conversations SET peerDisplayName = :displayName WHERE peerId = :peerId")
    suspend fun updateDisplayName(peerId: String, displayName: String)

    @Query("DELETE FROM conversations WHERE peerId = :peerId")
    suspend fun delete(peerId: String)
}
