package com.example.xcpro.weglide.domain

interface WeGlideFlightUploadRepository {
    suspend fun uploadQueuedFlight(item: WeGlideUploadQueueRecord): WeGlideUploadExecutionResult
}
