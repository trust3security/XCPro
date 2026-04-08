package com.example.xcpro.di

import com.example.xcpro.core.flight.calculations.TerrainElevationReadPort
import com.example.xcpro.terrain.OfflineTerrainSource
import com.example.xcpro.terrain.OnlineTerrainSource
import com.example.xcpro.terrain.OpenMeteoTerrainDataSource
import com.example.xcpro.terrain.SrtmTerrainDataSource
import com.example.xcpro.terrain.TerrainElevationDataSource
import com.example.xcpro.terrain.TerrainElevationRepository
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
