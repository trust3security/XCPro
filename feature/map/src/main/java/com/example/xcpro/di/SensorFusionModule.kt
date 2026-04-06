package com.example.xcpro.di

import com.example.xcpro.sensors.SensorFusionRepository
import com.example.xcpro.sensors.SensorFusionRepositoryFactory
import com.example.xcpro.sensors.UnifiedSensorManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(SingletonComponent::class)
object SensorFusionModule {

    @Provides
    @Singleton
    fun provideSensorFusionRepository(
        unifiedSensorManager: UnifiedSensorManager,
        factory: SensorFusionRepositoryFactory,
        @SensorRuntimeScope sensorRuntimeScope: CoroutineScope
    ): SensorFusionRepository {
        return factory.create(
            sensorDataSource = unifiedSensorManager,
            scope = sensorRuntimeScope,
            enableAudio = true,
            isReplayMode = false
        )
    }
}
