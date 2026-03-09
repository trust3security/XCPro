package com.example.xcpro.di

import com.example.xcpro.igc.IgcRecordingActionSink
import com.example.xcpro.igc.data.IgcDownloadsRepository
import com.example.xcpro.igc.data.IgcFlightLogRepository
import com.example.xcpro.igc.data.IgcRecordingRuntimeActionSink
import com.example.xcpro.igc.data.IgcSessionStateSnapshotStore
import com.example.xcpro.igc.data.MediaStoreIgcDownloadsRepository
import com.example.xcpro.igc.data.MediaStoreIgcFlightLogRepository
import com.example.xcpro.igc.data.SharedPrefsIgcSessionStateSnapshotStore
import com.example.xcpro.igc.usecase.IgcReplayLauncher
import com.example.xcpro.igc.usecase.IgcReplayUseCaseLauncher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class IgcCoreBindingsModule {
    @Binds
    @Singleton
    abstract fun bindIgcSessionStateSnapshotStore(
        impl: SharedPrefsIgcSessionStateSnapshotStore
    ): IgcSessionStateSnapshotStore

    @Binds
    @Singleton
    abstract fun bindIgcRecordingActionSink(
        impl: IgcRecordingRuntimeActionSink
    ): IgcRecordingActionSink

    @Binds
    @Singleton
    abstract fun bindIgcDownloadsRepository(
        impl: MediaStoreIgcDownloadsRepository
    ): IgcDownloadsRepository

    @Binds
    @Singleton
    abstract fun bindIgcFlightLogRepository(
        impl: MediaStoreIgcFlightLogRepository
    ): IgcFlightLogRepository

    @Binds
    @Singleton
    abstract fun bindIgcReplayLauncher(
        impl: IgcReplayUseCaseLauncher
    ): IgcReplayLauncher
}
