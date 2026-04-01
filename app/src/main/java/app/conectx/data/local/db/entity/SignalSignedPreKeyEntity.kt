package app.conectx.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signal_signed_pre_keys")
data class SignalSignedPreKeyEntity(
    @PrimaryKey val signedPreKeyId: Int,
    val record: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignalSignedPreKeyEntity) return false
        return signedPreKeyId == other.signedPreKeyId
    }
    override fun hashCode(): Int = signedPreKeyId
}
