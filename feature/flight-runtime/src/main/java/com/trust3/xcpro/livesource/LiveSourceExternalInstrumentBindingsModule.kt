package com.trust3.xcpro.livesource

import com.trust3.xcpro.external.ExternalInstrumentReadPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LiveSourceExternalInstrumentBindingsModule {
    @Binds
    @Singleton
    abstract fun bindSelectedExternalInstrumentReadPort(
        impl: ResolverSelectedExternalInstrumentReadPort
    ): ExternalInstrumentReadPort
}
