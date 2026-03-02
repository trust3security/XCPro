package com.example.xcpro.adsb

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

internal data class AdsbStoreSelection(
    val withinRadiusCount: Int,
    val withinVerticalCount: Int,
    val filteredByVerticalCount: Int,
    val cappedCount: Int,
    val displayed: List<AdsbTrafficUiModel>
)

internal class AdsbTrafficStore(
    private val proximityTrendEvaluator: AdsbProximityTrendEvaluator = AdsbProximityTrendEvaluator()
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
        queryCenterLat: Double,
        queryCenterLon: Double,
        referenceLat: Double,
        referenceLon: Double,
        ownshipAltitudeMeters: Double?,
        usesOwnshipReference: Boolean,
        radiusMeters: Double,
        verticalAboveMeters: Double,
        verticalBelowMeters: Double,
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

                val ageSec = ((nowMonoMs - target.receivedMonoMs) / 1_000L).toInt().coerceAtLeast(0)
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
                    nowMonoMs = nowMonoMs,
                    hasOwnshipReference = usesOwnshipReference
                )
                val isEmergencyCollisionRisk = isEmergencyCollisionRisk(
                    distanceMeters = distanceMeters,
                    trackDeg = target.trackDeg,
                    bearingDegFromUser = bearingDegFromUser,
                    enabled = usesOwnshipReference &&
                        trendAssessment.isClosing &&
                        ageSec <= EMERGENCY_MAX_AGE_SEC
                )
                val proximityTier = proximityTier(
                    distanceMeters = distanceMeters,
                    hasOwnshipReference = usesOwnshipReference,
                    showClosingAlert = trendAssessment.showClosingAlert,
                    isEmergencyCollisionRisk = isEmergencyCollisionRisk
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
                        isClosing = trendAssessment.isClosing,
                        closingRateMps = trendAssessment.closingRateMps,
                        isEmergencyCollisionRisk = isEmergencyCollisionRisk
                    )
                )
            }
        }

        val displayed = withinVertical
            .sortedWith(
                compareByDescending<AdsbTrafficUiModel> { it.isEmergencyCollisionRisk }
                    .thenBy { it.distanceMeters }
                    .thenBy { it.ageSec }
                    .thenBy { it.id.raw }
            )
            .take(maxDisplayed)

        val cappedCount = (withinVertical.size - displayed.size).coerceAtLeast(0)
        return AdsbStoreSelection(
            withinRadiusCount = withinHorizontalCount,
            withinVerticalCount = withinVertical.size,
            filteredByVerticalCount = filteredByVerticalCount,
            cappedCount = cappedCount,
            displayed = displayed
        )
    }

    private fun isEmergencyCollisionRisk(
        distanceMeters: Double,
        trackDeg: Double?,
        bearingDegFromUser: Double,
        enabled: Boolean
    ): Boolean {
        if (!enabled) return false
        if (!distanceMeters.isFinite() || distanceMeters > EMERGENCY_DISTANCE_METERS) return false
        val track = trackDeg ?: return false
        if (!track.isFinite()) return false
        val bearingFromTargetToUser = normalizeDegrees(bearingDegFromUser + 180.0)
        val headingError = minHeadingDiffDeg(track, bearingFromTargetToUser)
        return headingError <= COLLISION_HEADING_TOLERANCE_DEG
    }

    private fun proximityTier(
        distanceMeters: Double,
        hasOwnshipReference: Boolean,
        showClosingAlert: Boolean,
        isEmergencyCollisionRisk: Boolean
    ): AdsbProximityTier {
        if (!hasOwnshipReference) return AdsbProximityTier.NEUTRAL
        if (isEmergencyCollisionRisk) return AdsbProximityTier.EMERGENCY
        if (!showClosingAlert) return AdsbProximityTier.GREEN
        if (!distanceMeters.isFinite()) return AdsbProximityTier.GREEN
        return when {
            distanceMeters <= RED_DISTANCE_METERS -> AdsbProximityTier.RED
            distanceMeters <= AMBER_DISTANCE_METERS -> AdsbProximityTier.AMBER
            else -> AdsbProximityTier.GREEN
        }
    }

    private fun normalizeDegrees(value: Double): Double {
        val normalized = value % 360.0
        return if (normalized < 0.0) normalized + 360.0 else normalized
    }

    private fun minHeadingDiffDeg(a: Double, b: Double): Double {
        val diff = abs(normalizeDegrees(a) - normalizeDegrees(b))
        return if (diff > 180.0) 360.0 - diff else diff
    }

    private companion object {
        private const val EMERGENCY_DISTANCE_METERS = 1_000.0
        private const val COLLISION_HEADING_TOLERANCE_DEG = 20.0
        private const val EMERGENCY_MAX_AGE_SEC = 20
        private const val RED_DISTANCE_METERS = 2_000.0
        private const val AMBER_DISTANCE_METERS = 5_000.0
    }
}
