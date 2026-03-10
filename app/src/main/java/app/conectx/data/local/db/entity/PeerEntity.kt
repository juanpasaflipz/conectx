package app.conectx.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "peers")
data class PeerEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val squadId: String,
    val isConnected: Boolean,
    val lastSeen: Long
)
