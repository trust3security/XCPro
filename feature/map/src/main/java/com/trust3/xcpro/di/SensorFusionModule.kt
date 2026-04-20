package com.trust3.xcpro.di

import com.trust3.xcpro.di.LiveSource
import com.trust3.xcpro.sensors.SensorFusionRepository
import com.trust3.xcpro.sensors.SensorFusionRepositoryFactory
import com.trust3.xcpro.sensors.SensorDataSource
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
        @LiveSource sensorDataSource: SensorDataSource,
        factory: SensorFusionRepositoryFactory,
        @SensorRuntimeScope sensorRuntimeScope: CoroutineScope
    ): SensorFusionRepository {
        return factory.create(
            sensorDataSource = sensorDataSource,
            scope = sensorRuntimeScope,
            enableAudio = true,
            isReplayMode = false
        )
    }
}
