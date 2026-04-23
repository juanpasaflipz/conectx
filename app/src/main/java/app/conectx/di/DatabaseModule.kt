package app.conectx.di

import android.content.Context
import androidx.room.Room
import app.conectx.data.local.db.ConectxDatabase
import app.conectx.data.local.db.dao.ConversationDao
import app.conectx.data.local.db.dao.DirectMessageDao
import app.conectx.data.local.db.dao.MatchDao
import app.conectx.data.local.db.dao.MeetupPointDao
import app.conectx.data.local.db.dao.MessageDao
import app.conectx.data.local.db.dao.SignalDao
import app.conectx.data.local.db.dao.SquadDao
import app.conectx.data.local.db.dao.SyncRecordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ConectxDatabase {
        return Room.databaseBuilder(
            context,
            ConectxDatabase::class.java,
            "conectx.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideMessageDao(db: ConectxDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideSquadDao(db: ConectxDatabase): SquadDao = db.squadDao()

    @Provides
    fun provideSyncRecordDao(db: ConectxDatabase): SyncRecordDao = db.syncRecordDao()

    @Provides
    fun provideSignalDao(db: ConectxDatabase): SignalDao = db.signalDao()

    @Provides
    fun provideConversationDao(db: ConectxDatabase): ConversationDao = db.conversationDao()

    @Provides
    fun provideDirectMessageDao(db: ConectxDatabase): DirectMessageDao = db.directMessageDao()

    @Provides
    fun provideMatchDao(db: ConectxDatabase): MatchDao = db.matchDao()

    @Provides
    fun provideMeetupPointDao(db: ConectxDatabase): MeetupPointDao = db.meetupPointDao()
}
