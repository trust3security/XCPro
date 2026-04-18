package com.trust3.xcpro.adsb

object AdsbEmergencyAudioKpiPolicy {
    internal const val DISABLE_WITHIN_FIVE_MIN_RATE_THRESHOLD = 0.20
    internal const val DISABLE_WITHIN_FIVE_MIN_MIN_EVENTS = 2

    fun firstViolationCode(kpis: AdsbEmergencyAudioKpiSnapshot): String? = when {
        kpis.retriggerWithinCooldownCount > 0 -> "retrigger_within_cooldown_count"
        kpis.determinismMismatchCount > 0 -> "determinism_mismatch_count"
        isDisableWithinFiveMinutesRateViolated(kpis) -> "disable_within_5min_rate"
        else -> null
    }

    fun isDisableWithinFiveMinutesRateViolated(kpis: AdsbEmergencyAudioKpiSnapshot): Boolean =
        kpis.disableEventCount >= DISABLE_WITHIN_FIVE_MIN_MIN_EVENTS &&
            kpis.disableWithin5MinRate > DISABLE_WITHIN_FIVE_MIN_RATE_THRESHOLD
}
