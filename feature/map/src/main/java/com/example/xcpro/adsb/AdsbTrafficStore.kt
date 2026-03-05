package com.example.xcpro.adsb

import java.util.concurrent.ConcurrentHashMap

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
    private val collisionRiskEvaluator: AdsbCollisionRiskEvaluator = AdsbCollisionRiskEvaluator()
) {
    private val targetsById = ConcurrentHashMap<Icao24, AdsbTarget>()

    fun clear() {
        targetsById.clear()
        proximityTrendEvaluator.clear()
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
                val trendAssessment = proximityTrendEvaluator.evaluate(
                    id = target.id,
                    distanceMeters = distanceMeters,
                    sampleMonoMs = target.receivedMonoMs,
                    nowMonoMs = nowMonoMs,
                    hasOwnshipReference = usesOwnshipReference
                )
                val geometryEmergencyCollisionRisk = collisionRiskEvaluator.evaluate(
                    distanceMeters = distanceMeters,
                    trackDeg = target.trackDeg,
                    bearingDegFromUser = bearingDegFromUser,
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
                val isEmergencyCollisionRisk = geometryEmergencyCollisionRisk &&
                    !isCirclingEmergencyRedRule
                val isEmergencyAudioEligible = isEmergencyCollisionRisk || isCirclingEmergencyRedRule
                val proximityTier = proximityTier(
                    distanceMeters = distanceMeters,
                    hasOwnshipReference = usesOwnshipReference,
                    hasFreshTrendSample = trendAssessment.hasFreshTrendSample,
                    showClosingAlert = trendAssessment.showClosingAlert,
                    isCirclingEmergencyRedRule = isCirclingEmergencyRedRule,
                    isEmergencyCollisionRisk = isEmergencyCollisionRisk
                )
                val proximityReason = proximityReason(
                    hasOwnshipReference = usesOwnshipReference,
                    isCirclingEmergencyRedRule = isCirclingEmergencyRedRule,
                    isEmergencyCollisionRisk = isEmergencyCollisionRisk,
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
                        trackDeg = target.trackDeg,
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
                        isClosing = trendAssessment.isClosing,
                        closingRateMps = trendAssessment.closingRateMps,
                        isEmergencyCollisionRisk = isEmergencyCollisionRisk,
                        isEmergencyAudioEligible = isEmergencyAudioEligible,
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
        distanceMeters: Double,
        hasOwnshipReference: Boolean,
        hasFreshTrendSample: Boolean,
        showClosingAlert: Boolean,
        isCirclingEmergencyRedRule: Boolean,
        isEmergencyCollisionRisk: Boolean
    ): AdsbProximityTier {
        if (!hasOwnshipReference) return AdsbProximityTier.NEUTRAL
        if (isCirclingEmergencyRedRule) return AdsbProximityTier.RED
        if (isEmergencyCollisionRisk) return AdsbProximityTier.EMERGENCY
        if (!distanceMeters.isFinite()) return AdsbProximityTier.GREEN
        val distanceTier = when {
            distanceMeters <= RED_DISTANCE_METERS -> AdsbProximityTier.RED
            distanceMeters <= AMBER_DISTANCE_METERS -> AdsbProximityTier.AMBER
            else -> AdsbProximityTier.GREEN
        }
        if (showClosingAlert) return distanceTier
        if (!hasFreshTrendSample) return distanceTier
        return when (distanceTier) {
            // Avoid close-range red/amber -> green oscillation on short trend recoveries.
            AdsbProximityTier.RED -> AdsbProximityTier.AMBER
            AdsbProximityTier.AMBER -> AdsbProximityTier.GREEN
            AdsbProximityTier.GREEN -> AdsbProximityTier.GREEN
            else -> AdsbProximityTier.GREEN
        }
    }

    private fun proximityReason(
        hasOwnshipReference: Boolean,
        isCirclingEmergencyRedRule: Boolean,
        isEmergencyCollisionRisk: Boolean,
        hasTrendSample: Boolean,
        isClosing: Boolean,
        showClosingAlert: Boolean
    ): AdsbProximityReason {
        if (!hasOwnshipReference) return AdsbProximityReason.NO_OWNSHIP_REFERENCE
        if (isCirclingEmergencyRedRule) return AdsbProximityReason.CIRCLING_RULE_APPLIED
        if (isEmergencyCollisionRisk) return AdsbProximityReason.GEOMETRY_EMERGENCY_APPLIED
        if (isClosing) return AdsbProximityReason.APPROACH_CLOSING
        if (hasTrendSample && showClosingAlert) return AdsbProximityReason.RECOVERY_DWELL
        return AdsbProximityReason.DIVERGING_OR_STEADY
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

    private fun contactAgeSec(nowWallEpochSec: Long?, lastContactEpochSec: Long?): Int? {
        if (nowWallEpochSec == null || lastContactEpochSec == null) return null
        if (nowWallEpochSec < lastContactEpochSec) return null
        return (nowWallEpochSec - lastContactEpochSec).toInt().coerceAtLeast(0)
    }

    private companion object {
        private const val EMERGENCY_MAX_AGE_SEC = 20
        private const val CIRCLING_RED_DISTANCE_METERS = 1_000.0
        private const val CIRCLING_EMERGENCY_VERTICAL_CAP_METERS = 304.8
        private const val RED_DISTANCE_METERS = 2_000.0
        private const val AMBER_DISTANCE_METERS = 5_000.0
        private val DISPLAY_PRIORITY_COMPARATOR =
            compareByDescending<AdsbTrafficUiModel> { it.isEmergencyCollisionRisk }
                .thenBy { it.distanceMeters }
                .thenBy { it.ageSec }
                .thenBy { it.id.raw }
    }
}
