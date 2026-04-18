package com.trust3.xcpro.di

import com.trust3.xcpro.map.VarioRuntimeControlPort
import com.trust3.xcpro.service.ForegroundServiceVarioRuntimeController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VarioRuntimeControlModule {
    @Binds
    @Singleton
    abstract fun bindVarioRuntimeControlPort(
        impl: ForegroundServiceVarioRuntimeController
    ): VarioRuntimeControlPort
}
