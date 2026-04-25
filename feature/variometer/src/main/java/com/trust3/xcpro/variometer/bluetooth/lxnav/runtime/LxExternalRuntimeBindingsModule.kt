package com.trust3.xcpro.variometer.bluetooth.lxnav.runtime

import com.trust3.xcpro.di.DefaultLiveExternalInstrumentSource
import com.trust3.xcpro.di.DefaultLiveExternalFlightSettingsSource
import com.trust3.xcpro.external.ExternalFlightSettingsReadPort
import com.trust3.xcpro.external.ExternalInstrumentReadPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LxExternalRuntimeBindingsModule {
    @Binds
    @Singleton
    @DefaultLiveExternalInstrumentSource
    abstract fun bindExternalInstrumentReadPort(
        impl: LxExternalRuntimeRepository
    ): ExternalInstrumentReadPort

    @Binds
    @Singleton
    @DefaultLiveExternalFlightSettingsSource
    abstract fun bindExternalFlightSettingsReadPort(
        impl: LxExternalRuntimeRepository
    ): ExternalFlightSettingsReadPort
}
