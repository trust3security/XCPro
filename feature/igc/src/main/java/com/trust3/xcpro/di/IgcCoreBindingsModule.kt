package com.trust3.xcpro.di

import com.trust3.xcpro.igc.IgcRecordingActionSink
import com.trust3.xcpro.igc.data.IgcExportDiagnosticsRepository
import com.trust3.xcpro.igc.data.IgcDownloadsRepository
import com.trust3.xcpro.igc.data.InMemoryIgcExportDiagnosticsRepository
import com.trust3.xcpro.igc.data.IgcFlightLogRepository
import com.trust3.xcpro.igc.data.IgcRecoveryMetadataStore
import com.trust3.xcpro.igc.data.IgcRecordingRuntimeActionSink
import com.trust3.xcpro.igc.data.IgcSessionStateSnapshotStore
import com.trust3.xcpro.igc.data.IgcRecoveryDownloadsLookup
import com.trust3.xcpro.igc.data.MediaStoreIgcDownloadsRepository
import com.trust3.xcpro.igc.data.MediaStoreIgcFlightLogRepository
import com.trust3.xcpro.igc.data.MediaStoreIgcRecoveryDownloadsLookup
import com.trust3.xcpro.igc.domain.IgcLintValidator
import com.trust3.xcpro.igc.domain.StrictIgcLintValidator
import com.trust3.xcpro.igc.data.SharedPrefsIgcRecoveryMetadataStore
import com.trust3.xcpro.igc.data.SharedPrefsIgcSessionStateSnapshotStore
import com.trust3.xcpro.igc.usecase.IgcReplayLauncher
import com.trust3.xcpro.igc.usecase.IgcReplayUseCaseLauncher
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
    abstract fun bindIgcRecoveryMetadataStore(
        impl: SharedPrefsIgcRecoveryMetadataStore
    ): IgcRecoveryMetadataStore

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
    abstract fun bindIgcRecoveryDownloadsLookup(
        impl: MediaStoreIgcRecoveryDownloadsLookup
    ): IgcRecoveryDownloadsLookup

    @Binds
    @Singleton
    abstract fun bindIgcExportDiagnosticsRepository(
        impl: InMemoryIgcExportDiagnosticsRepository
    ): IgcExportDiagnosticsRepository

    @Binds
    @Singleton
    abstract fun bindIgcLintValidator(
        impl: StrictIgcLintValidator
    ): IgcLintValidator

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
