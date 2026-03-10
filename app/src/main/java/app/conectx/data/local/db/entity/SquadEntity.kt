package app.conectx.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "squads")
data class SquadEntity(
    @PrimaryKey val id: String,
    val name: String,
    val inviteCode: String,
    val memberIds: String, // Comma-separated IDs, mapped in repository
    val createdAt: Long
)
