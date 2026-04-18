package com.trust3.xcpro.di

import com.trust3.xcpro.core.flight.calculations.TerrainElevationReadPort
import com.trust3.xcpro.terrain.OfflineTerrainSource
import com.trust3.xcpro.terrain.OnlineTerrainSource
import com.trust3.xcpro.terrain.OpenMeteoTerrainDataSource
import com.trust3.xcpro.terrain.SrtmTerrainDataSource
import com.trust3.xcpro.terrain.TerrainElevationDataSource
import com.trust3.xcpro.terrain.TerrainElevationRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TerrainModule {

    @Provides
    @Singleton
    fun provideTerrainElevationReadPort(
        impl: TerrainElevationRepository
    ): TerrainElevationReadPort = impl

    @Provides
    @Singleton
    @OfflineTerrainSource
    fun provideOfflineTerrainDataSource(
        impl: SrtmTerrainDataSource
    ): TerrainElevationDataSource = impl

    @Provides
    @Singleton
    @OnlineTerrainSource
    fun provideOnlineTerrainDataSource(
        impl: OpenMeteoTerrainDataSource
    ): TerrainElevationDataSource = impl
}
