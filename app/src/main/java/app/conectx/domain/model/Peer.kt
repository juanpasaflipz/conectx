package app.conectx.domain.model

data class Peer(
    val id: String,
    val displayName: String,
    val squadId: String,
    val isConnected: Boolean,
    val lastSeen: Long
)
