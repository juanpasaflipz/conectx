package app.conectx.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val peerId: String,
    val peerDisplayName: String,
    val peerIdentityKey: ByteArray,
    val lastMessageText: String?,
    val lastMessageTimestamp: Long,
    val unreadCount: Int,
    val createdAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConversationEntity) return false
        return peerId == other.peerId
    }
    override fun hashCode(): Int = peerId.hashCode()
}
