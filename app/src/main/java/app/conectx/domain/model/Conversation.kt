package app.conectx.domain.model

/**
 * Represents a 1:1 conversation with a peer.
 * Replaces the squad-based group model for MVP cross-platform messaging.
 */
data class Conversation(
    val peerId: String,
    val peerDisplayName: String,
    val peerIdentityKey: ByteArray,
    val lastMessageText: String?,
    val lastMessageTimestamp: Long,
    val unreadCount: Int,
    val createdAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Conversation) return false
        return peerId == other.peerId
    }

    override fun hashCode(): Int = peerId.hashCode()
}
