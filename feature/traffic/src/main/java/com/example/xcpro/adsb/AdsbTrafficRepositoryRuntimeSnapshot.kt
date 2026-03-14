package com.example.xcpro.adsb

import com.example.xcpro.core.common.logging.AppLogger
import kotlinx.coroutines.launch

private const val TAG = "AdsbTrafficRepository"

private data class EmergencyAudioSnapshotProjection(
    val state: AdsbEmergencyAudioAlertState,
    val featureGateOn: Boolean,
    val masterRolloutEnabled: Boolean,
    val masterRolloutConfigured: Boolean,
    val shadowModeEnabled: Boolean,
    val alertTriggerCount: Int,
    val cooldownBlockEpisodeCount: Int,
    val transitionEventCount: Int,
    val lastAlertMonoMs: Long?,
    val cooldownRemainingMs: Long,
    val activeTargetId: String?,
    val kpis: AdsbEmergencyAudioKpiSnapshot
)

internal fun AdsbTrafficRepositoryRuntime.publishSnapshot() {
    val activeCenter = center
    val nowMonoMs = clock.nowMonoMs()
    val pollingHealthSnapshot = pollingHealthPolicy.snapshotTelemetry()
    val hasFreshOwnshipReference = hasFreshOwnshipReference(nowMonoMs = nowMonoMs)
    updateNetworkTransitionTelemetry(nowMonoMs)
    val emergencyAudioProjection = projectEmergencyAudioSnapshot(
        nowMonoMs = nowMonoMs,
        hasFreshOwnshipReference = hasFreshOwnshipReference
    )
    val proximityReasonCounts = AdsbProximityReasonCounts.fromTargets(_targets.value)
    val offlineDwellMs = currentOfflineDwellMs(nowMonoMs)
    _snapshot.value = AdsbTrafficSnapshot(
        targets = _targets.value,
        connectionState = connectionState,
        authMode = authMode,
        centerLat = activeCenter?.latitude,
        centerLon = activeCenter?.longitude,
        usesOwnshipReference = hasFreshOwnshipReference,
        receiveRadiusKm = receiveRadiusKm,
        fetchedCount = fetchedCount,
        withinRadiusCount = withinRadiusCount,
        withinVerticalCount = withinVerticalCount,
        filteredByVerticalCount = filteredByVerticalCount,
        cappedCount = cappedCount,
        displayedCount = _targets.value.size,
        lastHttpStatus = lastHttpStatus,
        remainingCredits = remainingCredits,
        lastPollMonoMs = lastPollMonoMs,
        lastSuccessMonoMs = lastSuccessMonoMs,
        lastError = lastError,
        lastNetworkFailureKind = lastNetworkFailureKind,
        consecutiveFailureCount = pollingHealthSnapshot.consecutiveFailureCount,
        nextRetryMonoMs = pollingHealthSnapshot.nextRetryMonoMs,
        lastFailureMonoMs = pollingHealthSnapshot.lastFailureMonoMs,
        networkOnline = networkOnline,
        networkOfflineTransitionCount = networkOfflineTransitionCount,
        networkOnlineTransitionCount = networkOnlineTransitionCount,
        lastNetworkTransitionMonoMs = lastNetworkTransitionMonoMs,
        currentOfflineDwellMs = offlineDwellMs,
        emergencyAudioState = emergencyAudioProjection.state,
        emergencyAudioEnabledBySetting = emergencyAudioSettings.enabled,
        emergencyAudioFeatureGateOn = emergencyAudioProjection.featureGateOn,
        emergencyAudioMasterRolloutEnabled = emergencyAudioProjection.masterRolloutEnabled,
        emergencyAudioMasterRolloutConfigured = emergencyAudioProjection.masterRolloutConfigured,
        emergencyAudioShadowModeEnabled = emergencyAudioProjection.shadowModeEnabled,
        emergencyAudioRollbackLatched = emergencyAudioRollbackLatched,
        emergencyAudioRollbackReason = emergencyAudioRollbackReason,
        emergencyAudioCooldownMs = emergencyAudioSettings.normalizedCooldownMs,
        emergencyAudioAlertTriggerCount = emergencyAudioProjection.alertTriggerCount,
        emergencyAudioCooldownBlockEpisodeCount = emergencyAudioProjection.cooldownBlockEpisodeCount,
        emergencyAudioTransitionEventCount = emergencyAudioProjection.transitionEventCount,
        emergencyAudioLastAlertMonoMs = emergencyAudioProjection.lastAlertMonoMs,
        emergencyAudioCooldownRemainingMs = emergencyAudioProjection.cooldownRemainingMs,
        emergencyAudioActiveTargetId = emergencyAudioProjection.activeTargetId,
        proximityReasonCounts = proximityReasonCounts,
        emergencyAudioKpis = emergencyAudioProjection.kpis
    )
}

private fun AdsbTrafficRepositoryRuntime.projectEmergencyAudioSnapshot(
    nowMonoMs: Long,
    hasFreshOwnshipReference: Boolean
): EmergencyAudioSnapshotProjection {
    val emergencyAudioMasterRolloutConfigured = currentEmergencyAudioMasterConfigured()
    val emergencyAudioMasterRolloutEnabled = currentEmergencyAudioMasterEnabled()
    val emergencyAudioShadowModeEnabled = currentEmergencyAudioShadowModeEnabled()
    val emergencyAudioFeatureGateOn = emergencyAudioMasterRolloutEnabled || emergencyAudioShadowModeEnabled
    val emergencyTargetId = emergencyAudioCandidateId ?: _targets.value.firstOrNull { target ->
        target.isEmergencyAudioEligible
    }?.id
    val emergencyAudioDecision = emergencyAudioAlertFsm.evaluate(
        nowMonoMs = nowMonoMs,
        emergencyTargetId = emergencyTargetId,
        hasOwnshipReference = hasFreshOwnshipReference,
        settings = emergencyAudioSettings,
        featureFlagEnabled = emergencyAudioFeatureGateOn && _isEnabled.value
    )
    maybePlayEmergencyAudioOutput(nowMonoMs, emergencyAudioDecision)
    val emergencyAudioTelemetry = emergencyAudioAlertFsm.snapshotTelemetry(nowMonoMs)
    val emergencyAudioKpis = emergencyAudioKpiAccumulator.updateAndSnapshot(
        nowMonoMs = nowMonoMs,
        observationActive = _isEnabled.value && hasFreshOwnshipReference,
        policyEnabled = emergencyAudioFeatureGateOn && emergencyAudioSettings.enabled,
        cooldownMs = emergencyAudioSettings.normalizedCooldownMs,
        telemetry = emergencyAudioTelemetry
    )
    applyEmergencyAudioRollbackLatchIfNeeded(emergencyAudioKpis)
    return EmergencyAudioSnapshotProjection(
        state = emergencyAudioTelemetry.state,
        featureGateOn = emergencyAudioFeatureGateOn,
        masterRolloutEnabled = emergencyAudioMasterRolloutEnabled,
        masterRolloutConfigured = emergencyAudioMasterRolloutConfigured,
        shadowModeEnabled = emergencyAudioShadowModeEnabled,
        alertTriggerCount = emergencyAudioTelemetry.alertTriggerCount,
        cooldownBlockEpisodeCount = emergencyAudioTelemetry.cooldownBlockEpisodeCount,
        transitionEventCount = emergencyAudioTelemetry.transitionEventCount,
        lastAlertMonoMs = emergencyAudioTelemetry.lastAlertMonoMs,
        cooldownRemainingMs = emergencyAudioTelemetry.cooldownRemainingMs,
        activeTargetId = emergencyAudioTelemetry.activeEmergencyTargetId?.raw,
        kpis = emergencyAudioKpis
    )
}

private fun AdsbTrafficRepositoryRuntime.currentOfflineDwellMs(nowMonoMs: Long): Long {
    if (networkOnline) return 0L
    val transitionMonoMs = lastNetworkTransitionMonoMs ?: nowMonoMs
    return (nowMonoMs - transitionMonoMs).coerceAtLeast(0L)
}

internal fun AdsbTrafficRepositoryRuntime.isEmergencyAudioFeatureGateOn(): Boolean =
    currentEmergencyAudioMasterEnabled() || currentEmergencyAudioShadowModeEnabled()

internal fun AdsbTrafficRepositoryRuntime.isEmergencyAudioMasterOutputEnabled(): Boolean =
    currentEmergencyAudioMasterEnabled()

internal fun AdsbTrafficRepositoryRuntime.currentEmergencyAudioMasterConfigured(): Boolean =
    emergencyAudioMasterConfigured

internal fun AdsbTrafficRepositoryRuntime.currentEmergencyAudioMasterEnabled(): Boolean =
    currentEmergencyAudioMasterConfigured() && !emergencyAudioRollbackLatched

internal fun AdsbTrafficRepositoryRuntime.currentEmergencyAudioShadowModeEnabled(): Boolean =
    emergencyAudioShadowModeConfigured

internal fun AdsbTrafficRepositoryRuntime.maybePlayEmergencyAudioOutput(
    nowMonoMs: Long,
    decision: AdsbEmergencyAudioDecision
) {
    if (!decision.shouldPlayAlert) return
    if (!isEmergencyAudioMasterOutputEnabled()) return
    runCatching {
        emergencyAudioOutputPort.playEmergencyAlert(
            triggerMonoMs = nowMonoMs,
            emergencyTargetId = decision.activeEmergencyTargetId?.raw
        )
    }.onFailure { throwable ->
        AppLogger.w(
            TAG,
            "ADS-B emergency audio output failed: ${throwable::class.java.simpleName}"
        )
    }
}

internal fun AdsbTrafficRepositoryRuntime.applyEmergencyAudioRollbackLatchIfNeeded(
    kpis: AdsbEmergencyAudioKpiSnapshot
) {
    if (emergencyAudioRollbackLatched) return
    val reason = AdsbEmergencyAudioKpiPolicy.firstViolationCode(kpis) ?: return

    emergencyAudioRollbackLatched = true
    emergencyAudioRollbackReason = reason
    val rolloutPort = emergencyAudioRolloutPort ?: return
    scope.launch {
        runCatching {
            rolloutPort.latchEmergencyAudioRollback(reason)
        }.onFailure { throwable ->
            AppLogger.w(
                TAG,
                "ADS-B emergency rollback latch persist failed: ${throwable::class.java.simpleName}"
            )
        }
    }
}

internal fun AdsbTrafficRepositoryRuntime.updateNetworkTransitionTelemetry(nowMonoMs: Long) {
    if (connectionState is AdsbConnectionState.Disabled) return
    val currentOnline = runCatching { networkAvailabilityPort.isOnline.value }.getOrDefault(networkOnline)
    if (currentOnline == networkOnline) return
    networkOnline = currentOnline
    lastNetworkTransitionMonoMs = nowMonoMs
    if (currentOnline) {
        networkOnlineTransitionCount += 1
    } else {
        networkOfflineTransitionCount += 1
    }
}
