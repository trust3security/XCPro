package com.example.xcpro.di

import com.example.xcpro.adsb.AdsbEmergencyAudioOutputPort
import com.example.xcpro.adsb.AdsbEmergencyAudioRolloutPort
import com.example.xcpro.adsb.AdsbEmergencyAudioSettingsPort
import com.example.xcpro.adsb.AdsbProviderClient
import com.example.xcpro.adsb.AdsbTrafficPreferencesRepository
import com.example.xcpro.adsb.AdsbTrafficRepository
import com.example.xcpro.adsb.AdsbTrafficRepositoryImpl
import com.example.xcpro.adsb.AndroidAdsbEmergencyAudioOutputAdapter
import com.example.xcpro.adsb.OpenSkyProviderClient
import com.example.xcpro.adsb.OpenSkyTokenRepository
import com.example.xcpro.adsb.OpenSkyTokenRepositoryImpl
import com.example.xcpro.adsb.data.AndroidAdsbNetworkAvailabilityAdapter
import com.example.xcpro.adsb.domain.AdsbNetworkAvailabilityPort
import com.example.xcpro.map.AdsbTrafficFacade
import com.example.xcpro.map.AdsbTrafficUseCase
import com.example.xcpro.map.OgnTrafficFacade
import com.example.xcpro.map.OgnTrafficUseCase
import com.example.xcpro.ogn.OgnGliderTrailRepository
import com.example.xcpro.ogn.OgnGliderTrailRepositoryImpl
import com.example.xcpro.ogn.OgnThermalRepository
import com.example.xcpro.ogn.OgnThermalRepositoryImpl
import com.example.xcpro.ogn.OgnTrafficRepository
import com.example.xcpro.ogn.OgnTrafficRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class OgnBindingsModule {
    @Binds
    abstract fun bindOgnTrafficFacade(impl: OgnTrafficUseCase): OgnTrafficFacade

    @Binds
    abstract fun bindOgnTrafficRepository(impl: OgnTrafficRepositoryImpl): OgnTrafficRepository

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
}
