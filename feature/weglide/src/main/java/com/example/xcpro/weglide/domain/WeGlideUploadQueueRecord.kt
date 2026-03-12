package com.example.xcpro.weglide.domain

data class WeGlideUploadQueueRecord(
    val localFlightId: String,
    val igcPath: String,
    val localProfileId: String,
    val scoringDate: String?,
    val sha256: String,
    val uploadState: WeGlideUploadState,
    val retryCount: Int,
    val lastErrorCode: Int?,
    val lastErrorMessage: String?,
    val remoteFlightId: Long?,
    val queuedAtEpochMs: Long,
    val updatedAtEpochMs: Long
)
