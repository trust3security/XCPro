package com.example.xcpro.livefollow.friends

import com.example.xcpro.livefollow.account.XcAccountSnapshot
import com.example.xcpro.livefollow.data.following.FollowingLiveSnapshot
import com.example.xcpro.livefollow.data.friends.FriendsFlyingSnapshot
import com.example.xcpro.livefollow.formatAgeLabel
import com.example.xcpro.livefollow.toDisplayLabel
import com.example.xcpro.livefollow.model.LiveFollowActivePilot
import com.example.xcpro.livefollow.model.LiveFollowFollowingPilot
import kotlin.math.roundToInt

enum class FriendsFlyingWatchTargetType {
    PUBLIC_SHARE_CODE,
    AUTHENTICATED_SESSION_ID
}

data class FriendsFlyingUiState(
    val title: String = "Friends Flying",
    val isLoading: Boolean = false,
    val pilotCountLabel: String? = null,
    val publicTab: FriendsFlyingTabUiState = FriendsFlyingTabUiState(
        title = "Public"
    ),
    val followingTab: FriendsFlyingTabUiState = FriendsFlyingTabUiState(
        title = "Following"
    )
)

data class FriendsFlyingTabUiState(
    val title: String,
    val pilots: List<FriendsFlyingPilotRowUiModel> = emptyList(),
    val message: String? = null,
    val canRefresh: Boolean = false,
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    val signInRequired: Boolean = false
)

data class FriendsFlyingPilotRowUiModel(
    val watchKey: String = "",
    val watchTargetType: FriendsFlyingWatchTargetType = FriendsFlyingWatchTargetType.PUBLIC_SHARE_CODE,
    val sessionId: String? = null,
    val shareCode: String? = null,
    val displayLabel: String,
    val statusLabel: String,
    val altitudeLabel: String?,
    val recencyLabel: String?,
    val speedLabel: String?,
    val headingLabel: String?,
    val detailLabel: String?,
    val isStale: Boolean
)

data class FriendsFlyingPilotSelection(
    val watchKey: String = "",
    val watchTargetType: FriendsFlyingWatchTargetType = FriendsFlyingWatchTargetType.PUBLIC_SHARE_CODE,
    val sessionId: String? = null,
    val shareCode: String? = null,
    val displayLabel: String,
    val statusLabel: String,
    val altitudeLabel: String?,
    val speedLabel: String?,
    val headingLabel: String?,
    val recencyLabel: String?,
    val isStale: Boolean
)

internal fun FriendsFlyingPilotRowUiModel.toSelection(): FriendsFlyingPilotSelection {
    return FriendsFlyingPilotSelection(
        watchKey = watchKey,
        watchTargetType = watchTargetType,
        sessionId = sessionId,
        shareCode = shareCode,
        displayLabel = displayLabel,
        statusLabel = statusLabel,
        altitudeLabel = altitudeLabel,
        speedLabel = speedLabel,
        headingLabel = headingLabel,
        recencyLabel = recencyLabel,
        isStale = isStale
    )
}

internal fun buildFriendsFlyingUiState(
    publicSnapshot: FriendsFlyingSnapshot,
    followingSnapshot: FollowingLiveSnapshot = FollowingLiveSnapshot(),
    accountSnapshot: XcAccountSnapshot = XcAccountSnapshot(),
    nowWallMs: Long
): FriendsFlyingUiState {
    val publicPilots = publicSnapshot.items.map { pilot ->
        publicPilotRow(
            pilot = pilot,
            nowWallMs = nowWallMs
        )
    }
    val followingPilots = followingSnapshot.items.map { pilot ->
        followingPilotRow(
            pilot = pilot,
            nowWallMs = nowWallMs
        )
    }
    return FriendsFlyingUiState(
        isLoading = publicSnapshot.isLoading || followingSnapshot.isLoading,
        pilotCountLabel = friendsFlyingPilotCountLabel(
            publicPilotCount = publicPilots.size,
            followingPilotCount = followingPilots.size
        ),
        publicTab = FriendsFlyingTabUiState(
            title = "Public",
            pilots = publicPilots,
            message = publicTabMessage(
                snapshot = publicSnapshot,
                totalPilotCount = publicPilots.size
            ),
            canRefresh = publicSnapshot.sideEffectsAllowed && !publicSnapshot.isLoading,
            isLoading = publicSnapshot.isLoading,
            isError = publicSnapshot.lastError != null
        ),
        followingTab = FriendsFlyingTabUiState(
            title = "Following",
            pilots = followingPilots,
            message = followingTabMessage(
                snapshot = followingSnapshot,
                accountSnapshot = accountSnapshot,
                totalPilotCount = followingPilots.size
            ),
            canRefresh = followingSnapshot.sideEffectsAllowed &&
                followingSnapshot.isSignedIn &&
                !followingSnapshot.isLoading,
            isLoading = followingSnapshot.isLoading,
            isError = followingSnapshot.lastError != null,
            signInRequired = followingSnapshot.signInRequired
        )
    )
}

internal fun filterFriendsFlyingPilots(
    pilots: List<FriendsFlyingPilotRowUiModel>,
    rawQuery: String
): List<FriendsFlyingPilotRowUiModel> {
    val query = rawQuery.trim()
    if (query.isEmpty()) return pilots
    return pilots.filter { pilot ->
        pilot.displayLabel.contains(query, ignoreCase = true) ||
            (pilot.shareCode?.contains(query, ignoreCase = true) == true) ||
            (pilot.sessionId?.contains(query, ignoreCase = true) == true)
    }
}

private fun publicTabMessage(
    snapshot: FriendsFlyingSnapshot,
    totalPilotCount: Int
): String? {
    if (!snapshot.sideEffectsAllowed) {
        return "Friends Flying is unavailable during replay."
    }
    snapshot.lastError?.let { return it }
    if (snapshot.isLoading && totalPilotCount == 0) {
        return "Loading public live pilots..."
    }
    if (totalPilotCount == 0) {
        return "No public pilots flying right now."
    }
    return "Tap a public pilot to watch live."
}

private fun followingTabMessage(
    snapshot: FollowingLiveSnapshot,
    accountSnapshot: XcAccountSnapshot,
    totalPilotCount: Int
): String? {
    if (!snapshot.sideEffectsAllowed) {
        return "Following live is unavailable during replay."
    }
    if (!accountSnapshot.isSignedIn || snapshot.signInRequired) {
        return "Sign in to see pilots you follow who are live."
    }
    snapshot.lastError?.let { return it }
    if (snapshot.isLoading && totalPilotCount == 0) {
        return "Loading followed pilots..."
    }
    if (totalPilotCount == 0) {
        return "No followed pilots are live right now."
    }
    return "Tap a followed pilot to open authorized live watch."
}

private fun friendsFlyingPilotCountLabel(
    publicPilotCount: Int,
    followingPilotCount: Int
): String? {
    if (publicPilotCount == 0 && followingPilotCount == 0) return null
    return "Public $publicPilotCount / Following $followingPilotCount"
}

private fun publicPilotRow(
    pilot: LiveFollowActivePilot,
    nowWallMs: Long
): FriendsFlyingPilotRowUiModel {
    val latest = pilot.latest
    val isStale = pilot.status.trim().equals("stale", ignoreCase = true)
    val speedLabel = latest?.groundSpeedMs?.let { speed -> "${speed.roundToInt()} m/s" }
    val headingLabel = latest?.headingDeg?.let { heading -> "${heading.roundToInt()} deg" }
    return FriendsFlyingPilotRowUiModel(
        watchKey = pilot.shareCode,
        watchTargetType = FriendsFlyingWatchTargetType.PUBLIC_SHARE_CODE,
        sessionId = pilot.sessionId,
        shareCode = pilot.shareCode,
        displayLabel = pilot.displayLabel,
        statusLabel = pilot.status.toDisplayLabel(),
        altitudeLabel = latest?.altitudeMslMeters?.let { altitude ->
            "${altitude.roundToInt()} m MSL"
        },
        recencyLabel = livePilotRecencyLabel(
            lastPositionWallMs = pilot.lastPositionWallMs,
            latestFixWallMs = latest?.fixWallMs,
            nowWallMs = nowWallMs
        ),
        speedLabel = speedLabel,
        headingLabel = headingLabel,
        detailLabel = listOfNotNull(speedLabel, headingLabel)
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = " / "),
        isStale = isStale
    )
}

private fun followingPilotRow(
    pilot: LiveFollowFollowingPilot,
    nowWallMs: Long
): FriendsFlyingPilotRowUiModel {
    val latest = pilot.latest
    val isStale = pilot.status.trim().equals("stale", ignoreCase = true)
    val speedLabel = latest?.groundSpeedMs?.let { speed -> "${speed.roundToInt()} m/s" }
    val headingLabel = latest?.headingDeg?.let { heading -> "${heading.roundToInt()} deg" }
    val visibilityLabel = pilot.visibility?.title
    return FriendsFlyingPilotRowUiModel(
        watchKey = pilot.sessionId,
        watchTargetType = FriendsFlyingWatchTargetType.AUTHENTICATED_SESSION_ID,
        sessionId = pilot.sessionId,
        shareCode = pilot.shareCode,
        displayLabel = pilot.displayLabel,
        statusLabel = pilot.status.toDisplayLabel(),
        altitudeLabel = latest?.altitudeMslMeters?.let { altitude ->
            "${altitude.roundToInt()} m MSL"
        },
        recencyLabel = livePilotRecencyLabel(
            lastPositionWallMs = pilot.lastPositionWallMs,
            latestFixWallMs = latest?.fixWallMs,
            nowWallMs = nowWallMs
        ),
        speedLabel = speedLabel,
        headingLabel = headingLabel,
        detailLabel = listOfNotNull(visibilityLabel, speedLabel, headingLabel)
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = " / "),
        isStale = isStale
    )
}

private fun livePilotRecencyLabel(
    lastPositionWallMs: Long?,
    latestFixWallMs: Long?,
    nowWallMs: Long
): String? {
    val updatedWallMs = listOfNotNull(
        lastPositionWallMs?.takeIf { it > 0L },
        latestFixWallMs?.takeIf { it > 0L }
    ).maxOrNull() ?: return null
    val ageLabel = formatAgeLabel(nowWallMs - updatedWallMs) ?: return null
    return "Updated $ageLabel ago"
}
