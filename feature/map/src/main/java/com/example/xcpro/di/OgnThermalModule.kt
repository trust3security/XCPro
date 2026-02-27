package com.example.xcpro.di

import com.example.xcpro.ogn.OgnTrafficPreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.ZoneId
import javax.inject.Qualifier
import kotlinx.coroutines.flow.Flow

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OgnThermalRetentionHoursFlow

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OgnThermalZoneId

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OgnHotspotsDisplayPercentFlow

@Module
@InstallIn(SingletonComponent::class)
object OgnThermalModule {
    @Provides
    @OgnThermalRetentionHoursFlow
    fun provideThermalRetentionHoursFlow(
        preferencesRepository: OgnTrafficPreferencesRepository
    ): Flow<Int> = preferencesRepository.thermalRetentionHoursFlow

    @Provides
    @OgnHotspotsDisplayPercentFlow
    fun provideHotspotsDisplayPercentFlow(
        preferencesRepository: OgnTrafficPreferencesRepository
    ): Flow<Int> = preferencesRepository.hotspotsDisplayPercentFlow

    @Provides
    @OgnThermalZoneId
    fun provideThermalZoneId(): ZoneId = ZoneId.systemDefault()
}
