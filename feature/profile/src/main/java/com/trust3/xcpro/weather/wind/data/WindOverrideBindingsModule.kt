package com.trust3.xcpro.weather.wind.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WindOverrideBindingsModule {
    @Binds
    @Singleton
    abstract fun bindExternalWindWritePort(
        impl: WindOverrideRepository
    ): ExternalWindWritePort
}
