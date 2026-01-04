package com.example.xcpro.di

import android.content.Context
import com.example.xcpro.replay.ReplaySensorSource
import com.example.xcpro.sensors.SensorDataSource
import com.example.xcpro.sensors.UnifiedSensorManager
import com.example.xcpro.weather.wind.data.WindSensorInputAdapter
import com.example.xcpro.weather.wind.data.WindSensorInputs
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
    @Singleton
    @LiveSource
    fun provideLiveSensorDataSource(
        unifiedSensorManager: UnifiedSensorManager
    ): SensorDataSource = unifiedSensorManager

    @Provides
    @Singleton
    @ReplaySource
    fun provideReplaySensorDataSource(
        replaySensorSource: ReplaySensorSource
    ): SensorDataSource = replaySensorSource

    @Provides
    @Singleton
    @LiveSource
    fun provideLiveWindInputs(
        adapter: WindSensorInputAdapter,
        @LiveSource source: SensorDataSource
    ): WindSensorInputs = adapter.adapt(source)

    @Provides
    @Singleton
    @ReplaySource
    fun provideReplayWindInputs(
        adapter: WindSensorInputAdapter,
        @ReplaySource source: SensorDataSource
    ): WindSensorInputs = adapter.adapt(source)
}
