package com.trust3.xcpro.adsb

internal class AdsbTargetTrackEstimator(
    private val minTrackDerivationDtMs: Long = MIN_TRACK_DERIVATION_DT_MS,
    private val minTrackDerivationDistanceMeters: Double = MIN_TRACK_DERIVATION_DISTANCE_METERS
) {

    private val stateByTargetId = HashMap<Icao24, TrackState>()

    fun clear() {
        stateByTargetId.clear()
    }

    fun removeTarget(id: Icao24) {
        stateByTargetId.remove(id)
    }

    fun resolveTrackDeg(
        id: Icao24,
        lat: Double,
        lon: Double,
        sampleMonoMs: Long,
        reportedTrackDeg: Double?
    ): Double? {
        val normalizedReportedTrack = normalizeDegrees(reportedTrackDeg)
        val previousState = stateByTargetId[id]
        val derivedTrackDeg = when {
            previousState == null -> null
            sampleMonoMs - previousState.sampleMonoMs < minTrackDerivationDtMs -> previousState.derivedTrackDeg
            else -> {
                val movedMeters = AdsbGeoMath.haversineMeters(
                    lat1 = previousState.lat,
                    lon1 = previousState.lon,
                    lat2 = lat,
                    lon2 = lon
                )
                if (!movedMeters.isFinite() || movedMeters < minTrackDerivationDistanceMeters) {
                    previousState.derivedTrackDeg
                } else {
                    AdsbGeoMath.bearingDegrees(
                        fromLat = previousState.lat,
                        fromLon = previousState.lon,
                        toLat = lat,
                        toLon = lon
                    )
                }
            }
        }

        stateByTargetId[id] = TrackState(
            lat = lat,
            lon = lon,
            sampleMonoMs = sampleMonoMs,
            derivedTrackDeg = derivedTrackDeg
        )
        return normalizedReportedTrack ?: derivedTrackDeg
    }

    private fun normalizeDegrees(value: Double?): Double? {
        val normalized = value?.takeIf { it.isFinite() } ?: return null
        return ((normalized % 360.0) + 360.0) % 360.0
    }

    private data class TrackState(
        val lat: Double,
        val lon: Double,
        val sampleMonoMs: Long,
        val derivedTrackDeg: Double?
    )

    private companion object {
        const val MIN_TRACK_DERIVATION_DT_MS = 800L
        const val MIN_TRACK_DERIVATION_DISTANCE_METERS = 35.0
    }
}
