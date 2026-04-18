package com.trust3.xcpro.weglide.domain

import com.trust3.xcpro.core.time.Clock
import javax.inject.Inject

class ExecuteWeGlideUploadUseCase @Inject constructor(
    private val queueRepository: WeGlideUploadQueueRepository,
    private val uploadRepository: WeGlideFlightUploadRepository,
    private val clock: Clock
) {

    suspend operator fun invoke(localFlightId: String): WeGlideUploadExecutionResult {
        val current = queueRepository.getByLocalFlightId(localFlightId)
            ?: return WeGlideUploadExecutionResult.PermanentFailure(
                code = null,
                message = "Missing WeGlide queue row"
            )

        when (current.uploadState) {
            WeGlideUploadState.UPLOADED -> {
                return WeGlideUploadExecutionResult.Success(current.remoteFlightId)
            }
            WeGlideUploadState.SKIPPED_DUPLICATE -> {
                return WeGlideUploadExecutionResult.Duplicate(current.remoteFlightId)
            }
            WeGlideUploadState.FAILED_PERMANENT -> {
                return WeGlideUploadExecutionResult.PermanentFailure(
                    code = current.lastErrorCode,
                    message = current.lastErrorMessage ?: "WeGlide upload already failed permanently"
                )
            }
            else -> Unit
        }

        val uploading = current.copy(
            uploadState = WeGlideUploadState.UPLOADING,
            updatedAtEpochMs = clock.nowWallMs()
        )
        queueRepository.upsert(uploading)

        return when (val result = uploadRepository.uploadQueuedFlight(uploading)) {
            is WeGlideUploadExecutionResult.Success -> {
                queueRepository.upsert(
                    uploading.copy(
                        uploadState = WeGlideUploadState.UPLOADED,
                        lastErrorCode = null,
                        lastErrorMessage = null,
                        remoteFlightId = result.remoteFlightId,
                        updatedAtEpochMs = clock.nowWallMs()
                    )
                )
                result
            }
            is WeGlideUploadExecutionResult.Duplicate -> {
                queueRepository.upsert(
                    uploading.copy(
                        uploadState = WeGlideUploadState.SKIPPED_DUPLICATE,
                        lastErrorCode = null,
                        lastErrorMessage = "Duplicate WeGlide flight",
                        remoteFlightId = result.remoteFlightId,
                        updatedAtEpochMs = clock.nowWallMs()
                    )
                )
                result
            }
            is WeGlideUploadExecutionResult.RetryableFailure -> {
                val nextRetryCount = current.retryCount + 1
                if (nextRetryCount >= MAX_RETRIES) {
                    val permanent = WeGlideUploadExecutionResult.PermanentFailure(
                        code = result.code,
                        message = "WeGlide max retries exceeded: ${result.message}"
                    )
                    queueRepository.upsert(
                        uploading.copy(
                            uploadState = WeGlideUploadState.FAILED_PERMANENT,
                            retryCount = nextRetryCount,
                            lastErrorCode = permanent.code,
                            lastErrorMessage = permanent.message,
                            updatedAtEpochMs = clock.nowWallMs()
                        )
                    )
                    permanent
                } else {
                    queueRepository.upsert(
                        uploading.copy(
                            uploadState = WeGlideUploadState.FAILED_RETRYABLE,
                            retryCount = nextRetryCount,
                            lastErrorCode = result.code,
                            lastErrorMessage = result.message,
                            updatedAtEpochMs = clock.nowWallMs()
                        )
                    )
                    result
                }
            }
            is WeGlideUploadExecutionResult.PermanentFailure -> {
                queueRepository.upsert(
                    uploading.copy(
                        uploadState = WeGlideUploadState.FAILED_PERMANENT,
                        retryCount = current.retryCount + 1,
                        lastErrorCode = result.code,
                        lastErrorMessage = result.message,
                        updatedAtEpochMs = clock.nowWallMs()
                    )
                )
                result
            }
        }
    }

    companion object {
        private const val MAX_RETRIES = 5
    }
}
