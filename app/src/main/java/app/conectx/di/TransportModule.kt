package app.conectx.di

import app.conectx.transport.TransportPlugin
import app.conectx.transport.firebase.FirebasePlugin
import app.conectx.transport.nearby.NearbyPlugin
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class TransportModule {

    @Binds
    @IntoSet
    abstract fun bindNearbyPlugin(impl: NearbyPlugin): TransportPlugin

    @Binds
    @IntoSet
    abstract fun bindFirebasePlugin(impl: FirebasePlugin): TransportPlugin
}
