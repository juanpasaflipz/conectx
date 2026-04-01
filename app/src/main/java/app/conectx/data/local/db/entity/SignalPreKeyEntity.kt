package app.conectx.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signal_pre_keys")
data class SignalPreKeyEntity(
    @PrimaryKey val preKeyId: Int,
    val record: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignalPreKeyEntity) return false
        return preKeyId == other.preKeyId
    }
    override fun hashCode(): Int = preKeyId
}
