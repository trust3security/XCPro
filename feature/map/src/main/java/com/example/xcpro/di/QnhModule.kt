package com.example.xcpro.di

import com.example.xcpro.qnh.QnhCalibrationConfig
import com.example.xcpro.qnh.QnhRepository
import com.example.xcpro.qnh.QnhRepositoryImpl
import com.example.xcpro.qnh.SrtmTerrainElevationProvider
import com.example.xcpro.qnh.TerrainElevationProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object QnhModule {

    @Provides
    @Singleton
    fun provideQnhRepository(
        impl: QnhRepositoryImpl
    ): QnhRepository = impl

    @Provides
    @Singleton
    fun provideTerrainElevationProvider(
        impl: SrtmTerrainElevationProvider
    ): TerrainElevationProvider = impl

    @Provides
    fun provideQnhCalibrationConfig(): QnhCalibrationConfig = QnhCalibrationConfig()
}

