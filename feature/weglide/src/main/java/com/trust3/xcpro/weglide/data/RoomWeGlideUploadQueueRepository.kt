package com.trust3.xcpro.weglide.data

import com.trust3.xcpro.weglide.domain.WeGlideUploadQueueRecord
import com.trust3.xcpro.weglide.domain.WeGlideUploadQueueRepository
import com.trust3.xcpro.weglide.domain.WeGlideUploadState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomWeGlideUploadQueueRepository @Inject constructor(
    private val queueDao: WeGlideUploadQueueDao
) : WeGlideUploadQueueRepository {

    override suspend fun getByLocalFlightId(localFlightId: String): WeGlideUploadQueueRecord? {
        return queueDao.getByLocalFlightId(localFlightId)?.toDomainRecord()
    }

    override suspend fun getUploadedBySha256(sha256: String): WeGlideUploadQueueRecord? {
        return queueDao.getUploadedBySha256(sha256)?.toDomainRecord()
    }

    override suspend fun upsert(record: WeGlideUploadQueueRecord) {
        queueDao.upsert(record.toEntity())
    }
}

private fun WeGlideUploadQueueEntity.toDomainRecord(): WeGlideUploadQueueRecord {
    return WeGlideUploadQueueRecord(
        localFlightId = localFlightId,
        igcPath = igcPath,
        localProfileId = localProfileId,
        scoringDate = scoringDate,
        sha256 = sha256,
        uploadState = runCatching { WeGlideUploadState.valueOf(uploadState) }
            .getOrDefault(WeGlideUploadState.QUEUED),
        retryCount = retryCount,
        lastErrorCode = lastErrorCode,
        lastErrorMessage = lastErrorMessage,
        remoteFlightId = remoteFlightId,
        queuedAtEpochMs = queuedAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs
    )
}

private fun WeGlideUploadQueueRecord.toEntity(): WeGlideUploadQueueEntity {
    return WeGlideUploadQueueEntity(
        localFlightId = localFlightId,
        igcPath = igcPath,
        localProfileId = localProfileId,
        scoringDate = scoringDate,
        sha256 = sha256,
        uploadState = uploadState.name,
        retryCount = retryCount,
        lastErrorCode = lastErrorCode,
        lastErrorMessage = lastErrorMessage,
        remoteFlightId = remoteFlightId,
        queuedAtEpochMs = queuedAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs
    )
}
