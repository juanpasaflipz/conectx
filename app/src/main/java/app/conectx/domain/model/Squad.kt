package app.conectx.domain.model

data class Squad(
    val id: String,
    val name: String,
    val inviteCode: String,
    val memberIds: List<String>,
    val createdAt: Long
)
