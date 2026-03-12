package com.example.xcpro.weglide.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.xcpro.weglide.domain.WeGlideUploadWorkScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeGlideUploadWorkSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : WeGlideUploadWorkScheduler {

    override fun enqueueUpload(localFlightId: String, wifiOnly: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .build()

        val request = OneTimeWorkRequestBuilder<WeGlideUploadWorker>()
            .setInputData(workDataOf(INPUT_LOCAL_FLIGHT_ID to localFlightId))
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName(localFlightId),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        const val INPUT_LOCAL_FLIGHT_ID = "localFlightId"

        fun uniqueWorkName(localFlightId: String): String = "weglide_upload_$localFlightId"
    }
}
