package com.trust3.xcpro.service

import com.trust3.xcpro.livesource.SelectedLiveRuntimeBackendPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SelectedLiveRuntimeBackendBindingsModule {

    @Binds
    @Singleton
    abstract fun bindSelectedLiveRuntimeBackendPort(
        impl: AndroidSelectedLiveRuntimeBackend
    ): SelectedLiveRuntimeBackendPort
}
