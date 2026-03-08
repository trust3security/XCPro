package com.example.xcpro.adsb

import kotlin.math.abs

internal const val ADSB_EMERGENCY_MAX_AGE_SEC = 20
internal const val ADSB_CIRCLING_RED_DISTANCE_METERS = 1_000.0
internal const val ADSB_CIRCLING_EMERGENCY_VERTICAL_CAP_METERS = 304.8
internal const val ADSB_VERTICAL_NON_THREAT_DELTA_METERS = 1_200.0

internal fun proximityReason(
    hasOwnshipReference: Boolean,
    isVerticalNonThreat: Boolean,
    isCirclingEmergencyRedRule: Boolean,
    isEmergencyCollisionRisk: Boolean,
    hasTrendSample: Boolean,
    isClosing: Boolean,
    showClosingAlert: Boolean
): AdsbProximityReason {
    if (!hasOwnshipReference) return AdsbProximityReason.NO_OWNSHIP_REFERENCE
    if (isVerticalNonThreat) return AdsbProximityReason.DIVERGING_OR_STEADY
    if (isCirclingEmergencyRedRule) return AdsbProximityReason.CIRCLING_RULE_APPLIED
    if (isEmergencyCollisionRisk) return AdsbProximityReason.GEOMETRY_EMERGENCY_APPLIED
    if (isClosing) return AdsbProximityReason.APPROACH_CLOSING
    if (hasTrendSample && showClosingAlert) return AdsbProximityReason.RECOVERY_DWELL
    return AdsbProximityReason.DIVERGING_OR_STEADY
}

internal fun emergencyAudioIneligibilityReason(
    isEmergencyAudioEligible: Boolean,
    hasOwnshipReference: Boolean,
    isVerticalNonThreat: Boolean,
    trendAssessment: AdsbProximityTrendAssessment,
    collisionRiskReason: AdsbEmergencyAudioIneligibilityReason?
): AdsbEmergencyAudioIneligibilityReason? {
    if (isEmergencyAudioEligible) return null
    if (!hasOwnshipReference) return AdsbEmergencyAudioIneligibilityReason.NO_OWNSHIP_REFERENCE
    if (isVerticalNonThreat) return AdsbEmergencyAudioIneligibilityReason.VERTICAL_NON_THREAT
    if (
        trendAssessment.hasTrendSample &&
        !trendAssessment.hasFreshTrendSample &&
        !trendAssessment.isClosing
    ) {
        return AdsbEmergencyAudioIneligibilityReason.TREND_STALE_WAITING_FOR_FRESH_SAMPLE
    }
    return collisionRiskReason
}

internal fun isCirclingEmergencyRedRule(
    distanceMeters: Double,
    altitudeDeltaMeters: Double?,
    enabled: Boolean,
    verticalAboveMeters: Double,
    verticalBelowMeters: Double
): Boolean {
    if (!enabled) return false
    if (!distanceMeters.isFinite() || distanceMeters > ADSB_CIRCLING_RED_DISTANCE_METERS) return false
    val altitudeDelta = altitudeDeltaMeters ?: return false
    val above = altitudeDelta
    val below = -altitudeDelta
    return above <= verticalAboveMeters && below <= verticalBelowMeters
}

internal fun isVerticalNonThreat(
    altitudeDeltaMeters: Double?,
    hasOwnshipReference: Boolean
): Boolean {
    if (!hasOwnshipReference) return false
    val altitudeDelta = altitudeDeltaMeters ?: return false
    if (!altitudeDelta.isFinite()) return false
    return abs(altitudeDelta) >= ADSB_VERTICAL_NON_THREAT_DELTA_METERS
}

internal fun contactAgeSec(nowWallEpochSec: Long?, lastContactEpochSec: Long?): Int? {
    if (nowWallEpochSec == null || lastContactEpochSec == null) return null
    if (nowWallEpochSec < lastContactEpochSec) return null
    return (nowWallEpochSec - lastContactEpochSec).toInt().coerceAtLeast(0)
}

internal fun trendSampleMonoMs(
    targetReceivedMonoMs: Long,
    ownshipReferenceSampleMonoMs: Long?,
    usesOwnshipReference: Boolean
): Long {
    if (!usesOwnshipReference) return targetReceivedMonoMs
    val ownshipSample = ownshipReferenceSampleMonoMs ?: return targetReceivedMonoMs
    return maxOf(targetReceivedMonoMs, ownshipSample)
}
