package com.example.xcpro.di

import com.example.xcpro.common.di.DefaultDispatcher
import com.example.xcpro.sensors.SensorFusionRepository
import com.example.xcpro.sensors.SensorFusionRepositoryFactory
import com.example.xcpro.sensors.UnifiedSensorManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object SensorFusionModule {

    @Provides
    @Singleton
    fun provideSensorFusionRepository(
        unifiedSensorManager: UnifiedSensorManager,
        factory: SensorFusionRepositoryFactory,
        @DefaultDispatcher dispatcher: CoroutineDispatcher
    ): SensorFusionRepository {
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        return factory.create(
            sensorDataSource = unifiedSensorManager,
            scope = scope,
            enableAudio = true,
            isReplayMode = false
        )
    }
}
