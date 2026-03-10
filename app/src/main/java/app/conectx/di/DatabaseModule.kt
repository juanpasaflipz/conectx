package app.conectx.di

import android.content.Context
import androidx.room.Room
import app.conectx.data.local.db.ConectxDatabase
import app.conectx.data.local.db.dao.MessageDao
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
        ).build()
    }

    @Provides
    fun provideMessageDao(db: ConectxDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideSquadDao(db: ConectxDatabase): SquadDao = db.squadDao()

    @Provides
    fun provideSyncRecordDao(db: ConectxDatabase): SyncRecordDao = db.syncRecordDao()
}
