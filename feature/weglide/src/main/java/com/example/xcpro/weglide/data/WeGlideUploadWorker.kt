package com.example.xcpro.weglide.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.xcpro.weglide.domain.ExecuteWeGlideUploadUseCase
import com.example.xcpro.weglide.domain.WeGlideUploadExecutionResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class WeGlideUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val executeWeGlideUploadUseCase: ExecuteWeGlideUploadUseCase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val localFlightId = inputData.getString(WeGlideUploadWorkSchedulerImpl.INPUT_LOCAL_FLIGHT_ID)
            ?: return Result.success()
        return when (executeWeGlideUploadUseCase(localFlightId)) {
            is WeGlideUploadExecutionResult.Success,
            is WeGlideUploadExecutionResult.Duplicate,
            is WeGlideUploadExecutionResult.PermanentFailure -> Result.success()
            is WeGlideUploadExecutionResult.RetryableFailure -> Result.retry()
        }
    }
}
