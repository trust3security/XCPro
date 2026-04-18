package com.trust3.xcpro.di

import com.trust3.xcpro.adsb.AdsbEmergencyAudioOutputPort
import com.trust3.xcpro.adsb.AdsbEmergencyAudioRolloutPort
import com.trust3.xcpro.adsb.AdsbEmergencyAudioSettingsPort
import com.trust3.xcpro.adsb.AdsbEmergencyAudioFeatureFlags
import com.trust3.xcpro.adsb.AdsbProviderClient
import com.trust3.xcpro.adsb.AdsbTrafficPreferencesRepository
import com.trust3.xcpro.adsb.AdsbTrafficRepository
import com.trust3.xcpro.adsb.AdsbTrafficRepositoryImpl
import com.trust3.xcpro.adsb.AndroidAdsbEmergencyAudioOutputAdapter
import com.trust3.xcpro.adsb.OpenSkyProviderClient
import com.trust3.xcpro.adsb.OpenSkyTokenRepository
import com.trust3.xcpro.adsb.OpenSkyTokenRepositoryImpl
import com.trust3.xcpro.adsb.data.AndroidAdsbNetworkAvailabilityAdapter
import com.trust3.xcpro.adsb.domain.AdsbNetworkAvailabilityPort
import com.trust3.xcpro.map.AdsbTrafficFacade
import com.trust3.xcpro.map.AdsbTrafficUseCase
import com.trust3.xcpro.map.OgnTrafficFacade
import com.trust3.xcpro.map.OgnTrafficUseCase
import com.trust3.xcpro.ogn.OgnGliderTrailRepository
import com.trust3.xcpro.ogn.OgnGliderTrailRepositoryImpl
import com.trust3.xcpro.ogn.OgnThermalRepository
import com.trust3.xcpro.ogn.OgnThermalRepositoryImpl
import com.trust3.xcpro.ogn.OgnTrafficRepository
import com.trust3.xcpro.ogn.OgnTrafficRepositoryImpl
import com.trust3.xcpro.ogn.data.AndroidOgnNetworkAvailabilityAdapter
import com.trust3.xcpro.ogn.domain.OgnNetworkAvailabilityPort
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OgnBindingsModule {
    @Binds
    abstract fun bindOgnTrafficFacade(impl: OgnTrafficUseCase): OgnTrafficFacade

    @Binds
    abstract fun bindOgnTrafficRepository(impl: OgnTrafficRepositoryImpl): OgnTrafficRepository

    @Binds
    abstract fun bindOgnNetworkAvailabilityPort(
        impl: AndroidOgnNetworkAvailabilityAdapter
    ): OgnNetworkAvailabilityPort

    @Binds
    abstract fun bindOgnThermalRepository(impl: OgnThermalRepositoryImpl): OgnThermalRepository

    @Binds
    abstract fun bindOgnGliderTrailRepository(
        impl: OgnGliderTrailRepositoryImpl
    ): OgnGliderTrailRepository
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AdsbBindingsModule {
    @Binds
    abstract fun bindAdsbTrafficFacade(impl: AdsbTrafficUseCase): AdsbTrafficFacade

    @Binds
    abstract fun bindAdsbProviderClient(impl: OpenSkyProviderClient): AdsbProviderClient

    @Binds
    abstract fun bindOpenSkyTokenRepository(impl: OpenSkyTokenRepositoryImpl): OpenSkyTokenRepository

    @Binds
    abstract fun bindAdsbNetworkAvailabilityPort(
        impl: AndroidAdsbNetworkAvailabilityAdapter
    ): AdsbNetworkAvailabilityPort

    @Binds
    abstract fun bindAdsbEmergencyAudioSettingsPort(
        impl: AdsbTrafficPreferencesRepository
    ): AdsbEmergencyAudioSettingsPort

    @Binds
    abstract fun bindAdsbEmergencyAudioRolloutPort(
        impl: AdsbTrafficPreferencesRepository
    ): AdsbEmergencyAudioRolloutPort

    @Binds
    abstract fun bindAdsbEmergencyAudioOutputPort(
        impl: AndroidAdsbEmergencyAudioOutputAdapter
    ): AdsbEmergencyAudioOutputPort

    @Binds
    abstract fun bindAdsbTrafficRepository(impl: AdsbTrafficRepositoryImpl): AdsbTrafficRepository

    companion object {
        @Provides
        @Singleton
        fun provideAdsbEmergencyAudioFeatureFlags(): AdsbEmergencyAudioFeatureFlags =
            // AI-NOTE: Production bootstrap policy is explicit in DI; test-only fallback wiring
            // stays in test support rather than a main-source convenience constructor.
            AdsbEmergencyAudioFeatureFlags.bootstrap()
    }
}
