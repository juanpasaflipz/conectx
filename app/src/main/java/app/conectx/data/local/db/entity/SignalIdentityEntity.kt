package app.conectx.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signal_identities")
data class SignalIdentityEntity(
    @PrimaryKey val address: String,
    val identityKey: ByteArray,
    val trusted: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignalIdentityEntity) return false
        return address == other.address
    }
    override fun hashCode(): Int = address.hashCode()
}
