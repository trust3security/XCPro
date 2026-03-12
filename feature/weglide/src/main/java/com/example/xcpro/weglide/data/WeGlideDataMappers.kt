package com.example.xcpro.weglide.data

import com.example.xcpro.weglide.domain.WeGlideAircraft
import com.example.xcpro.weglide.domain.WeGlideAircraftMapping
import com.example.xcpro.weglide.domain.WeGlideUploadQueueRecord
import com.example.xcpro.weglide.domain.WeGlideUploadState

fun WeGlideAircraft.toEntity(updatedAtEpochMs: Long): WeGlideAircraftEntity {
    return WeGlideAircraftEntity(
        aircraftId = aircraftId,
        name = name,
        kind = kind,
        scoringClass = scoringClass,
        rawJson = toString(),
        updatedAtEpochMs = updatedAtEpochMs
    )
}

fun WeGlideAircraftEntity.toDomain(): WeGlideAircraft {
    return WeGlideAircraft(
        aircraftId = aircraftId,
        name = name,
        kind = kind,
        scoringClass = scoringClass
    )
}

fun WeGlideAircraftMappingEntity.toDomain(): WeGlideAircraftMapping {
    return WeGlideAircraftMapping(
        localProfileId = localProfileId,
        weglideAircraftId = weglideAircraftId,
        weglideAircraftName = weglideAircraftName,
        updatedAtEpochMs = updatedAtEpochMs
    )
}

fun WeGlideUploadQueueEntity.toDomain(): WeGlideUploadQueueRecord {
    return WeGlideUploadQueueRecord(
        localFlightId = localFlightId,
        igcPath = igcPath,
        localProfileId = localProfileId,
        scoringDate = scoringDate,
        sha256 = sha256,
        uploadState = uploadState.toUploadState(),
        retryCount = retryCount,
        lastErrorCode = lastErrorCode,
        lastErrorMessage = lastErrorMessage,
        remoteFlightId = remoteFlightId,
        queuedAtEpochMs = queuedAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs
    )
}

private fun String.toUploadState(): WeGlideUploadState {
    return runCatching { WeGlideUploadState.valueOf(this) }
        .getOrDefault(WeGlideUploadState.FAILED_PERMANENT)
}
