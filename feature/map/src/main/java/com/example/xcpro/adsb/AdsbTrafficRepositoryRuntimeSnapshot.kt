package com.example.xcpro.adsb

import com.example.xcpro.core.common.logging.AppLogger
import kotlinx.coroutines.launch

private const val TAG = "AdsbTrafficRepository"

internal fun AdsbTrafficRepositoryRuntime.publishSnapshot() {
    val activeCenter = center
    val pollingHealthSnapshot = pollingHealthPolicy.snapshotTelemetry()
    val nowMonoMs = clock.nowMonoMs()
    val hasFreshOwnshipReference = hasFreshOwnshipReference(nowMonoMs = nowMonoMs)
    updateNetworkTransitionTelemetry(nowMonoMs)
    val emergencyAudioMasterRolloutConfigured = currentEmergencyAudioMasterConfigured()
    val emergencyAudioCohortEligible = currentEmergencyAudioCohortEligible()
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
    val proximityReasonCounts = AdsbProximityReasonCounts.fromTargets(_targets.value)
    val offlineDwellMs = if (!networkOnline) {
        val transitionMonoMs = lastNetworkTransitionMonoMs ?: nowMonoMs
        (nowMonoMs - transitionMonoMs).coerceAtLeast(0L)
    } else {
        0L
    }
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
        emergencyAudioState = emergencyAudioTelemetry.state,
        emergencyAudioEnabledBySetting = emergencyAudioSettings.enabled,
        emergencyAudioFeatureGateOn = emergencyAudioFeatureGateOn,
        emergencyAudioMasterRolloutEnabled = emergencyAudioMasterRolloutEnabled,
        emergencyAudioMasterRolloutConfigured = emergencyAudioMasterRolloutConfigured,
        emergencyAudioShadowModeEnabled = emergencyAudioShadowModeEnabled,
        emergencyAudioRolloutCohortPercent = emergencyAudioCohortPercent,
        emergencyAudioRolloutCohortBucket = emergencyAudioCohortBucket,
        emergencyAudioRolloutCohortEligible = emergencyAudioCohortEligible,
        emergencyAudioRollbackLatched = emergencyAudioRollbackLatched,
        emergencyAudioRollbackReason = emergencyAudioRollbackReason,
        emergencyAudioCooldownMs = emergencyAudioSettings.normalizedCooldownMs,
        emergencyAudioAlertTriggerCount = emergencyAudioTelemetry.alertTriggerCount,
        emergencyAudioCooldownBlockEpisodeCount =
            emergencyAudioTelemetry.cooldownBlockEpisodeCount,
        emergencyAudioTransitionEventCount = emergencyAudioTelemetry.transitionEventCount,
        emergencyAudioLastAlertMonoMs = emergencyAudioTelemetry.lastAlertMonoMs,
        emergencyAudioCooldownRemainingMs = emergencyAudioTelemetry.cooldownRemainingMs,
        emergencyAudioActiveTargetId = emergencyAudioTelemetry.activeEmergencyTargetId?.raw,
        proximityReasonCounts = proximityReasonCounts,
        emergencyAudioKpis = emergencyAudioKpis
    )
}

internal fun AdsbTrafficRepositoryRuntime.isEmergencyAudioFeatureGateOn(): Boolean =
    currentEmergencyAudioMasterEnabled() || currentEmergencyAudioShadowModeEnabled()

internal fun AdsbTrafficRepositoryRuntime.isEmergencyAudioMasterOutputEnabled(): Boolean =
    currentEmergencyAudioMasterEnabled()

internal fun AdsbTrafficRepositoryRuntime.currentEmergencyAudioMasterConfigured(): Boolean =
    if (emergencyAudioRolloutPort == null) {
        emergencyAudioFeatureFlags.emergencyAudioEnabled
    } else {
        emergencyAudioMasterEnabled
    }

internal fun AdsbTrafficRepositoryRuntime.currentEmergencyAudioCohortEligible(): Boolean {
    val normalizedPercent = emergencyAudioCohortPercent.coerceIn(
        ADSB_EMERGENCY_AUDIO_COHORT_PERCENT_MIN,
        ADSB_EMERGENCY_AUDIO_COHORT_PERCENT_MAX
    )
    val normalizedBucket = emergencyAudioCohortBucket.coerceIn(
        ADSB_EMERGENCY_AUDIO_COHORT_BUCKET_MIN,
        ADSB_EMERGENCY_AUDIO_COHORT_BUCKET_MAX
    )
    if (normalizedPercent <= ADSB_EMERGENCY_AUDIO_COHORT_PERCENT_MIN) return false
    if (normalizedPercent >= ADSB_EMERGENCY_AUDIO_COHORT_PERCENT_MAX) return true
    return normalizedBucket < normalizedPercent
}

internal fun AdsbTrafficRepositoryRuntime.currentEmergencyAudioMasterEnabled(): Boolean =
    currentEmergencyAudioMasterConfigured() &&
        currentEmergencyAudioCohortEligible() &&
        !emergencyAudioRollbackLatched

internal fun AdsbTrafficRepositoryRuntime.currentEmergencyAudioShadowModeEnabled(): Boolean =
    if (emergencyAudioRolloutPort == null) {
        emergencyAudioFeatureFlags.emergencyAudioShadowMode
    } else {
        emergencyAudioShadowMode
    }

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
