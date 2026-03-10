package app.conectx.domain.model

data class SyncRecord(
    val id: String,
    val squadId: String,
    val authorId: String,
    val lamportClock: Long,
    val timestamp: Long,
    val type: RecordType,
    val payload: ByteArray,
    val signature: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SyncRecord) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
