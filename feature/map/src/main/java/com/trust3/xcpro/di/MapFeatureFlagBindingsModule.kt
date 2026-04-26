package com.trust3.xcpro.di

import com.trust3.xcpro.map.config.MapFeatureFlags
import com.trust3.xcpro.map.config.MapReplayFeatureFlagPort
import com.trust3.xcpro.map.config.MapScreenFeatureFlagPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MapFeatureFlagBindingsModule {
    @Binds
    @Singleton
    abstract fun bindMapScreenFeatureFlagPort(
        impl: MapFeatureFlags
    ): MapScreenFeatureFlagPort

    @Binds
    @Singleton
    abstract fun bindMapReplayFeatureFlagPort(
        impl: MapFeatureFlags
    ): MapReplayFeatureFlagPort
}
