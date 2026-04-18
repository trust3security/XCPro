package com.trust3.xcpro.weglide.domain

interface WeGlideFlightUploadRepository {
    suspend fun uploadQueuedFlight(item: WeGlideUploadQueueRecord): WeGlideUploadExecutionResult
}
