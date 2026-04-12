package com.example.xcpro.di

import com.example.xcpro.weather.wind.data.ExternalAirspeedRepository
import com.example.xcpro.weather.wind.data.ExternalAirspeedWritePort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ExternalAirspeedModule {
    @Binds
    abstract fun bindExternalAirspeedWritePort(
        impl: ExternalAirspeedRepository
    ): ExternalAirspeedWritePort
}
