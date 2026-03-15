package com.example.xcpro.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LiveSource

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ReplaySource
