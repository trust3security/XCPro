package com.example.xcpro.adsb

import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal fun AdsbTrafficRepositoryRuntime.ensureLoopRunning() {
    synchronized(loopJobLock) {
        val existing = loopJob
        if (existing != null && existing.isActive) return
        loopJob = scope.launch {
            runLoop()
        }
    }
}

internal suspend fun AdsbTrafficRepositoryRuntime.stopLoop(clearTargets: Boolean) {
    val jobToCancel = synchronized(loopJobLock) {
        val existing = loopJob
        loopJob = null
        existing
    }
    jobToCancel?.cancelAndJoin()
    connectionState = AdsbConnectionState.Disabled
    lastError = null
    lastNetworkFailureKind = null
    pollingHealthPolicy.resetForStop()
    if (clearTargets) {
        clearCachedTargets()
    }
    publishSnapshot()
}

internal fun AdsbTrafficRepositoryRuntime.clearCachedTargets() {
    store.clear()
    _targets.value = emptyList()
    fetchedCount = 0
    withinRadiusCount = 0
    withinVerticalCount = 0
    filteredByVerticalCount = 0
    cappedCount = 0
    emergencyAudioCandidateId = null
    consecutiveEmptyPolls = 0
    lastPolledCenter = null
    lastOwnshipAltitudeReselectMonoMs = Long.MIN_VALUE
    lastOwnshipAltitudeReselectMeters = ownshipAltitudeMeters
}

internal fun AdsbTrafficRepositoryRuntime.observeEmergencyAudioSettings() {
    scope.launch {
        combine(
            emergencyAudioSettingsPort.emergencyAudioEnabledFlow,
            emergencyAudioSettingsPort.emergencyAudioCooldownMsFlow
        ) { enabled, cooldownMs ->
            AdsbEmergencyAudioSettings(
                enabled = enabled,
                cooldownMs = cooldownMs
            )
        }.collect { settings ->
            emergencyAudioSettings = settings
            publishSnapshot()
        }
    }
}

internal fun AdsbTrafficRepositoryRuntime.observeEmergencyAudioRollout() {
    val rolloutPort = emergencyAudioRolloutPort ?: return
    scope.launch {
        val rolloutConfigFlow = combine(
            rolloutPort.emergencyAudioMasterEnabledFlow,
            rolloutPort.emergencyAudioShadowModeFlow
        ) { masterEnabled, shadowModeEnabled ->
            RolloutConfig(
                masterEnabled = masterEnabled,
                shadowModeEnabled = shadowModeEnabled,
            )
        }
        val rollbackStateFlow = combine(
            rolloutPort.emergencyAudioRollbackLatchedFlow,
            rolloutPort.emergencyAudioRollbackReasonFlow
        ) { rollbackLatched, rollbackReason ->
            RollbackState(
                latched = rollbackLatched,
                reason = rollbackReason
            )
        }
        combine(
            rolloutConfigFlow,
            rollbackStateFlow
        ) { rolloutConfig, rollbackState ->
            RolloutSnapshot(
                masterEnabled = rolloutConfig.masterEnabled,
                shadowModeEnabled = rolloutConfig.shadowModeEnabled,
                rollbackLatched = rollbackState.latched,
                rollbackReason = rollbackState.reason
            )
        }.collect { rollout ->
            emergencyAudioMasterConfigured = rollout.masterEnabled
            emergencyAudioShadowModeConfigured = rollout.shadowModeEnabled
            emergencyAudioRollbackLatched = rollout.rollbackLatched
            emergencyAudioRollbackReason = rollout.rollbackReason
            publishSnapshot()
        }
    }
}

private data class RolloutConfig(
    val masterEnabled: Boolean,
    val shadowModeEnabled: Boolean
)

private data class RollbackState(
    val latched: Boolean,
    val reason: String?
)

private data class RolloutSnapshot(
    val masterEnabled: Boolean,
    val shadowModeEnabled: Boolean,
    val rollbackLatched: Boolean,
    val rollbackReason: String?
)

internal suspend fun AdsbTrafficRepositoryRuntime.runLoop() {
    val thisJob = currentCoroutineContext()[Job]
    var backoffMs = RECONNECT_BACKOFF_START_MS
    try {
        while (_isEnabled.value) {
            val centerAtPoll = waitForCenter() ?: break
            val step = try {
                executePollCycle(centerAtPoll = centerAtPoll, currentBackoffMs = backoffMs)
            } catch (throwable: Throwable) {
                handleUnexpectedLoopFailure(
                    throwable = throwable,
                    currentBackoffMs = backoffMs
                )
            }
            when (step) {
                is AdsbRuntimeLoopStep.Continue -> backoffMs = step.nextBackoffMs
                AdsbRuntimeLoopStep.Stop -> break
            }
        }
        connectionState = AdsbConnectionState.Disabled
        publishSnapshot()
    } finally {
        synchronized(loopJobLock) {
            if (loopJob == thisJob) {
                loopJob = null
            }
        }
    }
}

