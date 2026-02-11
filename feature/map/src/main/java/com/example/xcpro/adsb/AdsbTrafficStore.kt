package com.example.xcpro.adsb

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

internal data class AdsbStoreSelection(
    val withinRadiusCount: Int,
    val displayed: List<AdsbTrafficUiModel>
)

internal class AdsbTrafficStore {
    private val targetsById = ConcurrentHashMap<Icao24, AdsbTarget>()

    fun clear() {
        targetsById.clear()
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
                removed = true
            }
        }
        return removed
    }

    fun select(
        nowMonoMs: Long,
        centerLat: Double,
        centerLon: Double,
        radiusMeters: Double,
        maxDisplayed: Int,
        staleAfterSec: Int
    ): AdsbStoreSelection {
        val withinRadius = buildList {
            for (target in targetsById.values) {
                val distanceMeters = AdsbGeoMath.haversineMeters(
                    lat1 = centerLat,
                    lon1 = centerLon,
                    lat2 = target.lat,
                    lon2 = target.lon
                )
                if (distanceMeters > radiusMeters) continue
                val ageSec = ((nowMonoMs - target.receivedMonoMs) / 1_000L).toInt().coerceAtLeast(0)
                val bearingDegFromUser = AdsbGeoMath.bearingDegrees(
                    fromLat = centerLat,
                    fromLon = centerLon,
                    toLat = target.lat,
                    toLon = target.lon
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
                        positionSource = target.positionSource,
                        category = target.category,
                        lastContactEpochSec = target.lastContactEpochSec,
                        isEmergencyCollisionRisk = isEmergencyCollisionRisk(
                            distanceMeters = distanceMeters,
                            trackDeg = target.trackDeg,
                            bearingDegFromUser = bearingDegFromUser
                        )
                    )
                )
            }
        }

        val displayed = withinRadius
            .sortedWith(compareBy<AdsbTrafficUiModel> { it.ageSec }.thenBy { it.distanceMeters })
            .take(maxDisplayed)

        return AdsbStoreSelection(
            withinRadiusCount = withinRadius.size,
            displayed = displayed
        )
    }

    private fun isEmergencyCollisionRisk(
        distanceMeters: Double,
        trackDeg: Double?,
        bearingDegFromUser: Double
    ): Boolean {
        if (!distanceMeters.isFinite() || distanceMeters > EMERGENCY_DISTANCE_METERS) return false
        val track = trackDeg ?: return false
        if (!track.isFinite()) return false
        val bearingFromTargetToUser = normalizeDegrees(bearingDegFromUser + 180.0)
        val headingError = minHeadingDiffDeg(track, bearingFromTargetToUser)
        return headingError <= COLLISION_HEADING_TOLERANCE_DEG
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
    }
}
