package com.trust3.xcpro.weglide.domain

interface WeGlideUploadQueueRepository {
    suspend fun getByLocalFlightId(localFlightId: String): WeGlideUploadQueueRecord?

    suspend fun getUploadedBySha256(sha256: String): WeGlideUploadQueueRecord?

    suspend fun upsert(record: WeGlideUploadQueueRecord)
}
