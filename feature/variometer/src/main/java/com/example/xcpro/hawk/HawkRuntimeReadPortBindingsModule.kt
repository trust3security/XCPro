package com.example.xcpro.hawk

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HawkRuntimeReadPortBindingsModule {
    @Binds
    @Singleton
    abstract fun bindHawkAudioVarioReadPort(
        impl: HawkAudioVarioReadPortAdapter
    ): HawkAudioVarioReadPort
}
