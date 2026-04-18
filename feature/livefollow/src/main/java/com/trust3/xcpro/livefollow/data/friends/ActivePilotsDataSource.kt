package com.trust3.xcpro.livefollow.data.friends

import com.trust3.xcpro.livefollow.model.LiveFollowActivePilot
import com.trust3.xcpro.livefollow.model.LiveFollowTransportAvailability

interface ActivePilotsDataSource {
    suspend fun fetchActivePilots(): ActivePilotsFetchResult
}

sealed interface ActivePilotsFetchResult {
    data class Success(
        val items: List<LiveFollowActivePilot>
    ) : ActivePilotsFetchResult

    data class Failure(
        val availability: LiveFollowTransportAvailability,
        val message: String
    ) : ActivePilotsFetchResult
}
