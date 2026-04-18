package com.trust3.xcpro.adsb

internal const val ADSB_EMERGENCY_AUDIO_DEFAULT_COOLDOWN_MS = 45_000L
internal const val ADSB_EMERGENCY_AUDIO_MIN_COOLDOWN_MS = 15_000L
internal const val ADSB_EMERGENCY_AUDIO_MAX_COOLDOWN_MS = 180_000L

internal data class AdsbEmergencyAudioSettings(
    val enabled: Boolean = false,
    val cooldownMs: Long = ADSB_EMERGENCY_AUDIO_DEFAULT_COOLDOWN_MS
) {
    val normalizedCooldownMs: Long
        get() = cooldownMs.coerceIn(
            ADSB_EMERGENCY_AUDIO_MIN_COOLDOWN_MS,
            ADSB_EMERGENCY_AUDIO_MAX_COOLDOWN_MS
        )
}

enum class AdsbEmergencyAudioAlertState {
    DISABLED,
    IDLE,
    ACTIVE,
    COOLDOWN
}

internal enum class AdsbEmergencyAudioTransitionEvent {
    DISABLED_TO_IDLE,
    IDLE_TO_ACTIVE_ALERT,
    ACTIVE_TO_COOLDOWN,
    COOLDOWN_TO_ACTIVE_ALERT,
    COOLDOWN_TO_IDLE,
    TO_DISABLED,
    COOLDOWN_BLOCKED
}

internal data class AdsbEmergencyAudioDecision(
    val state: AdsbEmergencyAudioAlertState,
    val shouldPlayAlert: Boolean,
    val activeEmergencyTargetId: Icao24?,
    val cooldownRemainingMs: Long,
    val cooldownBlockedByPolicy: Boolean
)

internal data class AdsbEmergencyAudioTelemetry(
    val state: AdsbEmergencyAudioAlertState,
    val alertTriggerCount: Int,
    val cooldownBlockEpisodeCount: Int,
    val transitionEventCount: Int,
    val lastAlertMonoMs: Long?,
    val cooldownRemainingMs: Long,
    val activeEmergencyTargetId: Icao24?
)

internal class AdsbEmergencyAudioAlertFsm {
    private var state: AdsbEmergencyAudioAlertState = AdsbEmergencyAudioAlertState.DISABLED
    private var cooldownUntilMonoMs: Long? = null
    private var activeEmergencyTargetId: Icao24? = null
    private var suppressAlertUntilEmergencyClears: Boolean = false
    private var lastDecisionMonoMs: Long = 0L
    private var lastAlertMonoMs: Long? = null
    private var alertTriggerCount: Int = 0
    private var cooldownBlockEpisodeCount: Int = 0
    private var transitionEventCount: Int = 0
    private var cooldownBlockEventRecordedForEpisode: Boolean = false
    private val transitionEvents = ArrayDeque<AdsbEmergencyAudioTransitionEvent>()

    fun clear() {
        state = AdsbEmergencyAudioAlertState.DISABLED
        cooldownUntilMonoMs = null
        activeEmergencyTargetId = null
        suppressAlertUntilEmergencyClears = false
        lastDecisionMonoMs = 0L
        lastAlertMonoMs = null
        alertTriggerCount = 0
        cooldownBlockEpisodeCount = 0
        transitionEventCount = 0
        cooldownBlockEventRecordedForEpisode = false
        transitionEvents.clear()
    }

    fun evaluate(
        nowMonoMs: Long,
        emergencyTargetId: Icao24?,
        hasOwnshipReference: Boolean,
        settings: AdsbEmergencyAudioSettings,
        featureFlagEnabled: Boolean
    ): AdsbEmergencyAudioDecision {
        val now = normalizedNow(nowMonoMs)
        val eligibleEmergencyTargetId = if (hasOwnshipReference) emergencyTargetId else null
        val enabled = featureFlagEnabled && settings.enabled
        if (!enabled) {
            updateDisabledSuppressionState(eligibleEmergencyTargetId)
            transitionToDisabledIfNeeded()
            return AdsbEmergencyAudioDecision(
                state = state,
                shouldPlayAlert = false,
                activeEmergencyTargetId = null,
                cooldownRemainingMs = 0L,
                cooldownBlockedByPolicy = false
            )
        }

        if (state == AdsbEmergencyAudioAlertState.DISABLED) {
            state = AdsbEmergencyAudioAlertState.IDLE
            recordTransitionEvent(AdsbEmergencyAudioTransitionEvent.DISABLED_TO_IDLE)
        }

        if (eligibleEmergencyTargetId == null) {
            suppressAlertUntilEmergencyClears = false
        }
        var shouldPlayAlert = false
        var cooldownBlockedByPolicy = false
        when (state) {
            AdsbEmergencyAudioAlertState.DISABLED -> {
                // covered by gate above
            }

            AdsbEmergencyAudioAlertState.IDLE -> {
                if (eligibleEmergencyTargetId != null) {
                    if (!suppressAlertUntilEmergencyClears) {
                        activateEmergency(
                            nowMonoMs = now,
                            emergencyTargetId = eligibleEmergencyTargetId,
                            event = AdsbEmergencyAudioTransitionEvent.IDLE_TO_ACTIVE_ALERT
                        )
                        shouldPlayAlert = true
                    }
                }
            }

            AdsbEmergencyAudioAlertState.ACTIVE -> {
                if (eligibleEmergencyTargetId != null) {
                    activeEmergencyTargetId = eligibleEmergencyTargetId
                } else {
                    state = AdsbEmergencyAudioAlertState.COOLDOWN
                    cooldownUntilMonoMs = now + settings.normalizedCooldownMs
                    activeEmergencyTargetId = null
                    cooldownBlockEventRecordedForEpisode = false
                    recordTransitionEvent(AdsbEmergencyAudioTransitionEvent.ACTIVE_TO_COOLDOWN)
                }
            }

            AdsbEmergencyAudioAlertState.COOLDOWN -> {
                val cooldownUntil = cooldownUntilMonoMs ?: now.also { cooldownUntilMonoMs = it }
                if (now >= cooldownUntil) {
                    if (eligibleEmergencyTargetId != null) {
                        activateEmergency(
                            nowMonoMs = now,
                            emergencyTargetId = eligibleEmergencyTargetId,
                            event = AdsbEmergencyAudioTransitionEvent.COOLDOWN_TO_ACTIVE_ALERT
                        )
                        shouldPlayAlert = true
                    } else {
                        state = AdsbEmergencyAudioAlertState.IDLE
                        cooldownUntilMonoMs = null
                        activeEmergencyTargetId = null
                        cooldownBlockEventRecordedForEpisode = false
                        recordTransitionEvent(AdsbEmergencyAudioTransitionEvent.COOLDOWN_TO_IDLE)
                    }
                } else {
                    if (eligibleEmergencyTargetId != null) {
                        cooldownBlockedByPolicy = true
                        // Count one block event per contiguous blocked episode, not per evaluation tick.
                        if (!cooldownBlockEventRecordedForEpisode) {
                            cooldownBlockEventRecordedForEpisode = true
                            cooldownBlockEpisodeCount += 1
                            recordTransitionEvent(AdsbEmergencyAudioTransitionEvent.COOLDOWN_BLOCKED)
                        }
                    } else {
                        cooldownBlockEventRecordedForEpisode = false
                    }
                }
            }
        }

        return AdsbEmergencyAudioDecision(
            state = state,
            shouldPlayAlert = shouldPlayAlert,
            activeEmergencyTargetId = activeEmergencyTargetId,
            cooldownRemainingMs = cooldownRemainingMs(now),
            cooldownBlockedByPolicy = cooldownBlockedByPolicy
        )
    }

    fun drainTransitionEvents(): List<AdsbEmergencyAudioTransitionEvent> {
        if (transitionEvents.isEmpty()) return emptyList()
        val drained = transitionEvents.toList()
        transitionEvents.clear()
        return drained
    }

    fun snapshotTelemetry(nowMonoMs: Long): AdsbEmergencyAudioTelemetry {
        val normalizedNow = if (lastDecisionMonoMs > 0L && nowMonoMs < lastDecisionMonoMs) {
            lastDecisionMonoMs
        } else {
            nowMonoMs
        }
        return AdsbEmergencyAudioTelemetry(
            state = state,
            alertTriggerCount = alertTriggerCount,
            cooldownBlockEpisodeCount = cooldownBlockEpisodeCount,
            transitionEventCount = transitionEventCount,
            lastAlertMonoMs = lastAlertMonoMs,
            cooldownRemainingMs = cooldownRemainingMs(normalizedNow),
            activeEmergencyTargetId = activeEmergencyTargetId
        )
    }

    private fun transitionToDisabledIfNeeded() {
        if (state == AdsbEmergencyAudioAlertState.DISABLED) return
        state = AdsbEmergencyAudioAlertState.DISABLED
        cooldownUntilMonoMs = null
        activeEmergencyTargetId = null
        cooldownBlockEventRecordedForEpisode = false
        recordTransitionEvent(AdsbEmergencyAudioTransitionEvent.TO_DISABLED)
    }

    private fun updateDisabledSuppressionState(eligibleEmergencyTargetId: Icao24?) {
        if (eligibleEmergencyTargetId == null) return
        val shouldSuppress =
            state == AdsbEmergencyAudioAlertState.ACTIVE ||
                state == AdsbEmergencyAudioAlertState.COOLDOWN
        if (shouldSuppress) {
            suppressAlertUntilEmergencyClears = true
        }
    }

    private fun activateEmergency(
        nowMonoMs: Long,
        emergencyTargetId: Icao24,
        event: AdsbEmergencyAudioTransitionEvent
    ) {
        state = AdsbEmergencyAudioAlertState.ACTIVE
        activeEmergencyTargetId = emergencyTargetId
        cooldownUntilMonoMs = null
        cooldownBlockEventRecordedForEpisode = false
        lastAlertMonoMs = nowMonoMs
        alertTriggerCount += 1
        recordTransitionEvent(event)
    }

    private fun normalizedNow(nowMonoMs: Long): Long {
        val now = if (lastDecisionMonoMs > 0L && nowMonoMs < lastDecisionMonoMs) {
            lastDecisionMonoMs
        } else {
            nowMonoMs
        }
        lastDecisionMonoMs = now
        return now
    }

    private fun cooldownRemainingMs(nowMonoMs: Long): Long {
        val cooldownUntil = cooldownUntilMonoMs ?: return 0L
        return (cooldownUntil - nowMonoMs).coerceAtLeast(0L)
    }

    private fun recordTransitionEvent(event: AdsbEmergencyAudioTransitionEvent) {
        transitionEventCount += 1
        transitionEvents.addLast(event)
    }
}
