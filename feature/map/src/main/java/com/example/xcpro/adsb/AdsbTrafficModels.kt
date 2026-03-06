package com.example.xcpro.adsb

internal const val ADSB_ERROR_OFFLINE = "Network unavailable"
internal const val ADSB_ERROR_CIRCUIT_BREAKER_OPEN = "ADS-B paused after repeated failures"
internal const val ADSB_ERROR_CIRCUIT_BREAKER_PROBE = "ADS-B retry probe in progress"

@JvmInline
value class Icao24(val raw: String) {
    companion object {
        fun from(value: String?): Icao24? {
            val sanitized = value
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.matches(Regex("[0-9a-f]{6}")) }
            return sanitized?.let(::Icao24)
        }
    }
}

data class AdsbTarget(
    val id: Icao24,
    val callsign: String?,
    val lat: Double,
    val lon: Double,
    val altitudeM: Double?,
    val speedMps: Double?,
    val trackDeg: Double?,
    val climbMps: Double?,
    val positionSource: Int?,
    val category: Int?,
    val lastContactEpochSec: Long?,
    val receivedMonoMs: Long
)

data class AdsbTrafficUiModel(
    val id: Icao24,
    val callsign: String?,
    val lat: Double,
    val lon: Double,
    val altitudeM: Double?,
    val speedMps: Double?,
    val trackDeg: Double?,
    val climbMps: Double?,
    val ageSec: Int,
    val isStale: Boolean,
    val distanceMeters: Double,
    val bearingDegFromUser: Double,
    val usesOwnshipReference: Boolean = true,
    val positionSource: Int?,
    val category: Int?,
    val lastContactEpochSec: Long?,
    val proximityTier: AdsbProximityTier = AdsbProximityTier.NEUTRAL,
    val proximityReason: AdsbProximityReason = AdsbProximityReason.DIVERGING_OR_STEADY,
    val isClosing: Boolean = false,
    val closingRateMps: Double? = null,
    val isEmergencyCollisionRisk: Boolean = false,
    val isEmergencyAudioEligible: Boolean = false,
    val emergencyAudioIneligibilityReason: AdsbEmergencyAudioIneligibilityReason? = null,
    val isCirclingEmergencyRedRule: Boolean = false,
    val metadataTypecode: String? = null,
    val metadataIcaoAircraftType: String? = null
)

enum class AdsbProximityReason(val code: String) {
    NO_OWNSHIP_REFERENCE("no_ownship_reference"),
    CIRCLING_RULE_APPLIED("circling_rule_applied"),
    GEOMETRY_EMERGENCY_APPLIED("geometry_emergency_applied"),
    APPROACH_CLOSING("approach_closing"),
    RECOVERY_DWELL("recovery_dwell"),
    DIVERGING_OR_STEADY("diverging_or_steady");

    companion object {
        fun fromCode(code: String?): AdsbProximityReason =
            values().firstOrNull { reason -> reason.code == code } ?: DIVERGING_OR_STEADY
    }
}

enum class AdsbEmergencyAudioIneligibilityReason(val code: String) {
    NO_OWNSHIP_REFERENCE("no_ownship_reference"),
    NOT_CLOSING("not_closing"),
    TREND_STALE_WAITING_FOR_FRESH_SAMPLE("trend_stale_waiting_for_fresh_sample"),
    STALE_TARGET_SAMPLE("stale_target_sample"),
    DISTANCE_OUTSIDE_EMERGENCY_RANGE("distance_outside_emergency_range"),
    RELATIVE_ALTITUDE_UNAVAILABLE("relative_altitude_unavailable"),
    OUTSIDE_VERTICAL_GATE("outside_vertical_gate"),
    TARGET_TRACK_UNAVAILABLE("target_track_unavailable"),
    HEADING_GATE_FAILED("heading_gate_failed"),
    MOTION_CONFIDENCE_LOW("motion_confidence_low"),
    PROJECTED_CONFLICT_NOT_LIKELY("projected_conflict_not_likely"),
    LOW_MOTION_SPEED("low_motion_speed"),
    VERTICAL_NON_THREAT("vertical_non_threat");

    companion object {
        fun fromCode(code: String?): AdsbEmergencyAudioIneligibilityReason? =
            values().firstOrNull { reason -> reason.code == code }
    }
}

enum class AdsbProximityTier(val code: Int) {
    NEUTRAL(0),
    GREEN(1),
    AMBER(2),
    RED(3),
    EMERGENCY(4);

    companion object {
        fun fromCode(code: Int?): AdsbProximityTier =
            values().firstOrNull { tier -> tier.code == code } ?: NEUTRAL
    }
}

sealed interface AdsbConnectionState {
    data object Disabled : AdsbConnectionState
    data object Active : AdsbConnectionState
    data class BackingOff(val retryAfterSec: Int) : AdsbConnectionState
    data class Error(val message: String) : AdsbConnectionState
}

enum class AdsbAuthMode {
    Anonymous,
    Authenticated,
    AuthFailed
}

data class AdsbTrafficSnapshot(
    val targets: List<AdsbTrafficUiModel>,
    val connectionState: AdsbConnectionState,
    val authMode: AdsbAuthMode = AdsbAuthMode.Anonymous,
    val centerLat: Double?,
    val centerLon: Double?,
    val usesOwnshipReference: Boolean = false,
    val receiveRadiusKm: Int,
    val fetchedCount: Int,
    val withinRadiusCount: Int,
    val withinVerticalCount: Int = 0,
    val filteredByVerticalCount: Int = 0,
    val cappedCount: Int = 0,
    val displayedCount: Int,
    val lastHttpStatus: Int?,
    val remainingCredits: Int?,
    val lastPollMonoMs: Long?,
    val lastSuccessMonoMs: Long?,
    val lastError: String?,
    val lastNetworkFailureKind: AdsbNetworkFailureKind? = null,
    val consecutiveFailureCount: Int = 0,
    val nextRetryMonoMs: Long? = null,
    val lastFailureMonoMs: Long? = null,
    val networkOnline: Boolean = true,
    val networkOfflineTransitionCount: Int = 0,
    val networkOnlineTransitionCount: Int = 0,
    val lastNetworkTransitionMonoMs: Long? = null,
    val currentOfflineDwellMs: Long = 0L,
    val emergencyAudioState: AdsbEmergencyAudioAlertState = AdsbEmergencyAudioAlertState.DISABLED,
    val emergencyAudioEnabledBySetting: Boolean = false,
    val emergencyAudioFeatureGateOn: Boolean = false,
    val emergencyAudioMasterRolloutEnabled: Boolean = false,
    val emergencyAudioMasterRolloutConfigured: Boolean = false,
    val emergencyAudioShadowModeEnabled: Boolean = false,
    val emergencyAudioRolloutCohortPercent: Int = ADSB_EMERGENCY_AUDIO_COHORT_PERCENT_DEFAULT,
    val emergencyAudioRolloutCohortBucket: Int = ADSB_EMERGENCY_AUDIO_COHORT_BUCKET_MIN,
    val emergencyAudioRolloutCohortEligible: Boolean = false,
    val emergencyAudioRollbackLatched: Boolean = false,
    val emergencyAudioRollbackReason: String? = null,
    val emergencyAudioCooldownMs: Long = ADSB_EMERGENCY_AUDIO_DEFAULT_COOLDOWN_MS,
    val emergencyAudioAlertTriggerCount: Int = 0,
    val emergencyAudioCooldownBlockEpisodeCount: Int = 0,
    val emergencyAudioTransitionEventCount: Int = 0,
    val emergencyAudioLastAlertMonoMs: Long? = null,
    val emergencyAudioCooldownRemainingMs: Long = 0L,
    val emergencyAudioActiveTargetId: String? = null,
    val proximityReasonCounts: AdsbProximityReasonCounts = AdsbProximityReasonCounts(),
    val emergencyAudioKpis: AdsbEmergencyAudioKpiSnapshot = AdsbEmergencyAudioKpiSnapshot()
)

data class AdsbProximityReasonCounts(
    val noOwnshipReferenceCount: Int = 0,
    val circlingRuleAppliedCount: Int = 0,
    val geometryEmergencyAppliedCount: Int = 0,
    val approachClosingCount: Int = 0,
    val recoveryDwellCount: Int = 0,
    val divergingOrSteadyCount: Int = 0
) {
    companion object {
        fun fromTargets(targets: List<AdsbTrafficUiModel>): AdsbProximityReasonCounts {
            var noOwnshipReferenceCount = 0
            var circlingRuleAppliedCount = 0
            var geometryEmergencyAppliedCount = 0
            var approachClosingCount = 0
            var recoveryDwellCount = 0
            var divergingOrSteadyCount = 0
            targets.forEach { target ->
                when (target.proximityReason) {
                    AdsbProximityReason.NO_OWNSHIP_REFERENCE -> noOwnshipReferenceCount += 1
                    AdsbProximityReason.CIRCLING_RULE_APPLIED -> circlingRuleAppliedCount += 1
                    AdsbProximityReason.GEOMETRY_EMERGENCY_APPLIED ->
                        geometryEmergencyAppliedCount += 1
                    AdsbProximityReason.APPROACH_CLOSING -> approachClosingCount += 1
                    AdsbProximityReason.RECOVERY_DWELL -> recoveryDwellCount += 1
                    AdsbProximityReason.DIVERGING_OR_STEADY -> divergingOrSteadyCount += 1
                }
            }
            return AdsbProximityReasonCounts(
                noOwnshipReferenceCount = noOwnshipReferenceCount,
                circlingRuleAppliedCount = circlingRuleAppliedCount,
                geometryEmergencyAppliedCount = geometryEmergencyAppliedCount,
                approachClosingCount = approachClosingCount,
                recoveryDwellCount = recoveryDwellCount,
                divergingOrSteadyCount = divergingOrSteadyCount
            )
        }
    }
}

data class AdsbEmergencyAudioKpiSnapshot(
    val alertTriggerCount: Int = 0,
    val cooldownBlockEpisodeCount: Int = 0,
    val activeObservationMs: Long = 0L,
    val alertsPerFlightHour: Double = 0.0,
    val cooldownBlockEpisodesPerFlightHour: Double = 0.0,
    val disableWithin5MinCount: Int = 0,
    val disableEventCount: Int = 0,
    val disableWithin5MinRate: Double = 0.0,
    val retriggerWithinCooldownCount: Int = 0,
    val determinismMismatchCount: Int = 0
)

data class AdsbAuth(
    val bearerToken: String?
)
