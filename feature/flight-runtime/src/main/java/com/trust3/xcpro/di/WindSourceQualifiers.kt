package com.trust3.xcpro.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LiveSource

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ReplaySource

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PhoneLiveSensorSource

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CondorLiveSensorSource

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PhoneLiveAirspeedSource

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CondorLiveAirspeedSource

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultLiveExternalInstrumentSource

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CondorLiveExternalInstrumentSource
