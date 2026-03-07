package com.example.xcpro.adsb

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

internal data class AdsbStoreSelection(
    val withinRadiusCount: Int,
    val withinVerticalCount: Int,
    val filteredByVerticalCount: Int,
    val cappedCount: Int,
    val displayed: List<AdsbTrafficUiModel>,
    val emergencyAudioCandidateId: Icao24?
)

internal class AdsbTrafficStore(
    private val proximityTrendEvaluator: AdsbProximityTrendEvaluator = AdsbProximityTrendEvaluator(),
    private val collisionRiskEvaluator: AdsbCollisionRiskEvaluator = AdsbCollisionRiskEvaluator(),
    private val emergencyRiskStabilizer: AdsbEmergencyRiskStabilizer = AdsbEmergencyRiskStabilizer(),
    private val targetTrackEstimator: AdsbTargetTrackEstimator = AdsbTargetTrackEstimator()
) {
    private val targetsById = ConcurrentHashMap<Icao24, AdsbTarget>()
    private val distanceTierStateByTargetId = ConcurrentHashMap<Icao24, AdsbProximityTier>()
    private val resolvedTierStateByTargetId = ConcurrentHashMap<Icao24, AdsbProximityTier>()

    fun clear() {
        targetsById.clear()
        proximityTrendEvaluator.clear()
        emergencyRiskStabilizer.clear()
        targetTrackEstimator.clear()
        distanceTierStateByTargetId.clear()
        resolvedTierStateByTargetId.clear()
    }

    fun upsertAll(targets: List<AdsbTarget>) {
        for (target in targets) {
            targetsById[target.id] = target
        }
    }

    fun purgeExpired(nowMonoMs: Long, expiryAfterSec: Int): Boolean {
        var removed = false
        val iterator = targetsById.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val ageSec = ((nowMonoMs - entry.value.receivedMonoMs) / 1_000L).toInt()
            if (ageSec >= expiryAfterSec) {
                iterator.remove()
                proximityTrendEvaluator.removeTarget(entry.key)
                emergencyRiskStabilizer.removeTarget(entry.key)
                targetTrackEstimator.removeTarget(entry.key)
                distanceTierStateByTargetId.remove(entry.key)
                resolvedTierStateByTargetId.remove(entry.key)
                removed = true
            }
        }
        return removed
    }

    fun select(
        nowMonoMs: Long,
        nowWallEpochSec: Long? = null,
        queryCenterLat: Double,
        queryCenterLon: Double,
        referenceLat: Double,
        referenceLon: Double,
        ownshipAltitudeMeters: Double?,
        referenceSampleMonoMs: Long? = null,
        ownshipTrackDeg: Double? = null,
        ownshipSpeedMps: Double? = null,
        usesOwnshipReference: Boolean,
        radiusMeters: Double,
        verticalAboveMeters: Double,
        verticalBelowMeters: Double,
        ownshipIsCircling: Boolean = false,
        circlingFeatureEnabled: Boolean = false,
        maxDisplayed: Int,
        staleAfterSec: Int
    ): AdsbStoreSelection {
        var withinHorizontalCount = 0
        var filteredByVerticalCount = 0
        val withinVertical = buildList {
            for (target in targetsById.values) {
                val distanceFromQueryCenterMeters = AdsbGeoMath.haversineMeters(
                    lat1 = queryCenterLat,
                    lon1 = queryCenterLon,
                    lat2 = target.lat,
                    lon2 = target.lon
                )
                if (distanceFromQueryCenterMeters > radiusMeters) continue
                withinHorizontalCount += 1

                val altitudeDeltaMeters = ownshipAltitudeMeters?.let { ownshipAlt ->
                    val targetAltitude = target.altitudeM?.takeIf { it.isFinite() } ?: return@let null
                    targetAltitude - ownshipAlt
                }
                if (altitudeDeltaMeters != null) {
                    val above = altitudeDeltaMeters
                    val below = -altitudeDeltaMeters
                    if (above > verticalAboveMeters || below > verticalBelowMeters) {
                        filteredByVerticalCount += 1
                        continue
                    }
                }

                val receivedAgeSec =
                    ((nowMonoMs - target.receivedMonoMs) / 1_000L).toInt().coerceAtLeast(0)
                val contactAgeSec = contactAgeSec(
                    nowWallEpochSec = nowWallEpochSec,
                    lastContactEpochSec = target.lastContactEpochSec
                )
                val ageSec = maxOf(receivedAgeSec, contactAgeSec ?: 0)
                val distanceMeters = AdsbGeoMath.haversineMeters(
                    lat1 = referenceLat,
                    lon1 = referenceLon,
                    lat2 = target.lat,
                    lon2 = target.lon
                )
                val bearingDegFromUser = AdsbGeoMath.bearingDegrees(
                    fromLat = referenceLat,
                    fromLon = referenceLon,
                    toLat = target.lat,
                    toLon = target.lon
                )
                val trendSampleMonoMs = trendSampleMonoMs(
                    targetReceivedMonoMs = target.receivedMonoMs,
                    ownshipReferenceSampleMonoMs = referenceSampleMonoMs,
                    usesOwnshipReference = usesOwnshipReference
                )
                val trendAssessment = proximityTrendEvaluator.evaluate(
                    id = target.id,
                    distanceMeters = distanceMeters,
                    sampleMonoMs = trendSampleMonoMs,
                    nowMonoMs = nowMonoMs,
                    hasOwnshipReference = usesOwnshipReference
                )
                val resolvedTargetTrackDeg = targetTrackEstimator.resolveTrackDeg(
                    id = target.id,
                    lat = target.lat,
                    lon = target.lon,
                    sampleMonoMs = trendSampleMonoMs,
                    reportedTrackDeg = target.trackDeg
                )
                val collisionRiskAssessment = collisionRiskEvaluator.evaluate(
                    distanceMeters = distanceMeters,
                    trackDeg = resolvedTargetTrackDeg,
                    targetSpeedMps = target.speedMps,
                    bearingDegFromUser = bearingDegFromUser,
                    ownshipTrackDeg = ownshipTrackDeg,
                    ownshipSpeedMps = ownshipSpeedMps,
                    altitudeDeltaMeters = altitudeDeltaMeters,
                    verticalAboveMeters = verticalAboveMeters,
                    verticalBelowMeters = verticalBelowMeters,
                    hasOwnshipReference = usesOwnshipReference,
                    isClosing = trendAssessment.isClosing,
                    ageSec = ageSec
                )
                val circlingEmergencyContextEnabled =
                    usesOwnshipReference && ownshipIsCircling && circlingFeatureEnabled
                val circlingEmergencyVerticalAboveMeters = minOf(
                    verticalAboveMeters,
                    CIRCLING_EMERGENCY_VERTICAL_CAP_METERS
                )
                val circlingEmergencyVerticalBelowMeters = minOf(
                    verticalBelowMeters,
                    CIRCLING_EMERGENCY_VERTICAL_CAP_METERS
                )
                val isCirclingEmergencyRedRule = isCirclingEmergencyRedRule(
                    distanceMeters = distanceMeters,
                    altitudeDeltaMeters = altitudeDeltaMeters,
                    enabled = circlingEmergencyContextEnabled &&
                        trendAssessment.isClosing &&
                        ageSec <= EMERGENCY_MAX_AGE_SEC,
                    verticalAboveMeters = circlingEmergencyVerticalAboveMeters,
                    verticalBelowMeters = circlingEmergencyVerticalBelowMeters
                )
                val isEmergencyCollisionRisk = collisionRiskAssessment.isEmergencyCollisionRisk &&
                    !isCirclingEmergencyRedRule
                val isVerticalNonThreat = isVerticalNonThreat(
                    altitudeDeltaMeters = altitudeDeltaMeters,
                    hasOwnshipReference = usesOwnshipReference
                )
                val isEmergencyCollisionRiskCapped = emergencyRiskStabilizer.stabilize(
                    id = target.id,
                    candidateEmergencyRisk = isEmergencyCollisionRisk && !isVerticalNonThreat,
                    hasFreshTrendSample = trendAssessment.hasFreshTrendSample,
                    forceClear = !usesOwnshipReference ||
                        isVerticalNonThreat ||
                        ageSec > EMERGENCY_MAX_AGE_SEC
                )
                val isEmergencyAudioEligible =
                    isEmergencyCollisionRiskCapped || isCirclingEmergencyRedRule
                val emergencyAudioIneligibilityReason = emergencyAudioIneligibilityReason(
                    isEmergencyAudioEligible = isEmergencyAudioEligible,
                    hasOwnshipReference = usesOwnshipReference,
                    isVerticalNonThreat = isVerticalNonThreat,
                    trendAssessment = trendAssessment,
                    collisionRiskReason = collisionRiskAssessment.ineligibilityReason
                )
                val proximityTier = proximityTier(
                    targetId = target.id,
                    distanceTier = distanceTierWithHysteresis(
                        targetId = target.id,
                        distanceMeters = distanceMeters,
                        hasOwnshipReference = usesOwnshipReference
                    ),
                    hasOwnshipReference = usesOwnshipReference,
                    isVerticalNonThreat = isVerticalNonThreat,
                    hasFreshTrendSample = trendAssessment.hasFreshTrendSample,
                    showClosingAlert = trendAssessment.showClosingAlert,
                    postPassDivergingSampleCount = trendAssessment.postPassDivergingSampleCount,
                    isCirclingEmergencyRedRule = isCirclingEmergencyRedRule,
                    isEmergencyCollisionRisk = isEmergencyCollisionRiskCapped
                )
                val proximityReason = proximityReason(
                    hasOwnshipReference = usesOwnshipReference,
                    isVerticalNonThreat = isVerticalNonThreat,
                    isCirclingEmergencyRedRule = isCirclingEmergencyRedRule,
                    isEmergencyCollisionRisk = isEmergencyCollisionRiskCapped,
                    hasTrendSample = trendAssessment.hasTrendSample,
                    isClosing = trendAssessment.isClosing,
                    showClosingAlert = trendAssessment.showClosingAlert
                )
                add(
                    AdsbTrafficUiModel(
                        id = target.id,
                        callsign = target.callsign,
                        lat = target.lat,
                        lon = target.lon,
                        altitudeM = target.altitudeM,
                        speedMps = target.speedMps,
                        trackDeg = resolvedTargetTrackDeg,
                        climbMps = target.climbMps,
                        ageSec = ageSec,
                        isStale = ageSec >= staleAfterSec,
                        distanceMeters = distanceMeters,
                        bearingDegFromUser = bearingDegFromUser,
                        usesOwnshipReference = usesOwnshipReference,
                        positionSource = target.positionSource,
                        category = target.category,
                        lastContactEpochSec = target.lastContactEpochSec,
                        proximityTier = proximityTier,
                        proximityReason = proximityReason,
                        isClosing = trendAssessment.isClosing && !isVerticalNonThreat,
                        closingRateMps = trendAssessment.closingRateMps,
                        isEmergencyCollisionRisk = isEmergencyCollisionRiskCapped,
                        isEmergencyAudioEligible = isEmergencyAudioEligible,
                        emergencyAudioIneligibilityReason = emergencyAudioIneligibilityReason,
                        isCirclingEmergencyRedRule = isCirclingEmergencyRedRule
                    )
                )
            }
        }

        val emergencyAudioCandidateId = selectEmergencyAudioCandidate(withinVertical)?.id
        val displayed = withinVertical
            .sortedWith(DISPLAY_PRIORITY_COMPARATOR)
            .take(maxDisplayed)
        val cappedCount = (withinVertical.size - displayed.size).coerceAtLeast(0)
        return AdsbStoreSelection(
            withinRadiusCount = withinHorizontalCount,
            withinVerticalCount = withinVertical.size,
            filteredByVerticalCount = filteredByVerticalCount,
            cappedCount = cappedCount,
            displayed = displayed,
            emergencyAudioCandidateId = emergencyAudioCandidateId
        )
    }

    private fun selectEmergencyAudioCandidate(
        targets: List<AdsbTrafficUiModel>
    ): AdsbTrafficUiModel? {
        var best: AdsbTrafficUiModel? = null
        for (target in targets) {
            if (!target.isEmergencyAudioEligible) continue
            val currentBest = best
            if (currentBest == null || compareEmergencyCandidate(target, currentBest) < 0) {
                best = target
            }
        }
        return best
    }

    private fun compareEmergencyCandidate(
        candidate: AdsbTrafficUiModel,
        incumbent: AdsbTrafficUiModel
    ): Int {
        if (candidate.isEmergencyCollisionRisk != incumbent.isEmergencyCollisionRisk) {
            return if (candidate.isEmergencyCollisionRisk) -1 else 1
        }
        if (candidate.isCirclingEmergencyRedRule != incumbent.isCirclingEmergencyRedRule) {
            return if (candidate.isCirclingEmergencyRedRule) -1 else 1
        }
        val distanceCompare = candidate.distanceMeters.compareTo(incumbent.distanceMeters)
        if (distanceCompare != 0) return distanceCompare
        val ageCompare = candidate.ageSec.compareTo(incumbent.ageSec)
        if (ageCompare != 0) return ageCompare
        return candidate.id.raw.compareTo(incumbent.id.raw)
    }

    private fun proximityTier(
        targetId: Icao24,
        distanceTier: AdsbProximityTier,
        hasOwnshipReference: Boolean,
        isVerticalNonThreat: Boolean,
        hasFreshTrendSample: Boolean,
        showClosingAlert: Boolean,
        postPassDivergingSampleCount: Int,
        isCirclingEmergencyRedRule: Boolean,
        isEmergencyCollisionRisk: Boolean
    ): AdsbProximityTier {
        if (!hasOwnshipReference) {
            resolvedTierStateByTargetId.remove(targetId)
            return AdsbProximityTier.NEUTRAL
        }
        if (isVerticalNonThreat) {
            resolvedTierStateByTargetId[targetId] = AdsbProximityTier.GREEN
            return AdsbProximityTier.GREEN
        }
        if (isCirclingEmergencyRedRule) {
            resolvedTierStateByTargetId[targetId] = AdsbProximityTier.RED
            return AdsbProximityTier.RED
        }
        if (isEmergencyCollisionRisk) return AdsbProximityTier.EMERGENCY
        val candidateTier = when {
            showClosingAlert -> distanceTier
            !hasFreshTrendSample -> staleTrendTierCandidate(
                targetId = targetId,
                distanceTier = distanceTier
            )
            else -> freshTrendTierCandidate(
                distanceTier = distanceTier,
                postPassDivergingSampleCount = postPassDivergingSampleCount
            )
        }
        resolvedTierStateByTargetId[targetId] = candidateTier
        return candidateTier
    }
    private fun staleTrendTierCandidate(
        targetId: Icao24,
        distanceTier: AdsbProximityTier
    ): AdsbProximityTier {
        val previousTier = resolvedTierStateByTargetId[targetId] ?: return distanceTier
        return when {
            distanceTier == AdsbProximityTier.RED && previousTier != AdsbProximityTier.RED ->
                AdsbProximityTier.RED
            else -> previousTier
        }
    }
    private fun freshTrendTierCandidate(
        distanceTier: AdsbProximityTier,
        postPassDivergingSampleCount: Int
    ): AdsbProximityTier = when (distanceTier) {
            // RED traffic de-escalates in two fresh post-pass samples: RED -> AMBER -> GREEN.
            AdsbProximityTier.RED -> when {
                postPassDivergingSampleCount >= POST_PASS_RED_TO_GREEN_MIN_SAMPLES ->
                    AdsbProximityTier.GREEN
                postPassDivergingSampleCount >= POST_PASS_RED_TO_AMBER_MIN_SAMPLES ->
                    AdsbProximityTier.AMBER
                else -> AdsbProximityTier.RED
            }
            // AMBER de-escalation to GREEN requires evidence of a prior closing episode.
            AdsbProximityTier.AMBER -> when {
                postPassDivergingSampleCount >= POST_PASS_AMBER_TO_GREEN_MIN_SAMPLES ->
                    AdsbProximityTier.GREEN
                else -> AdsbProximityTier.AMBER
            }
            AdsbProximityTier.GREEN -> AdsbProximityTier.GREEN
            else -> AdsbProximityTier.GREEN
    }

    private fun distanceTierWithHysteresis(
        targetId: Icao24,
        distanceMeters: Double,
        hasOwnshipReference: Boolean
    ): AdsbProximityTier {
        if (!hasOwnshipReference || !distanceMeters.isFinite()) {
            distanceTierStateByTargetId.remove(targetId)
            return AdsbProximityTier.GREEN
        }
        val previous = distanceTierStateByTargetId[targetId]
        val next = when (previous) {
            AdsbProximityTier.RED -> when {
                distanceMeters <= RED_EXIT_DISTANCE_METERS -> AdsbProximityTier.RED
                distanceMeters <= AMBER_EXIT_DISTANCE_METERS -> AdsbProximityTier.AMBER
                else -> AdsbProximityTier.GREEN
            }
            AdsbProximityTier.AMBER -> when {
                distanceMeters <= RED_ENTER_DISTANCE_METERS -> AdsbProximityTier.RED
                distanceMeters > AMBER_EXIT_DISTANCE_METERS -> AdsbProximityTier.GREEN
                else -> AdsbProximityTier.AMBER
            }
            else -> when {
                distanceMeters <= RED_ENTER_DISTANCE_METERS -> AdsbProximityTier.RED
                distanceMeters <= AMBER_ENTER_DISTANCE_METERS -> AdsbProximityTier.AMBER
                else -> AdsbProximityTier.GREEN
            }
        }
        distanceTierStateByTargetId[targetId] = next
        return next
    }

    private fun proximityReason(
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

    private fun emergencyAudioIneligibilityReason(
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

    private fun isCirclingEmergencyRedRule(
        distanceMeters: Double,
        altitudeDeltaMeters: Double?,
        enabled: Boolean,
        verticalAboveMeters: Double,
        verticalBelowMeters: Double
    ): Boolean {
        if (!enabled) return false
        if (!distanceMeters.isFinite() || distanceMeters > CIRCLING_RED_DISTANCE_METERS) return false
        val altitudeDelta = altitudeDeltaMeters ?: return false
        val above = altitudeDelta
        val below = -altitudeDelta
        return above <= verticalAboveMeters && below <= verticalBelowMeters
    }

    private fun isVerticalNonThreat(
        altitudeDeltaMeters: Double?,
        hasOwnshipReference: Boolean
    ): Boolean {
        if (!hasOwnshipReference) return false
        val altitudeDelta = altitudeDeltaMeters ?: return false
        if (!altitudeDelta.isFinite()) return false
        return abs(altitudeDelta) >= VERTICAL_NON_THREAT_DELTA_METERS
    }

    private fun contactAgeSec(nowWallEpochSec: Long?, lastContactEpochSec: Long?): Int? {
        if (nowWallEpochSec == null || lastContactEpochSec == null) return null
        if (nowWallEpochSec < lastContactEpochSec) return null
        return (nowWallEpochSec - lastContactEpochSec).toInt().coerceAtLeast(0)
    }

    private fun trendSampleMonoMs(
        targetReceivedMonoMs: Long,
        ownshipReferenceSampleMonoMs: Long?,
        usesOwnshipReference: Boolean
    ): Long {
        if (!usesOwnshipReference) return targetReceivedMonoMs
        val ownshipSample = ownshipReferenceSampleMonoMs ?: return targetReceivedMonoMs
        return maxOf(targetReceivedMonoMs, ownshipSample)
    }

    private companion object {
        private const val EMERGENCY_MAX_AGE_SEC = 20
        private const val CIRCLING_RED_DISTANCE_METERS = 1_000.0
        private const val CIRCLING_EMERGENCY_VERTICAL_CAP_METERS = 304.8
        private const val RED_ENTER_DISTANCE_METERS = 2_000.0
        private const val RED_EXIT_DISTANCE_METERS = 2_200.0
        private const val AMBER_ENTER_DISTANCE_METERS = 5_000.0
        private const val AMBER_EXIT_DISTANCE_METERS = 5_300.0
        private const val VERTICAL_NON_THREAT_DELTA_METERS = 1_200.0
        private const val POST_PASS_RED_TO_AMBER_MIN_SAMPLES = 1
        private const val POST_PASS_AMBER_TO_GREEN_MIN_SAMPLES = 1
        private const val POST_PASS_RED_TO_GREEN_MIN_SAMPLES = 2
        private val DISPLAY_PRIORITY_COMPARATOR =
            compareByDescending<AdsbTrafficUiModel> { it.isEmergencyCollisionRisk }
                .thenBy { it.distanceMeters }
                .thenBy { it.ageSec }
                .thenBy { it.id.raw }
    }
}
