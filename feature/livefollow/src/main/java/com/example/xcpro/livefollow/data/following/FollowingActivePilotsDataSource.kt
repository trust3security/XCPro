package com.example.xcpro.livefollow.data.following

import com.example.xcpro.livefollow.model.LiveFollowFollowingPilot
import com.example.xcpro.livefollow.model.LiveFollowTransportAvailability

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
