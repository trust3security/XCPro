package com.example.xcpro.di

import com.example.xcpro.glider.PolarStillAirSinkProvider
import com.example.xcpro.glider.StillAirSinkProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SensorBindingsModule {

    @Binds
    abstract fun bindStillAirSinkProvider(
        impl: PolarStillAirSinkProvider
    ): StillAirSinkProvider
}

