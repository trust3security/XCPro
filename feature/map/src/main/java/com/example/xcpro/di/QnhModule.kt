package com.example.xcpro.di

import com.example.xcpro.qnh.QnhCalibrationConfig
import com.example.xcpro.qnh.QnhRepository
import com.example.xcpro.qnh.QnhRepositoryImpl
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
    fun provideQnhCalibrationConfig(): QnhCalibrationConfig = QnhCalibrationConfig()
}
