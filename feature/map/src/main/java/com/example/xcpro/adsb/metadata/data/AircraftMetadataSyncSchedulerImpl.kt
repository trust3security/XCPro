package com.example.xcpro.adsb.metadata.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.xcpro.adsb.metadata.domain.AircraftMetadataSyncRepository
import com.example.xcpro.adsb.metadata.domain.AircraftMetadataSyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AircraftMetadataSyncSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val checkpointStore: AircraftMetadataSyncCheckpointStore,
    private val syncRepository: AircraftMetadataSyncRepository
) : AircraftMetadataSyncScheduler {

    override suspend fun onOverlayPreferenceChanged(enabled: Boolean) {
        if (enabled) {
            enqueueSyncWork()
        } else {
            cancelSyncWork()
        }
    }

    override suspend fun bootstrapForOverlayPreference(overlayEnabled: Boolean) {
        if (overlayEnabled) {
            enqueueSyncWork()
        } else {
            cancelSyncWork()
        }
    }

    private suspend fun enqueueSyncWork() {
        syncRepository.onScheduled()
        val workManager = WorkManager.getInstance(context)

        val periodicRequest = PeriodicWorkRequestBuilder<AircraftMetadataSyncWorker>(
            AircraftMetadataSyncPolicy.PERIODIC_SYNC_DAYS,
            TimeUnit.DAYS
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            AircraftMetadataSyncPolicy.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )

        val checkpoint = checkpointStore.snapshot()
        if (checkpoint.lastSuccessWallMs == null) {
            val oneTimeRequest = OneTimeWorkRequestBuilder<AircraftMetadataSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            workManager.enqueueUniqueWork(
                AircraftMetadataSyncPolicy.ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                oneTimeRequest
            )
        }
    }

    private suspend fun cancelSyncWork() {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(AircraftMetadataSyncPolicy.ONE_TIME_WORK_NAME)
        workManager.cancelUniqueWork(AircraftMetadataSyncPolicy.PERIODIC_WORK_NAME)
        syncRepository.onPausedByUser()
    }
}

