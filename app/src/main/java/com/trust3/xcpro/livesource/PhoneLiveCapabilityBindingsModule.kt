package com.trust3.xcpro.livesource

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PhoneLiveCapabilityBindingsModule {
    @Binds
    @Singleton
    abstract fun bindPhoneLiveCapabilityPort(
        impl: AndroidPhoneLiveCapabilityPort
    ): PhoneLiveCapabilityPort
}
