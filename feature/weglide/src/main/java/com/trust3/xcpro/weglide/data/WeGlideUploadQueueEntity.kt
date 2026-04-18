package com.trust3.xcpro.weglide.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weglide_upload_queue")
data class WeGlideUploadQueueEntity(
    @PrimaryKey val localFlightId: String,
    val igcPath: String,
    val localProfileId: String,
    val scoringDate: String?,
    val sha256: String,
    val uploadState: String,
    val retryCount: Int,
    val lastErrorCode: Int?,
    val lastErrorMessage: String?,
    val remoteFlightId: Long?,
    val queuedAtEpochMs: Long,
    val updatedAtEpochMs: Long
)
