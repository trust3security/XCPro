package com.example.xcpro.audio

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VarioAudioRuntimeBindingsModule {
    @Binds
    @Singleton
    abstract fun bindVarioAudioControllerFactory(
        impl: DefaultVarioAudioControllerFactory
    ): VarioAudioControllerFactory
}
