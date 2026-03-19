package com.example.xcpro.livefollow.pilot

import com.example.xcpro.livefollow.data.session.LiveFollowCommandResult
import com.example.xcpro.livefollow.data.session.LiveFollowSessionLifecycle
import com.example.xcpro.livefollow.data.session.LiveFollowSessionRole
import com.example.xcpro.livefollow.data.session.LiveFollowSessionSnapshot
import com.example.xcpro.livefollow.model.LiveOwnshipSnapshot
import com.example.xcpro.livefollow.toDisplayLabel

data class LiveFollowPilotUiState(
    val lifecycleLabel: String = "Idle",
    val sessionId: String? = null,
    val replayBlockReasonLabel: String = "None",
    val ownshipIdentityLabel: String = "Unavailable",
    val ownshipSourceLabel: String = "Unknown",
    val ownshipQualityLabel: String = "Position unavailable / vertical unavailable",
    val statusMessage: String = "Ready to share when live data is available.",
    val lastError: String? = null,
    val canStartSharing: Boolean = false,
    val canStopSharing: Boolean = false,
    val isBusy: Boolean = false
)

internal data class LiveFollowPilotActionState(
    val isBusy: Boolean = false,
    val commandMessage: String? = null
)

internal fun buildLiveFollowPilotUiState(
    session: LiveFollowSessionSnapshot,
    ownshipSnapshot: LiveOwnshipSnapshot?,
    actionState: LiveFollowPilotActionState
): LiveFollowPilotUiState {
    val canStartSharing = !actionState.isBusy &&
        session.sideEffectsAllowed &&
        session.role == LiveFollowSessionRole.NONE &&
        session.lifecycle == LiveFollowSessionLifecycle.IDLE &&
        ownshipSnapshot?.canonicalIdentity != null
    val canStopSharing = !actionState.isBusy &&
        session.sideEffectsAllowed &&
        session.role == LiveFollowSessionRole.PILOT &&
        session.sessionId != null
    val ownshipQualityLabel = buildString {
        append("Position ")
        append(ownshipSnapshot?.positionQuality?.state?.name?.toDisplayLabel() ?: "Unavailable")
        append(" / Vertical ")
        append(ownshipSnapshot?.verticalQuality?.state?.name?.toDisplayLabel() ?: "Unavailable")
    }
    return LiveFollowPilotUiState(
        lifecycleLabel = session.lifecycle.name.toDisplayLabel(),
        sessionId = session.sessionId,
        replayBlockReasonLabel = session.replayBlockReason.name.toDisplayLabel(),
        ownshipIdentityLabel = ownshipSnapshot?.canonicalIdentity?.canonicalKey ?: "Unavailable",
        ownshipSourceLabel = ownshipSnapshot?.sourceLabel?.name?.toDisplayLabel() ?: "Unknown",
        ownshipQualityLabel = ownshipQualityLabel,
        statusMessage = pilotStatusMessage(
            session = session,
            actionState = actionState
        ),
        lastError = session.lastError,
        canStartSharing = canStartSharing,
        canStopSharing = canStopSharing,
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
    actionState: LiveFollowPilotActionState
): String {
    actionState.commandMessage?.let { message -> return message }
    session.lastError?.let { error -> return error }
    if (!session.sideEffectsAllowed) {
        return "LiveFollow sharing is blocked during replay."
    }
    return when {
        session.role == LiveFollowSessionRole.PILOT &&
            session.lifecycle == LiveFollowSessionLifecycle.ACTIVE -> {
            "LiveFollow sharing is active."
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
