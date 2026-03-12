package com.example.xcpro.di

import com.example.xcpro.forecast.ForecastCatalogPort
import com.example.xcpro.forecast.ForecastLegendPort
import com.example.xcpro.forecast.ForecastTilesPort
import com.example.xcpro.forecast.ForecastValuePort
import com.example.xcpro.forecast.SkySightForecastProviderAdapter
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
