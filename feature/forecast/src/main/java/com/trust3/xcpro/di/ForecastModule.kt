package com.trust3.xcpro.di

import com.trust3.xcpro.forecast.ForecastCatalogPort
import com.trust3.xcpro.forecast.ForecastLegendPort
import com.trust3.xcpro.forecast.ForecastTilesPort
import com.trust3.xcpro.forecast.ForecastValuePort
import com.trust3.xcpro.forecast.SkySightForecastProviderAdapter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ForecastModule {

    @Binds
    @Singleton
    abstract fun bindForecastCatalogPort(
        impl: SkySightForecastProviderAdapter
    ): ForecastCatalogPort

    @Binds
    @Singleton
    abstract fun bindForecastTilesPort(
        impl: SkySightForecastProviderAdapter
    ): ForecastTilesPort

    @Binds
    @Singleton
    abstract fun bindForecastLegendPort(
        impl: SkySightForecastProviderAdapter
    ): ForecastLegendPort

    @Binds
    @Singleton
    abstract fun bindForecastValuePort(
        impl: SkySightForecastProviderAdapter
    ): ForecastValuePort
}
