package app.conectx.domain.model

data class Message(
    val id: String,
    val squadId: String,
    val authorId: String,
    val authorName: String,
    val text: String,
    val lamportClock: Long,
    val timestamp: Long,
    val isSystem: Boolean = false
)
