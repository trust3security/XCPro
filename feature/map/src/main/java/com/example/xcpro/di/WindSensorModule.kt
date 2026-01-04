package com.example.xcpro.di

import android.content.Context
import com.example.xcpro.replay.ReplaySensorSource
import com.example.xcpro.sensors.SensorDataSource
import com.example.xcpro.sensors.UnifiedSensorManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WindSensorModule {

    @Provides
    @Singleton
    fun provideUnifiedSensorManager(
        @ApplicationContext context: Context
    ): UnifiedSensorManager = UnifiedSensorManager(context)

    @Provides
    @Singleton
    fun provideReplaySensorSource(): ReplaySensorSource = ReplaySensorSource()

    @Provides
    @LiveSource
    fun provideLiveSensorSource(
        unifiedSensorManager: UnifiedSensorManager
    ): SensorDataSource = unifiedSensorManager

    @Provides
    @ReplaySource
    fun provideReplaySensorSourceBinding(
        replaySensorSource: ReplaySensorSource
    ): SensorDataSource = replaySensorSource
}
