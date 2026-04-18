package com.trust3.xcpro.livefollow.watch

import com.trust3.xcpro.livefollow.formatAgeLabel
import com.trust3.xcpro.livefollow.liveFollowTransportLabel
import com.trust3.xcpro.livefollow.toDisplayLabel
import com.trust3.xcpro.livefollow.data.session.LiveFollowCommandResult
import com.trust3.xcpro.livefollow.data.session.LiveFollowSessionLifecycle
import com.trust3.xcpro.livefollow.data.session.LiveFollowSessionRole
import com.trust3.xcpro.livefollow.data.session.LiveFollowSessionSnapshot
import com.trust3.xcpro.livefollow.data.watch.WatchTrafficSnapshot
import com.trust3.xcpro.livefollow.model.LiveFollowSourceType
import com.trust3.xcpro.livefollow.state.LiveFollowSessionState
import kotlin.math.roundToInt

enum class LiveFollowWatchPanelTone {
    ACTIVE,
    NEUTRAL,
    WARNING
}

data class LiveFollowWatchUiState(
    val visible: Boolean = false,
    val selectedShareCode: String? = null,
    val selectedSessionId: String? = null,
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
    val panelStatusLabel: String? = null,
    val panelStatusTone: LiveFollowWatchPanelTone = LiveFollowWatchPanelTone.NEUTRAL,
    val panelAltitudeLabel: String? = null,
    val panelAglLabel: String? = null,
    val panelSpeedLabel: String? = null,
    val panelHeadingLabel: String? = null,
    val panelFreshnessLabel: String? = null,
    val fixAgeLabel: String? = null,
    val feedbackMessage: String? = null,
    val directTransportMessage: String? = null,
    val isBusy: Boolean = false,
    val mapRenderState: LiveFollowMapRenderState = LiveFollowMapRenderState()
)

data class LiveFollowWatchSelectionHint(
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

internal data class LiveFollowWatchRouteFeedback(
    val requestedSessionId: String? = null,
    val requestedShareCode: String? = null,
    val selectedTarget: LiveFollowWatchSelectionHint? = null,
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
    val selectedSessionId = feedback.selectedTarget?.sessionId
        ?: session.sessionId
        ?: feedback.requestedSessionId
    val selectedShareCode = feedback.selectedTarget?.shareCode
        ?: session.shareCode
        ?: feedback.requestedShareCode
    val selectedTargetMatchesSession = when {
        feedback.selectedTarget?.sessionId != null -> selectedSessionId == session.sessionId
        feedback.selectedTarget?.shareCode != null -> selectedShareCode == session.shareCode
        selectedSessionId != null -> selectedSessionId == session.sessionId
        selectedShareCode != null -> selectedShareCode == session.shareCode
        else -> false
    }
    val resolvedSelectedWatch = hasActiveWatch &&
        selectedTargetMatchesSession &&
        watchSnapshot.sourceState != LiveFollowSessionState.STOPPED
    val liveAircraftLabel = watchSnapshot.aircraft?.displayLabel
    val liveAltitudeLabel = watchSnapshot.aircraft?.altitudeMslMeters?.let { altitude ->
        "${altitude.roundToInt()} m MSL"
    }
    val liveAglLabel = watchSnapshot.aircraft?.aglMeters?.let { agl ->
        "${agl.roundToInt()} m"
    }
    val liveSpeedLabel = watchSnapshot.aircraft?.groundSpeedMs?.let { speed ->
        "${speed.roundToInt()} m/s"
    }
    val liveHeadingLabel = watchSnapshot.aircraft?.trackDeg?.let { heading ->
        "${heading.roundToInt()} deg"
    }
    val liveFreshnessLabel = formatAgeLabel(watchSnapshot.ageMs)?.let { age ->
        "Updated $age ago"
    }
    val aircraftLabel = when {
        selectedTargetMatchesSession && liveAircraftLabel != null -> liveAircraftLabel
        feedback.selectedTarget != null -> feedback.selectedTarget.displayLabel
        else -> liveAircraftLabel
    }
    val panelStatusLabel = when {
        resolvedSelectedWatch ->
            watchPanelStatusLabel(watchSnapshot.sourceState)
        feedback.selectedTarget != null -> feedback.selectedTarget.statusLabel
        else -> watchPanelStatusLabel(watchSnapshot.sourceState)
    }
    val panelStatusTone = when {
        resolvedSelectedWatch ->
            watchPanelStatusTone(watchSnapshot.sourceState)
        feedback.selectedTarget != null -> selectionHintPanelTone(feedback.selectedTarget.isStale)
        else -> watchPanelStatusTone(watchSnapshot.sourceState)
    }
    val panelAltitudeLabel = when {
        resolvedSelectedWatch -> liveAltitudeLabel
        feedback.selectedTarget?.altitudeLabel != null -> feedback.selectedTarget.altitudeLabel
        else -> liveAltitudeLabel
    }
    val panelSpeedLabel = when {
        resolvedSelectedWatch -> liveSpeedLabel
        feedback.selectedTarget?.speedLabel != null -> feedback.selectedTarget.speedLabel
        else -> liveSpeedLabel
    }
    val panelHeadingLabel = when {
        resolvedSelectedWatch -> liveHeadingLabel
        feedback.selectedTarget?.headingLabel != null -> feedback.selectedTarget.headingLabel
        else -> liveHeadingLabel
    }
    val panelFreshnessLabel = when {
        resolvedSelectedWatch -> liveFreshnessLabel
        feedback.selectedTarget?.recencyLabel != null -> feedback.selectedTarget.recencyLabel
        else -> liveFreshnessLabel
    }
    val visible = hasActiveWatch ||
        feedback.requestedSessionId != null ||
        feedback.requestedShareCode != null ||
        feedback.selectedTarget != null ||
        feedbackMessage != null
    return LiveFollowWatchUiState(
        visible = visible,
        selectedShareCode = selectedShareCode,
        selectedSessionId = selectedSessionId,
        sessionId = session.sessionId ?: feedback.requestedSessionId,
        shareCode = selectedShareCode,
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
        aircraftLabel = aircraftLabel,
        aircraftIdentityLabel = watchSnapshot.aircraft?.canonicalIdentity?.canonicalKey
            ?: session.watchIdentity?.canonicalIdentity?.canonicalKey,
        panelStatusLabel = panelStatusLabel,
        panelStatusTone = panelStatusTone,
        panelAltitudeLabel = panelAltitudeLabel,
        panelAglLabel = liveAglLabel,
        panelSpeedLabel = panelSpeedLabel,
        panelHeadingLabel = panelHeadingLabel,
        panelFreshnessLabel = panelFreshnessLabel,
        fixAgeLabel = formatAgeLabel(watchSnapshot.ageMs),
        feedbackMessage = feedbackMessage,
        directTransportMessage = watchDirectTransportMessage(
            hasActiveWatch = hasActiveWatch,
            requestedSessionId = feedback.requestedSessionId,
            requestedShareCode = feedback.requestedShareCode,
            feedbackMessage = feedbackMessage,
            watchSnapshot = watchSnapshot
        ),
        isBusy = feedback.isBusy,
        mapRenderState = mapRenderState
    )
}

internal fun buildLiveFollowMapRenderState(
    session: LiveFollowSessionSnapshot,
    watchSnapshot: WatchTrafficSnapshot
): LiveFollowMapRenderState {
    val watchedTask = watchSnapshot.task?.takeIf { it.isRenderable() }
    val taskRenderPolicy = when {
        watchedTask != null -> LiveFollowTaskRenderPolicy.AVAILABLE
        watchSnapshot.sourceState == LiveFollowSessionState.AMBIGUOUS ->
            LiveFollowTaskRenderPolicy.BLOCKED_AMBIGUOUS
        watchSnapshot.sourceState == LiveFollowSessionState.WAITING ||
            watchSnapshot.sourceState == LiveFollowSessionState.STOPPED ->
            LiveFollowTaskRenderPolicy.HIDDEN
        else -> LiveFollowTaskRenderPolicy.READ_ONLY_UNAVAILABLE
    }
    return LiveFollowMapRenderState(
        isVisible = session.role == LiveFollowSessionRole.WATCHER ||
            session.lifecycle == LiveFollowSessionLifecycle.JOINING ||
            session.lifecycle == LiveFollowSessionLifecycle.STOPPING,
        sessionId = session.sessionId,
        shareCode = session.shareCode,
        lifecycle = session.lifecycle,
        sourceState = watchSnapshot.sourceState,
        activeSource = watchSnapshot.activeSource,
        displayLabel = watchSnapshot.aircraft?.displayLabel,
        latitudeDeg = watchSnapshot.aircraft?.latitudeDeg,
        longitudeDeg = watchSnapshot.aircraft?.longitudeDeg,
        trackDeg = watchSnapshot.aircraft?.trackDeg,
        ageMs = watchSnapshot.ageMs,
        taskSnapshot = watchedTask,
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

private fun watchPanelStatusLabel(
    state: LiveFollowSessionState
): String? {
    return when (state) {
        LiveFollowSessionState.LIVE_OGN,
        LiveFollowSessionState.LIVE_DIRECT -> "Active"
        LiveFollowSessionState.STALE -> "Stale"
        LiveFollowSessionState.WAITING -> "Waiting"
        LiveFollowSessionState.AMBIGUOUS -> "Ambiguous"
        LiveFollowSessionState.OFFLINE -> "Unavailable"
        LiveFollowSessionState.STOPPED -> null
    }
}

private fun watchPanelStatusTone(
    state: LiveFollowSessionState
): LiveFollowWatchPanelTone {
    return when (state) {
        LiveFollowSessionState.LIVE_OGN,
        LiveFollowSessionState.LIVE_DIRECT -> LiveFollowWatchPanelTone.ACTIVE
        LiveFollowSessionState.WAITING,
        LiveFollowSessionState.STOPPED -> LiveFollowWatchPanelTone.NEUTRAL
        LiveFollowSessionState.AMBIGUOUS,
        LiveFollowSessionState.STALE,
        LiveFollowSessionState.OFFLINE -> LiveFollowWatchPanelTone.WARNING
    }
}

private fun selectionHintPanelTone(
    isStale: Boolean
): LiveFollowWatchPanelTone {
    return if (isStale) {
        LiveFollowWatchPanelTone.WARNING
    } else {
        LiveFollowWatchPanelTone.ACTIVE
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
        LiveFollowSessionState.STALE -> "Pilot is stale"
        LiveFollowSessionState.OFFLINE -> "Pilot is unavailable"
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
        LiveFollowSessionState.STALE -> "This pilot has not updated recently."
        LiveFollowSessionState.OFFLINE -> "This pilot is no longer live right now."
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
