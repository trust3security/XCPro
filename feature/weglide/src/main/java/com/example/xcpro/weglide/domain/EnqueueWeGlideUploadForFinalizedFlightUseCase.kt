package com.example.xcpro.weglide.domain

import com.example.xcpro.core.time.Clock
import com.example.xcpro.profiles.ProfileIdResolver
import com.example.xcpro.profiles.ProfileRepository
import com.example.xcpro.weglide.data.WeGlideIgcDocumentStore
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull

class EnqueueWeGlideUploadForFinalizedFlightUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val accountStore: WeGlideAccountStore,
    private val preferencesStore: WeGlidePreferencesStore,
    private val queueRepository: WeGlideUploadQueueRepository,
    private val resolveWeGlideAircraftForProfileUseCase: ResolveWeGlideAircraftForProfileUseCase,
    private val igcDocumentStore: WeGlideIgcDocumentStore,
    private val workScheduler: WeGlideUploadWorkScheduler,
    private val clock: Clock
) {

    suspend operator fun invoke(
        request: WeGlideFinalizedFlightUploadRequest
    ): EnqueueWeGlideUploadResult {
        val accountLink = accountStore.accountLink.firstOrNull()
            ?: return EnqueueWeGlideUploadResult.Skipped(
                reason = EnqueueSkipReason.ACCOUNT_DISCONNECTED
            )

        val preferences = preferencesStore.preferences.firstOrNull() ?: WeGlideUploadPreferences()
        if (!preferences.autoUploadFinishedFlights) {
            return EnqueueWeGlideUploadResult.Skipped(
                reason = EnqueueSkipReason.AUTO_UPLOAD_DISABLED
            )
        }

        val profileId = ProfileIdResolver.canonicalOrDefault(profileRepository.activeProfile.value?.id)
        val resolution = resolveWeGlideAircraftForProfileUseCase(profileId)
        if (resolution.status != WeGlideAircraftMappingResolution.Status.MAPPED ||
            resolution.mapping == null ||
            resolution.aircraft == null
        ) {
            return EnqueueWeGlideUploadResult.Skipped(
                reason = EnqueueSkipReason.MAPPING_MISSING,
                detail = profileId
            )
        }

        val existing = queueRepository.getByLocalFlightId(request.localFlightId)
        if (existing != null) {
            if (existing.uploadState == WeGlideUploadState.QUEUED ||
                existing.uploadState == WeGlideUploadState.FAILED_RETRYABLE
            ) {
                workScheduler.enqueueUpload(
                    localFlightId = existing.localFlightId,
                    wifiOnly = preferences.uploadOnWifiOnly
                )
            }
            return EnqueueWeGlideUploadResult.AlreadyQueued(
                localFlightId = existing.localFlightId,
                uploadState = existing.uploadState
            )
        }

        val sha256 = runCatching {
            igcDocumentStore.sha256Hex(request.document.uri)
        }.getOrElse { error ->
            return EnqueueWeGlideUploadResult.Skipped(
                reason = EnqueueSkipReason.DOCUMENT_UNREADABLE,
                detail = error.message
            )
        }

        val duplicate = queueRepository.getUploadedBySha256(sha256)
        val now = clock.nowWallMs()
        if (duplicate != null) {
            queueRepository.upsert(
                WeGlideUploadQueueRecord(
                    localFlightId = request.localFlightId,
                    igcPath = request.document.uri,
                    localProfileId = profileId,
                    scoringDate = request.scoringDate,
                    sha256 = sha256,
                    uploadState = WeGlideUploadState.SKIPPED_DUPLICATE,
                    retryCount = 0,
                    lastErrorCode = null,
                    lastErrorMessage = "Duplicate WeGlide upload suppressed",
                    remoteFlightId = duplicate.remoteFlightId,
                    queuedAtEpochMs = now,
                    updatedAtEpochMs = now
                )
            )
            return EnqueueWeGlideUploadResult.Skipped(
                reason = EnqueueSkipReason.DUPLICATE_FLIGHT,
                detail = duplicate.localFlightId
            )
        }

        queueRepository.upsert(
            WeGlideUploadQueueRecord(
                localFlightId = request.localFlightId,
                igcPath = request.document.uri,
                localProfileId = profileId,
                scoringDate = request.scoringDate,
                sha256 = sha256,
                uploadState = WeGlideUploadState.QUEUED,
                retryCount = 0,
                lastErrorCode = null,
                lastErrorMessage = null,
                remoteFlightId = null,
                queuedAtEpochMs = now,
                updatedAtEpochMs = now
            )
        )
        workScheduler.enqueueUpload(
            localFlightId = request.localFlightId,
            wifiOnly = preferences.uploadOnWifiOnly
        )
        return EnqueueWeGlideUploadResult.Enqueued(
            localFlightId = request.localFlightId,
            localProfileId = profileId,
            authMode = accountLink.authMode
        )
    }
}

sealed interface EnqueueWeGlideUploadResult {
    data class Enqueued(
        val localFlightId: String,
        val localProfileId: String,
        val authMode: WeGlideAuthMode
    ) : EnqueueWeGlideUploadResult

    data class AlreadyQueued(
        val localFlightId: String,
        val uploadState: WeGlideUploadState
    ) : EnqueueWeGlideUploadResult

    data class Skipped(
        val reason: EnqueueSkipReason,
        val detail: String? = null
    ) : EnqueueWeGlideUploadResult
}

enum class EnqueueSkipReason {
    ACCOUNT_DISCONNECTED,
    AUTO_UPLOAD_DISABLED,
    MAPPING_MISSING,
    DOCUMENT_UNREADABLE,
    DUPLICATE_FLIGHT
}
