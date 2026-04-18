package com.trust3.xcpro.weglide.domain

sealed interface WeGlideUploadExecutionResult {
    data class Success(
        val remoteFlightId: Long?
    ) : WeGlideUploadExecutionResult

    data class Duplicate(
        val remoteFlightId: Long?
    ) : WeGlideUploadExecutionResult

    data class RetryableFailure(
        val code: Int?,
        val message: String
    ) : WeGlideUploadExecutionResult

    data class PermanentFailure(
        val code: Int?,
        val message: String
    ) : WeGlideUploadExecutionResult
}
