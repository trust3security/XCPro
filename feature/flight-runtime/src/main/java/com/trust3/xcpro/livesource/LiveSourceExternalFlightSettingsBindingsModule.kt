package com.trust3.xcpro.livesource

import com.trust3.xcpro.external.ExternalFlightSettingsReadPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LiveSourceExternalFlightSettingsBindingsModule {
    @Binds
    @Singleton
    abstract fun bindSelectedExternalFlightSettingsReadPort(
        impl: ResolverSelectedExternalFlightSettingsReadPort
    ): ExternalFlightSettingsReadPort
}
