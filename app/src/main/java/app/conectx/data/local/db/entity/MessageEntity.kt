package app.conectx.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val squadId: String,
    val authorId: String,
    val authorName: String,
    val text: String,
    val lamportClock: Long,
    val timestamp: Long,
    val isSystem: Boolean = false
)
