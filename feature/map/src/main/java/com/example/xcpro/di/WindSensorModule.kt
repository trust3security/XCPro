package com.example.xcpro.di

import android.content.Context
import com.example.xcpro.common.di.DefaultDispatcher
import com.example.xcpro.core.time.Clock
import com.example.xcpro.replay.ReplaySensorSource
import com.example.xcpro.sensors.FlightStateRepository
import com.example.xcpro.sensors.FlightStateSource
import com.example.xcpro.sensors.SensorDataSource
import com.example.xcpro.sensors.UnifiedSensorManager
import com.example.xcpro.weather.wind.data.AirspeedDataSource
import com.example.xcpro.weather.wind.data.ExternalAirspeedRepository
import com.example.xcpro.weather.wind.data.ReplayAirspeedRepository
import com.example.xcpro.weather.wind.data.WindSensorInputAdapter
import com.example.xcpro.weather.wind.data.WindSensorInputs
import com.example.xcpro.weather.wind.data.WindOverrideRepository
import com.example.xcpro.weather.wind.data.WindOverrideSource
import com.example.xcpro.weather.wind.domain.WindSelectionUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object WindSensorModule {

    @Provides
    @Singleton
    @SensorRuntimeScope
    fun provideSensorRuntimeScope(
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher
    ): CoroutineScope = CoroutineScope(SupervisorJob() + defaultDispatcher)

    @Provides
    @Singleton
    fun provideUnifiedSensorManager(
        @ApplicationContext context: Context,
        clock: Clock,
        @SensorRuntimeScope sensorRuntimeScope: CoroutineScope
    ): UnifiedSensorManager = UnifiedSensorManager(context, clock, sensorRuntimeScope)

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
    fun provideLiveAirspeedSource(
        externalAirspeedRepository: ExternalAirspeedRepository
    ): AirspeedDataSource = externalAirspeedRepository

    @Provides
    @Singleton
    @ReplaySource
    fun provideReplayAirspeedSource(
        replayAirspeedRepository: ReplayAirspeedRepository
    ): AirspeedDataSource = replayAirspeedRepository

    @Provides
    @Singleton
    fun provideWindOverrideSource(
        repository: WindOverrideRepository
    ): WindOverrideSource = repository

    @Provides
    @Singleton
    fun provideWindSelectionUseCase(): WindSelectionUseCase = WindSelectionUseCase()

    @Provides
    @Singleton
    fun provideFlightStateSource(
        repository: FlightStateRepository
    ): FlightStateSource = repository

    @Provides
    @Singleton
    @LiveSource
    fun provideLiveWindInputs(
        adapter: WindSensorInputAdapter,
        @LiveSource source: SensorDataSource,
        @LiveSource airspeedSource: AirspeedDataSource
    ): WindSensorInputs = adapter.adapt(source, airspeedSource)

    @Provides
    @Singleton
    @ReplaySource
    fun provideReplayWindInputs(
        adapter: WindSensorInputAdapter,
        @ReplaySource source: SensorDataSource,
        @ReplaySource airspeedSource: AirspeedDataSource
    ): WindSensorInputs = adapter.adapt(source, airspeedSource)
}
