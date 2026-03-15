package com.example.xcpro.glider

import com.example.xcpro.common.glider.GliderConfigRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class GliderBindingsModule {
    @Binds
    abstract fun bindGliderConfigRepository(impl: GliderRepository): GliderConfigRepository

    @Binds
    abstract fun bindStillAirSinkProvider(impl: PolarStillAirSinkProvider): StillAirSinkProvider
}
