package com.example.xcpro.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ForecastHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SkySightApiKey
