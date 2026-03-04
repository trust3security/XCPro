package com.example.xcpro.di

import com.example.xcpro.map.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object ForecastNetworkModule {

    @Provides
    @Singleton
    @SkySightApiKey
    fun provideSkySightApiKey(): String = BuildConfig.SKYSIGHT_API_KEY.trim()

    @Provides
    @Singleton
    @ForecastHttpClient
    fun provideForecastHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
    }
}
