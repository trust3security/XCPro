package com.example.xcpro.di

import com.example.xcpro.map.VarioRuntimeControlPort
import com.example.xcpro.service.ForegroundServiceVarioRuntimeController
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
