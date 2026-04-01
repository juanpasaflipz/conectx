package app.conectx.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "direct_messages")
data class DirectMessageEntity(
    @PrimaryKey val id: String,
    val peerId: String,
    val text: String,
    val isOutgoing: Boolean,
    val timestamp: Long,
    val status: String = "sent" // sent, delivered, read
)
