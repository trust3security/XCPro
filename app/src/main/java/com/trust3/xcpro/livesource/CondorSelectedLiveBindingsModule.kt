package com.trust3.xcpro.livesource

import com.trust3.xcpro.di.CondorLiveAirspeedSource
import com.trust3.xcpro.di.CondorLiveExternalFlightSettingsSource
import com.trust3.xcpro.di.CondorLiveExternalInstrumentSource
import com.trust3.xcpro.di.CondorLiveSensorSource
import com.trust3.xcpro.external.ExternalFlightSettingsReadPort
import com.trust3.xcpro.external.ExternalFlightSettingsSnapshot
import com.trust3.xcpro.external.ExternalInstrumentReadPort
import com.trust3.xcpro.sensors.SensorDataSource
import com.trust3.xcpro.simulator.condor.CondorLiveAirspeedDataSource
import com.trust3.xcpro.simulator.condor.CondorLiveSampleRepository
import com.trust3.xcpro.simulator.condor.CondorLiveSensorDataSource
import com.trust3.xcpro.weather.wind.data.AirspeedDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow

@Module
@InstallIn(SingletonComponent::class)
object CondorSelectedLiveBindingsModule {

    @Provides
    @Singleton
    @CondorLiveSensorSource
    fun provideCondorLiveSensorSource(
        source: CondorLiveSensorDataSource
    ): SensorDataSource = source

    @Provides
    @Singleton
    @CondorLiveAirspeedSource
    fun provideCondorLiveAirspeedSource(
        source: CondorLiveAirspeedDataSource
    ): AirspeedDataSource = source

    @Provides
    @Singleton
    @CondorLiveExternalInstrumentSource
    fun provideCondorLiveExternalInstrumentSource(
        source: CondorLiveSampleRepository
    ): ExternalInstrumentReadPort = source

    @Provides
    @Singleton
    @CondorLiveExternalFlightSettingsSource
    fun provideCondorLiveExternalFlightSettingsSource(): ExternalFlightSettingsReadPort =
        object : ExternalFlightSettingsReadPort {
            override val externalFlightSettingsSnapshot =
                MutableStateFlow(ExternalFlightSettingsSnapshot())
        }
}
