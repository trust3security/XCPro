package com.example.xcpro.livefollow.friends

import com.example.xcpro.livefollow.toDisplayLabel
import com.example.xcpro.livefollow.data.friends.FriendsFlyingSnapshot
import com.example.xcpro.livefollow.model.LiveFollowActivePilot
import kotlin.math.roundToInt
import java.util.Locale

data class FriendsFlyingUiState(
    val title: String = "Friends Flying",
    val isLoading: Boolean = false,
    val pilots: List<FriendsFlyingPilotRowUiModel> = emptyList(),
    val message: String? = null,
    val canRefresh: Boolean = false
)

data class FriendsFlyingPilotRowUiModel(
    val shareCode: String,
    val displayLabel: String,
    val statusLabel: String,
    val summary: String?,
    val latestSampleLabel: String?
)

internal fun buildFriendsFlyingUiState(
    snapshot: FriendsFlyingSnapshot
): FriendsFlyingUiState {
    val pilots = snapshot.items.map(::toPilotRowUiModel)
    return FriendsFlyingUiState(
        isLoading = snapshot.isLoading,
        pilots = pilots,
        message = friendsFlyingMessage(snapshot, pilots.isEmpty()),
        canRefresh = snapshot.sideEffectsAllowed && !snapshot.isLoading
    )
}

private fun friendsFlyingMessage(
    snapshot: FriendsFlyingSnapshot,
    listEmpty: Boolean
): String? {
    if (!snapshot.sideEffectsAllowed) {
        return "Friends Flying is unavailable during replay."
    }
    snapshot.lastError?.let { return it }
    if (snapshot.isLoading && listEmpty) {
        return "Loading active pilots..."
    }
    if (listEmpty) {
        return "No public pilots are flying right now."
    }
    return "Tap a pilot to open the existing watch flow."
}

private fun toPilotRowUiModel(
    pilot: LiveFollowActivePilot
): FriendsFlyingPilotRowUiModel {
    val latest = pilot.latest
    return FriendsFlyingPilotRowUiModel(
        shareCode = pilot.shareCode,
        displayLabel = pilot.displayLabel,
        statusLabel = pilot.status.toDisplayLabel(),
        summary = buildList {
            latest?.altitudeMslMeters?.let { altitude ->
                add("${altitude.roundToInt()} m MSL")
            }
            latest?.groundSpeedMs?.let { speed ->
                add("${speed.roundToInt()} m/s")
            }
            latest?.headingDeg?.let { heading ->
                add("${heading.roundToInt()} deg")
            }
        }.takeIf { it.isNotEmpty() }?.joinToString(separator = " / "),
        latestSampleLabel = latest?.let {
            String.format(Locale.US, "%.4f, %.4f", it.latitudeDeg, it.longitudeDeg)
        }
    )
}
