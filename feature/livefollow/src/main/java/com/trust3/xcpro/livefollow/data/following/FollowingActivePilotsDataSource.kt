package com.trust3.xcpro.livefollow.data.following

import com.trust3.xcpro.livefollow.model.LiveFollowFollowingPilot
import com.trust3.xcpro.livefollow.model.LiveFollowTransportAvailability

interface FollowingActivePilotsDataSource {
    suspend fun fetchFollowingActivePilots(): FollowingActivePilotsFetchResult
}

sealed interface FollowingActivePilotsFetchResult {
    data class Success(
        val items: List<LiveFollowFollowingPilot>
    ) : FollowingActivePilotsFetchResult

    data object SignedOut : FollowingActivePilotsFetchResult

    data class Failure(
        val availability: LiveFollowTransportAvailability,
        val message: String
    ) : FollowingActivePilotsFetchResult
}
