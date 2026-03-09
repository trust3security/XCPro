package com.example.xcpro.adsb

internal class AdsbEmergencyAudioKpiAccumulator {
    private var lastUpdateMonoMs: Long? = null
    private var normalizedLastMonoMs: Long? = null
    private var activeObservationMs: Long = 0L
    private var disableEventCount: Int = 0
    private var disableWithin5MinCount: Int = 0
    private var retriggerWithinCooldownCount: Int = 0
    private var determinismMismatchCount: Int = 0
    private var lastPolicyEnabled: Boolean? = null
    private var previousAlertTriggerCount: Int = 0
    private var previousCooldownBlockEpisodeCount: Int = 0
    private var lastAlertMonoMs: Long? = null

    fun updateAndSnapshot(
        nowMonoMs: Long,
        observationActive: Boolean,
        policyEnabled: Boolean,
        cooldownMs: Long,
        telemetry: AdsbEmergencyAudioTelemetry
    ): AdsbEmergencyAudioKpiSnapshot {
        val now = normalizedNow(nowMonoMs)
        val lastUpdate = lastUpdateMonoMs
        if (lastUpdate != null && observationActive) {
            activeObservationMs += (now - lastUpdate).coerceAtLeast(0L)
        }
        lastUpdateMonoMs = now

        val currentAlertTriggerCount = telemetry.alertTriggerCount
        if (currentAlertTriggerCount < previousAlertTriggerCount) {
            determinismMismatchCount += 1
        } else if (currentAlertTriggerCount > previousAlertTriggerCount) {
            val newTriggers = currentAlertTriggerCount - previousAlertTriggerCount
            repeat(newTriggers) {
                val previousAlertMonoMs = lastAlertMonoMs
                if (previousAlertMonoMs != null &&
                    now - previousAlertMonoMs < cooldownMs.coerceAtLeast(0L)
                ) {
                    retriggerWithinCooldownCount += 1
                }
                lastAlertMonoMs = now
            }
        }

        telemetry.lastAlertMonoMs?.let { telemetryLastAlertMonoMs ->
            val previousAlertMonoMs = lastAlertMonoMs
            if (previousAlertMonoMs != null && telemetryLastAlertMonoMs < previousAlertMonoMs) {
                determinismMismatchCount += 1
            }
            lastAlertMonoMs = if (previousAlertMonoMs == null) {
                telemetryLastAlertMonoMs
            } else {
                maxOf(previousAlertMonoMs, telemetryLastAlertMonoMs)
            }
        }

        val cooldownBlockEpisodeCount = telemetry.cooldownBlockEpisodeCount
        if (cooldownBlockEpisodeCount < previousCooldownBlockEpisodeCount) {
            determinismMismatchCount += 1
        }

        val previousPolicyEnabled = lastPolicyEnabled
        if (previousPolicyEnabled == true && !policyEnabled) {
            disableEventCount += 1
            val currentAlertMonoMs = lastAlertMonoMs
            if (currentAlertMonoMs != null && now - currentAlertMonoMs <= DISABLE_WITHIN_FIVE_MIN_MS) {
                disableWithin5MinCount += 1
            }
        }

        previousAlertTriggerCount = currentAlertTriggerCount
        previousCooldownBlockEpisodeCount = cooldownBlockEpisodeCount
        lastPolicyEnabled = policyEnabled

        return AdsbEmergencyAudioKpiSnapshot(
            alertTriggerCount = currentAlertTriggerCount,
            cooldownBlockEpisodeCount = cooldownBlockEpisodeCount,
            activeObservationMs = activeObservationMs,
            alertsPerFlightHour = perFlightHour(currentAlertTriggerCount, activeObservationMs),
            cooldownBlockEpisodesPerFlightHour = perFlightHour(
                cooldownBlockEpisodeCount,
                activeObservationMs
            ),
            disableWithin5MinCount = disableWithin5MinCount,
            disableEventCount = disableEventCount,
            disableWithin5MinRate = safeRate(
                numerator = disableWithin5MinCount,
                denominator = disableEventCount
            ),
            retriggerWithinCooldownCount = retriggerWithinCooldownCount,
            determinismMismatchCount = determinismMismatchCount
        )
    }

    private fun normalizedNow(nowMonoMs: Long): Long {
        val previousMonoMs = normalizedLastMonoMs
        if (previousMonoMs != null && nowMonoMs < previousMonoMs) {
            determinismMismatchCount += 1
            return previousMonoMs
        }
        normalizedLastMonoMs = nowMonoMs
        return nowMonoMs
    }

    private fun perFlightHour(count: Int, activeMs: Long): Double {
        if (activeMs <= 0L || count <= 0) return 0.0
        return count.toDouble() * MILLIS_PER_HOUR.toDouble() / activeMs.toDouble()
    }

    private fun safeRate(numerator: Int, denominator: Int): Double {
        if (denominator <= 0 || numerator <= 0) return 0.0
        return numerator.toDouble() / denominator.toDouble()
    }

    private companion object {
        const val MILLIS_PER_HOUR = 60L * 60L * 1000L
        const val DISABLE_WITHIN_FIVE_MIN_MS = 5L * 60L * 1000L
    }
}
