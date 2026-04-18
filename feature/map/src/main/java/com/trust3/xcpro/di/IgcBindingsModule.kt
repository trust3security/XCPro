package com.trust3.xcpro.di

import com.trust3.xcpro.common.di.DefaultDispatcher
import com.trust3.xcpro.igc.data.AndroidIgcRecorderMetadataSource
import com.trust3.xcpro.igc.data.ProfileUseCaseIgcProfileMetadataSource
import com.trust3.xcpro.igc.data.TaskRepositoryIgcTaskDeclarationSource
import com.trust3.xcpro.igc.domain.IgcProfileMetadataSource
import com.trust3.xcpro.igc.domain.IgcRecorderMetadataSource
import com.trust3.xcpro.igc.domain.IgcTaskDeclarationSource
import com.trust3.xcpro.replay.IgcReplayController
import com.trust3.xcpro.replay.IgcReplayControllerPort
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
abstract class IgcMapBindingsModule {
    @Binds
    @Singleton
    abstract fun bindIgcProfileMetadataSource(
        impl: ProfileUseCaseIgcProfileMetadataSource
    ): IgcProfileMetadataSource

    @Binds
    @Singleton
    abstract fun bindIgcRecorderMetadataSource(
        impl: AndroidIgcRecorderMetadataSource
    ): IgcRecorderMetadataSource

    @Binds
    @Singleton
    abstract fun bindIgcTaskDeclarationSource(
        impl: TaskRepositoryIgcTaskDeclarationSource
    ): IgcTaskDeclarationSource

    @Binds
    @Singleton
    abstract fun bindIgcReplayControllerPort(
        impl: IgcReplayController
    ): IgcReplayControllerPort

    companion object {
        @Provides
        @Singleton
        @IgcRuntimeScope
        fun provideIgcRuntimeScope(
            @DefaultDispatcher defaultDispatcher: CoroutineDispatcher
        ): CoroutineScope = CoroutineScope(SupervisorJob() + defaultDispatcher)
    }
}
