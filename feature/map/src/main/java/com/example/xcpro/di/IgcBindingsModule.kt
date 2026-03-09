package com.example.xcpro.di

import com.example.xcpro.igc.data.AndroidIgcRecorderMetadataSource
import com.example.xcpro.igc.data.ProfileUseCaseIgcProfileMetadataSource
import com.example.xcpro.igc.data.TaskRepositoryIgcTaskDeclarationSource
import com.example.xcpro.igc.domain.IgcProfileMetadataSource
import com.example.xcpro.igc.domain.IgcRecorderMetadataSource
import com.example.xcpro.igc.domain.IgcTaskDeclarationSource
import com.example.xcpro.replay.IgcReplayController
import com.example.xcpro.replay.IgcReplayControllerPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
}
