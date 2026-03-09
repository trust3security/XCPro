package com.example.xcpro.adsb.metadata.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.xcpro.adsb.metadata.domain.AircraftMetadataSyncRepository
import com.example.xcpro.adsb.metadata.domain.MetadataSyncRunResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class AircraftMetadataSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncRepository: AircraftMetadataSyncRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return when (syncRepository.runSyncNow()) {
            MetadataSyncRunResult.Succeeded -> Result.success()
            MetadataSyncRunResult.Skipped -> Result.success()
            MetadataSyncRunResult.Retry -> Result.retry()
        }
    }
}

