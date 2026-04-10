package com.example.xcpro.variometer.bluetooth.lxnav.runtime

import com.example.xcpro.external.ExternalInstrumentReadPort
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
    abstract fun bindExternalInstrumentReadPort(
        impl: LxExternalRuntimeRepository
    ): ExternalInstrumentReadPort
}
