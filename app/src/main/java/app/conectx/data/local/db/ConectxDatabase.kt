package app.conectx.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import app.conectx.data.local.db.dao.ConversationDao
import app.conectx.data.local.db.dao.DirectMessageDao
import app.conectx.data.local.db.dao.MatchDao
import app.conectx.data.local.db.dao.MeetupPointDao
import app.conectx.data.local.db.dao.MessageDao
import app.conectx.data.local.db.dao.SignalDao
import app.conectx.data.local.db.dao.SquadDao
import app.conectx.data.local.db.dao.SyncRecordDao
import app.conectx.data.local.db.entity.ConversationEntity
import app.conectx.data.local.db.entity.DirectMessageEntity
import app.conectx.data.local.db.entity.MatchEntity
import app.conectx.data.local.db.entity.MeetupPointEntity
import app.conectx.data.local.db.entity.MessageEntity
import app.conectx.data.local.db.entity.PeerEntity
import app.conectx.data.local.db.entity.SignalIdentityEntity
import app.conectx.data.local.db.entity.SignalPreKeyEntity
import app.conectx.data.local.db.entity.SignalSessionEntity
import app.conectx.data.local.db.entity.SignalSignedPreKeyEntity
import app.conectx.data.local.db.entity.SquadEntity
import app.conectx.data.local.db.entity.SyncRecordEntity

@Database(
    entities = [
        MessageEntity::class,
        SquadEntity::class,
        PeerEntity::class,
        SyncRecordEntity::class,
        SignalIdentityEntity::class,
        SignalPreKeyEntity::class,
        SignalSignedPreKeyEntity::class,
        SignalSessionEntity::class,
        ConversationEntity::class,
        DirectMessageEntity::class,
        MatchEntity::class,
        MeetupPointEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class ConectxDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun squadDao(): SquadDao
    abstract fun syncRecordDao(): SyncRecordDao
    abstract fun signalDao(): SignalDao
    abstract fun conversationDao(): ConversationDao
    abstract fun directMessageDao(): DirectMessageDao
    abstract fun matchDao(): MatchDao
    abstract fun meetupPointDao(): MeetupPointDao
}
