package com.trust3.xcpro.weglide.domain

import com.trust3.xcpro.profiles.ProfileIdResolver
import com.trust3.xcpro.profiles.ProfileRepository
import com.trust3.xcpro.weglide.data.WeGlideIgcDocumentStore
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull

class EvaluateWeGlidePostFlightPromptUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val accountStore: WeGlideAccountStore,
    private val preferencesStore: WeGlidePreferencesStore,
    private val queueRepository: WeGlideUploadQueueRepository,
    private val resolveWeGlideAircraftForProfileUseCase: ResolveWeGlideAircraftForProfileUseCase,
    private val igcDocumentStore: WeGlideIgcDocumentStore
) {

    suspend operator fun invoke(
        request: WeGlideFinalizedFlightUploadRequest
    ): WeGlidePostFlightUploadPrompt? {
        accountStore.accountLink.firstOrNull() ?: return null
        val preferences = preferencesStore.preferences.firstOrNull() ?: WeGlideUploadPreferences()
        if (!preferences.autoUploadFinishedFlights) {
            return null
        }

        val activeProfile = profileRepository.activeProfile.value
        val profileId = ProfileIdResolver.canonicalOrDefault(activeProfile?.id)
        val resolution = resolveWeGlideAircraftForProfileUseCase(profileId)
        if (resolution.status != WeGlideAircraftMappingResolution.Status.MAPPED ||
            resolution.mapping == null ||
            resolution.aircraft == null
        ) {
            return null
        }

        if (queueRepository.getByLocalFlightId(request.localFlightId) != null) {
            return null
        }

        val sha256 = runCatching {
            igcDocumentStore.sha256Hex(request.document.uri)
        }.getOrNull() ?: return null
        if (queueRepository.getUploadedBySha256(sha256) != null) {
            return null
        }

        return WeGlidePostFlightUploadPrompt(
            request = request,
            profileId = profileId,
            profileName = activeProfile?.name?.trim()?.takeIf { it.isNotBlank() },
            aircraftName = resolution.aircraft.name,
            fileName = request.document.fileName()
        )
    }
}
