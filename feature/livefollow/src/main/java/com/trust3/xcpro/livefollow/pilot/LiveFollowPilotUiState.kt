package com.trust3.xcpro.livefollow.pilot

import com.trust3.xcpro.livefollow.account.XcAccountSnapshot
import com.trust3.xcpro.livefollow.data.session.LiveFollowCommandResult
import com.trust3.xcpro.livefollow.data.session.LiveFollowSessionLifecycle
import com.trust3.xcpro.livefollow.data.session.LiveFollowSessionRole
import com.trust3.xcpro.livefollow.data.session.LiveFollowSessionSnapshot
import com.trust3.xcpro.livefollow.data.session.LiveFollowSessionVisibility
import com.trust3.xcpro.livefollow.liveFollowTransportLabel
import com.trust3.xcpro.livefollow.model.LiveOwnshipSnapshot
import com.trust3.xcpro.livefollow.state.LiveFollowReplayBlockReason
import com.trust3.xcpro.livefollow.toDisplayLabel

enum class LiveFollowPilotShareIndicatorState {
    STARTING,
    LIVE,
    FAILED,
    STOPPED
}

data class LiveFollowPilotUiState(
    val lifecycleLabel: String = "Idle",
    val sessionId: String? = null,
    val shareCode: String? = null,
    val sessionTransportLabel: String = "Available",
    val replayBlockReasonLabel: String = "None",
    val ownshipIdentityLabel: String = "Unavailable",
    val ownshipSourceLabel: String = "Unknown",
    val ownshipQualityLabel: String = "Position unavailable / vertical unavailable",
    val statusMessage: String = "Ready to share when live data is available.",
    val lastError: String? = null,
    val selectedVisibility: LiveFollowSessionVisibility = LiveFollowSessionVisibility.PUBLIC,
    val currentVisibilityLabel: String = "Public",
    val currentVisibilitySummary: String = "Visible through the public LiveFollow lane.",
    val isSignedIn: Boolean = false,
    val canUsePrivateVisibility: Boolean = false,
    val canStartSharing: Boolean = false,
    val canStopSharing: Boolean = false,
    val canUpdateVisibility: Boolean = false,
    val canCopyShareCode: Boolean = false,
    val shareIndicatorState: LiveFollowPilotShareIndicatorState = LiveFollowPilotShareIndicatorState.STOPPED,
    val isBusy: Boolean = false
)

internal enum class LiveFollowPilotPendingCommand {
    START_SHARING,
    STOP_SHARING,
    UPDATE_VISIBILITY
}

internal data class LiveFollowPilotActionState(
    val isBusy: Boolean = false,
    val commandMessage: String? = null,
    val pendingCommand: LiveFollowPilotPendingCommand? = null,
    val lastShareCommandFailed: Boolean = false,
    val manualVisibilitySelection: LiveFollowSessionVisibility? = null
)

internal fun buildLiveFollowPilotUiState(
    session: LiveFollowSessionSnapshot,
    ownshipSnapshot: LiveOwnshipSnapshot?,
    accountSnapshot: XcAccountSnapshot = XcAccountSnapshot(),
    actionState: LiveFollowPilotActionState
): LiveFollowPilotUiState {
    val accountDefaultVisibility = LiveFollowSessionVisibility.fromWireValue(
        accountSnapshot.privacy?.defaultLiveVisibility?.wireValue
    ) ?: LiveFollowSessionVisibility.PUBLIC
    val selectedVisibility = session.visibility
        ?: actionState.manualVisibilitySelection
        ?: accountDefaultVisibility
    val canUsePrivateVisibility = accountSnapshot.isSignedIn
    val canStartSharing = !actionState.isBusy &&
        session.sideEffectsAllowed &&
        session.transportAvailability.isAvailable &&
        session.role == LiveFollowSessionRole.NONE &&
        session.lifecycle == LiveFollowSessionLifecycle.IDLE &&
        ownshipSnapshot?.canonicalIdentity != null &&
        (selectedVisibility == LiveFollowSessionVisibility.PUBLIC || canUsePrivateVisibility)
    val canStopSharing = !actionState.isBusy &&
        session.sideEffectsAllowed &&
        session.role == LiveFollowSessionRole.PILOT &&
        session.sessionId != null
    val canUpdateVisibility = !actionState.isBusy &&
        session.sideEffectsAllowed &&
        session.role == LiveFollowSessionRole.PILOT &&
        session.lifecycle == LiveFollowSessionLifecycle.ACTIVE &&
        session.ownerUserId != null &&
        session.visibility != null &&
        session.visibility != selectedVisibility
    val ownshipQualityLabel = buildString {
        append("Position ")
        append(ownshipSnapshot?.positionQuality?.state?.name?.toDisplayLabel() ?: "Unavailable")
        append(" / Vertical ")
        append(ownshipSnapshot?.verticalQuality?.state?.name?.toDisplayLabel() ?: "Unavailable")
    }
    return LiveFollowPilotUiState(
        lifecycleLabel = session.lifecycle.name.toDisplayLabel(),
        sessionId = session.sessionId,
        shareCode = session.shareCode,
        sessionTransportLabel = liveFollowTransportLabel(session.transportAvailability),
        replayBlockReasonLabel = session.replayBlockReason.name.toDisplayLabel(),
        ownshipIdentityLabel = ownshipSnapshot?.canonicalIdentity?.canonicalKey ?: "Unavailable",
        ownshipSourceLabel = ownshipSnapshot?.sourceLabel?.name?.toDisplayLabel() ?: "Unknown",
        ownshipQualityLabel = ownshipQualityLabel,
        statusMessage = pilotStatusMessage(
            session = session,
            actionState = actionState,
            isSignedIn = accountSnapshot.isSignedIn,
            selectedVisibility = selectedVisibility
        ),
        lastError = session.lastError,
        selectedVisibility = selectedVisibility,
        currentVisibilityLabel = (session.visibility ?: selectedVisibility).title,
        currentVisibilitySummary = (session.visibility ?: selectedVisibility).subtitle,
        isSignedIn = accountSnapshot.isSignedIn,
        canUsePrivateVisibility = canUsePrivateVisibility,
        canStartSharing = canStartSharing,
        canStopSharing = canStopSharing,
        canUpdateVisibility = canUpdateVisibility,
        canCopyShareCode = !actionState.isBusy && !session.shareCode.isNullOrBlank(),
        shareIndicatorState = pilotShareIndicatorState(
            session = session,
            actionState = actionState
        ),
        isBusy = actionState.isBusy
    )
}

internal fun liveFollowPilotCommandMessage(
    result: LiveFollowCommandResult
): String {
    return when (result) {
        LiveFollowCommandResult.Success -> "LiveFollow command completed."
        is LiveFollowCommandResult.Rejected -> result.reason.name.toDisplayLabel()
        is LiveFollowCommandResult.Failure -> result.message
    }
}

private fun pilotStatusMessage(
    session: LiveFollowSessionSnapshot,
    actionState: LiveFollowPilotActionState,
    isSignedIn: Boolean,
    selectedVisibility: LiveFollowSessionVisibility
): String {
    actionState.commandMessage?.let { message -> return message }
    session.lastError?.let { error -> return error }
    if (!session.sideEffectsAllowed) {
        return blockedStatusMessage(session.replayBlockReason)
    }
    if (!session.transportAvailability.isAvailable) {
        return session.transportAvailability.message
            ?: "LiveFollow session transport unavailable."
    }
    if (!isSignedIn && selectedVisibility != LiveFollowSessionVisibility.PUBLIC) {
        return "Sign in to use Off or Followers live visibility. Signed-out sharing stays public."
    }
    return when {
        session.role == LiveFollowSessionRole.PILOT &&
            session.lifecycle == LiveFollowSessionLifecycle.ACTIVE -> {
            "LiveFollow sharing is active (${(session.visibility ?: selectedVisibility).title})."
        }

        session.lifecycle == LiveFollowSessionLifecycle.STARTING -> {
            "Starting LiveFollow sharing..."
        }

        session.lifecycle == LiveFollowSessionLifecycle.STOPPING -> {
            "Stopping LiveFollow sharing..."
        }

        else -> "Ready to share when live data is available."
    }
}

private fun blockedStatusMessage(
    reason: LiveFollowReplayBlockReason
): String {
    return when (reason) {
        LiveFollowReplayBlockReason.NONE ->
            "LiveFollow sharing is currently blocked."
        LiveFollowReplayBlockReason.REPLAY_MODE ->
            "LiveFollow sharing is blocked during replay."
        LiveFollowReplayBlockReason.SIMULATOR_SOURCE ->
            "LiveFollow sharing is blocked while a simulator source is active."
    }
}

private fun pilotShareIndicatorState(
    session: LiveFollowSessionSnapshot,
    actionState: LiveFollowPilotActionState
): LiveFollowPilotShareIndicatorState {
    return when {
        session.role == LiveFollowSessionRole.PILOT &&
            session.lifecycle == LiveFollowSessionLifecycle.ACTIVE -> {
            LiveFollowPilotShareIndicatorState.LIVE
        }

        session.lifecycle == LiveFollowSessionLifecycle.STARTING ||
            (
                actionState.isBusy &&
                    actionState.pendingCommand in setOf(
                        LiveFollowPilotPendingCommand.START_SHARING,
                        LiveFollowPilotPendingCommand.UPDATE_VISIBILITY
                    )
                ) -> {
            LiveFollowPilotShareIndicatorState.STARTING
        }

        session.lifecycle == LiveFollowSessionLifecycle.ERROR ||
            actionState.lastShareCommandFailed ||
            (
                session.role != LiveFollowSessionRole.PILOT &&
                    !session.lastError.isNullOrBlank()
                ) -> {
            LiveFollowPilotShareIndicatorState.FAILED
        }

        else -> LiveFollowPilotShareIndicatorState.STOPPED
    }
}
