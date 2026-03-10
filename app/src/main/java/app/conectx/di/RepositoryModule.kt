package app.conectx.di

import app.conectx.data.repository.MessageRepositoryImpl
import app.conectx.data.repository.SquadRepositoryImpl
import app.conectx.domain.repository.MessageRepository
import app.conectx.domain.repository.SquadRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSquadRepository(impl: SquadRepositoryImpl): SquadRepository

    @Binds
    @Singleton
    abstract fun bindMessageRepository(impl: MessageRepositoryImpl): MessageRepository
}
