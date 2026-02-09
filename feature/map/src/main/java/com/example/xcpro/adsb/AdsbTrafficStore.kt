package com.example.xcpro.adsb

import java.util.concurrent.ConcurrentHashMap

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
                        bearingDegFromUser = AdsbGeoMath.bearingDegrees(
                            fromLat = centerLat,
                            fromLon = centerLon,
                            toLat = target.lat,
                            toLon = target.lon
                        ),
                        positionSource = target.positionSource,
                        category = target.category,
                        lastContactEpochSec = target.lastContactEpochSec
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
}

