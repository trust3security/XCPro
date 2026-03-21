package com.example.xcpro.livefollow.watch

import com.example.xcpro.livefollow.formatAgeLabel
import com.example.xcpro.livefollow.liveFollowTaskAttachmentMessage
import com.example.xcpro.livefollow.liveFollowTransportLabel
import com.example.xcpro.livefollow.toDisplayLabel
import com.example.xcpro.livefollow.data.session.LiveFollowCommandResult
import com.example.xcpro.livefollow.data.session.LiveFollowSessionLifecycle
import com.example.xcpro.livefollow.data.session.LiveFollowSessionRole
import com.example.xcpro.livefollow.data.session.LiveFollowSessionSnapshot
import com.example.xcpro.livefollow.data.watch.WatchTrafficSnapshot
import com.example.xcpro.livefollow.model.LiveFollowSourceType
import com.example.xcpro.livefollow.state.LiveFollowSessionState

data class LiveFollowWatchUiState(
    val visible: Boolean = false,
    val sessionId: String? = null,
    val shareCode: String? = null,
    val lifecycleLabel: String = "Idle",
    val headline: String = "No active watch session",
    val detail: String = "Open a LiveFollow watch route to begin.",
    val sessionTransportLabel: String = "Available",
    val sourceLabel: String = "Unavailable",
    val stateLabel: String = "Stopped",
    val directTransportLabel: String = "Available",
    val aircraftLabel: String? = null,
    val aircraftIdentityLabel: String? = null,
    val fixAgeLabel: String? = null,
    val feedbackMessage: String? = null,
    val directTransportMessage: String? = null,
    val taskMessage: String? = null,
    val canStopWatching: Boolean = false,
    val canDismissMessage: Boolean = false,
    val isBusy: Boolean = false,
    val mapRenderState: LiveFollowMapRenderState = LiveFollowMapRenderState()
)

internal data class LiveFollowWatchRouteFeedback(
    val requestedSessionId: String? = null,
    val requestedShareCode: String? = null,
    val message: String? = null,
    val isBusy: Boolean = false
)

internal fun buildLiveFollowWatchUiState(
    session: LiveFollowSessionSnapshot,
    watchSnapshot: WatchTrafficSnapshot,
    feedback: LiveFollowWatchRouteFeedback
): LiveFollowWatchUiState {
    val hasActiveWatch = session.role == LiveFollowSessionRole.WATCHER ||
        session.lifecycle == LiveFollowSessionLifecycle.JOINING ||
        session.lifecycle == LiveFollowSessionLifecycle.STOPPING
    val mapRenderState = buildLiveFollowMapRenderState(
        session = session,
        watchSnapshot = watchSnapshot
    )
    val feedbackMessage = feedback.message ?: session.lastError
    val taskMessage = liveFollowTaskAttachmentMessage(mapRenderState.taskRenderPolicy)
    val visible = hasActiveWatch ||
        feedback.requestedSessionId != null ||
        feedback.requestedShareCode != null ||
        feedbackMessage != null
    return LiveFollowWatchUiState(
        visible = visible,
        sessionId = session.sessionId ?: feedback.requestedSessionId,
        shareCode = session.shareCode ?: feedback.requestedShareCode,
        lifecycleLabel = session.lifecycle.name.toDisplayLabel(),
        sessionTransportLabel = liveFollowTransportLabel(session.transportAvailability),
        headline = watchHeadline(
            hasActiveWatch = hasActiveWatch,
            watchState = watchSnapshot.sourceState,
            feedbackMessage = feedbackMessage
        ),
        detail = watchDetail(
            hasActiveWatch = hasActiveWatch,
            session = session,
            watchSnapshot = watchSnapshot,
            feedbackMessage = feedbackMessage
        ),
        sourceLabel = sourceLabel(
            state = watchSnapshot.sourceState,
            activeSource = watchSnapshot.activeSource
        ),
        stateLabel = watchSnapshot.sourceState.name.toDisplayLabel(),
        directTransportLabel = liveFollowTransportLabel(watchSnapshot.directTransportAvailability),
        aircraftLabel = watchSnapshot.aircraft?.displayLabel,
        aircraftIdentityLabel = watchSnapshot.aircraft?.canonicalIdentity?.canonicalKey
            ?: session.watchIdentity?.canonicalIdentity?.canonicalKey,
        fixAgeLabel = formatAgeLabel(watchSnapshot.ageMs),
        feedbackMessage = feedbackMessage,
        directTransportMessage = watchDirectTransportMessage(
            hasActiveWatch = hasActiveWatch,
            requestedSessionId = feedback.requestedSessionId,
            requestedShareCode = feedback.requestedShareCode,
            feedbackMessage = feedbackMessage,
            watchSnapshot = watchSnapshot
        ),
        taskMessage = taskMessage,
        canStopWatching = !feedback.isBusy &&
            session.sideEffectsAllowed &&
            session.role == LiveFollowSessionRole.WATCHER &&
            session.sessionId != null,
        canDismissMessage = !hasActiveWatch && feedbackMessage != null,
        isBusy = feedback.isBusy,
        mapRenderState = mapRenderState
    )
}

internal fun buildLiveFollowMapRenderState(
    session: LiveFollowSessionSnapshot,
    watchSnapshot: WatchTrafficSnapshot
): LiveFollowMapRenderState {
    val taskRenderPolicy = when (watchSnapshot.sourceState) {
        LiveFollowSessionState.AMBIGUOUS -> LiveFollowTaskRenderPolicy.BLOCKED_AMBIGUOUS
        LiveFollowSessionState.WAITING,
        LiveFollowSessionState.STOPPED -> LiveFollowTaskRenderPolicy.HIDDEN
        else -> LiveFollowTaskRenderPolicy.READ_ONLY_UNAVAILABLE
    }
    return LiveFollowMapRenderState(
        isVisible = session.role == LiveFollowSessionRole.WATCHER ||
            session.lifecycle == LiveFollowSessionLifecycle.JOINING ||
            session.lifecycle == LiveFollowSessionLifecycle.STOPPING,
        sessionId = session.sessionId,
        lifecycle = session.lifecycle,
        sourceState = watchSnapshot.sourceState,
        activeSource = watchSnapshot.activeSource,
        displayLabel = watchSnapshot.aircraft?.displayLabel,
        latitudeDeg = watchSnapshot.aircraft?.latitudeDeg,
        longitudeDeg = watchSnapshot.aircraft?.longitudeDeg,
        trackDeg = watchSnapshot.aircraft?.trackDeg,
        ageMs = watchSnapshot.ageMs,
        taskRenderPolicy = taskRenderPolicy
    )
}

internal fun liveFollowWatchCommandMessage(
    result: LiveFollowCommandResult
): String {
    return when (result) {
        LiveFollowCommandResult.Success -> "LiveFollow watch command completed."
        is LiveFollowCommandResult.Rejected -> result.reason.name.toDisplayLabel()
        is LiveFollowCommandResult.Failure -> result.message
    }
}

private fun watchHeadline(
    hasActiveWatch: Boolean,
    watchState: LiveFollowSessionState,
    feedbackMessage: String?
): String {
    if (feedbackMessage != null && !hasActiveWatch) {
        return "Unable to join LiveFollow"
    }
    return when (watchState) {
        LiveFollowSessionState.WAITING -> "Waiting for watch data"
        LiveFollowSessionState.LIVE_OGN -> "Watching via OGN"
        LiveFollowSessionState.LIVE_DIRECT -> "Watching via direct source"
        LiveFollowSessionState.AMBIGUOUS -> "Identity is ambiguous"
        LiveFollowSessionState.STALE -> "Watch data is stale"
        LiveFollowSessionState.OFFLINE -> "Watch data is offline"
        LiveFollowSessionState.STOPPED -> "No active watch session"
    }
}

private fun watchDetail(
    hasActiveWatch: Boolean,
    session: LiveFollowSessionSnapshot,
    watchSnapshot: WatchTrafficSnapshot,
    feedbackMessage: String?
): String {
    if (feedbackMessage != null && !hasActiveWatch) {
        return feedbackMessage
    }
    return when (watchSnapshot.sourceState) {
        LiveFollowSessionState.WAITING -> "Session opened. Waiting for a confirmed traffic source."
        LiveFollowSessionState.AMBIGUOUS -> "Resolve identity ambiguity before trusting the watch target."
        LiveFollowSessionState.STALE -> "The last confirmed watch fix is no longer fresh."
        LiveFollowSessionState.OFFLINE -> "No usable watch source is currently delivering fixes."
        LiveFollowSessionState.STOPPED -> "Open a LiveFollow watch route to begin."
        LiveFollowSessionState.LIVE_OGN,
        LiveFollowSessionState.LIVE_DIRECT -> buildString {
            append(
                watchSnapshot.aircraft?.displayLabel
                    ?: session.shareCode
                    ?: session.watchIdentity?.canonicalIdentity?.canonicalKey
                    ?: "Watching session"
            )
            val ageLabel = formatAgeLabel(watchSnapshot.ageMs)
            if (ageLabel != null) {
                append(" updated ")
                append(ageLabel)
                append(" ago.")
            }
        }
    }
}

private fun sourceLabel(
    state: LiveFollowSessionState,
    activeSource: LiveFollowSourceType?
): String {
    if (activeSource != null) return activeSource.name.toDisplayLabel()
    return when (state) {
        LiveFollowSessionState.LIVE_OGN -> LiveFollowSourceType.OGN.name.toDisplayLabel()
        LiveFollowSessionState.LIVE_DIRECT -> LiveFollowSourceType.DIRECT.name.toDisplayLabel()
        LiveFollowSessionState.AMBIGUOUS -> "Ambiguous"
        LiveFollowSessionState.WAITING -> "Waiting"
        LiveFollowSessionState.STALE -> "Stale"
        LiveFollowSessionState.OFFLINE -> "Offline"
        LiveFollowSessionState.STOPPED -> "Unavailable"
    }
}

private fun watchDirectTransportMessage(
    hasActiveWatch: Boolean,
    requestedSessionId: String?,
    requestedShareCode: String?,
    feedbackMessage: String?,
    watchSnapshot: WatchTrafficSnapshot
): String? {
    if (feedbackMessage != null) return null
    if (!hasActiveWatch && requestedSessionId == null && requestedShareCode == null) return null
    val availability = watchSnapshot.directTransportAvailability
    return availability.message?.takeIf { !availability.isAvailable }
}
