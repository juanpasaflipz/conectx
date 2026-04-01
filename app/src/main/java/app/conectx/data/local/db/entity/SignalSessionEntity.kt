package app.conectx.data.local.db.entity

import androidx.room.Entity

@Entity(
    tableName = "signal_sessions",
    primaryKeys = ["address", "deviceId"]
)
data class SignalSessionEntity(
    val address: String,
    val deviceId: Int,
    val record: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignalSessionEntity) return false
        return address == other.address && deviceId == other.deviceId
    }
    override fun hashCode(): Int = 31 * address.hashCode() + deviceId
}
