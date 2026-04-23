package app.conectx.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import app.conectx.data.local.db.dao.MessageDao
import app.conectx.data.local.db.dao.SquadDao
import app.conectx.data.local.db.dao.SyncRecordDao
import app.conectx.data.local.db.entity.MessageEntity
import app.conectx.data.local.db.entity.PeerEntity
import app.conectx.data.local.db.entity.SquadEntity
import app.conectx.data.local.db.entity.SyncRecordEntity

@Database(
    entities = [
        MessageEntity::class,
        SquadEntity::class,
        PeerEntity::class,
        SyncRecordEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class ConectxDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun squadDao(): SquadDao
    abstract fun syncRecordDao(): SyncRecordDao
}
