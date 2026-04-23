package app.conectx.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_records")
data class SyncRecordEntity(
    @PrimaryKey val id: String,
    val squadId: String,
    val authorId: String,
    val lamportClock: Long,
    val timestamp: Long,
    val type: String,
    val payload: ByteArray,
    val signature: ByteArray,
    val firebaseSyncedAt: Long? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SyncRecordEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
