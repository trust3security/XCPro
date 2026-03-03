package com.example.xcpro.ogn

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt

internal data class ThermalInput(
    val targets: List<OgnTrafficTarget>,
    val streamingEnabled: Boolean,
    val suppressedTargetKeys: Set<String>,
    val retentionHours: Int,
    val displayPercent: Int
)

internal data class ProcessedSourceState(
    val lastSourceSeenMonoMs: Long,
    val lastProcessedMonoMs: Long
)

internal data class ThermalTracker(
    val sourceTargetId: String,
    var sourceLabel: String,
    val startedAtMonoMs: Long,
    val startedAtWallMs: Long,
    var lastSeenMonoMs: Long,
    var lastStrongClimbMonoMs: Long,
    var startAltitudeMeters: Double?,
    var maxAltitudeMeters: Double?,
    var maxAltitudeAtMonoMs: Long?,
    var sampleCount: Int,
    var climbSumMps: Double,
    var maxClimbRateMps: Double,
    var previousTrackDegrees: Double?,
    var accumulatedTurnDegrees: Double,
    var centroidLatitudeSum: Double,
    var centroidLongitudeSum: Double,
    var centroidSampleCount: Int,
    var confirmed: Boolean,
    var hotspotId: String?
) {
    val centroidLatitude: Double
        get() = centroidLatitudeSum / centroidSampleCount.toDouble()

    val centroidLongitude: Double
        get() = centroidLongitudeSum / centroidSampleCount.toDouble()

    val averageClimbRateMps: Double?
        get() = if (sampleCount > 0) climbSumMps / sampleCount.toDouble() else null

    val averageBottomToTopClimbRateMps: Double?
        get() {
            val startAltitude = startAltitudeMeters ?: return null
            val maxAltitude = maxAltitudeMeters ?: return null
            val maxAltitudeMs = maxAltitudeAtMonoMs ?: return null
            val elapsedSeconds = (maxAltitudeMs - startedAtMonoMs) / 1000.0
            if (!elapsedSeconds.isFinite() || elapsedSeconds <= 0.0) return null
            return (maxAltitude - startAltitude) / elapsedSeconds
        }

    fun addPositionSample(latitude: Double, longitude: Double) {
        centroidLatitudeSum += latitude
        centroidLongitudeSum += longitude
        centroidSampleCount += 1
    }

    fun addTrackSample(trackDegrees: Double?) {
        val normalizedTrackDegrees = normalizeTrackDegreesOrNullInternal(trackDegrees) ?: return
        val previousTrack = previousTrackDegrees
        if (previousTrack != null) {
            accumulatedTurnDegrees += shortestTurnDeltaDegreesInternal(previousTrack, normalizedTrackDegrees)
        }
        previousTrackDegrees = normalizedTrackDegrees
    }

    fun toHotspot(
        stableId: String,
        nowMonoMs: Long,
        nowWallMs: Long,
        state: OgnThermalHotspotState
    ): OgnThermalHotspot {
        return OgnThermalHotspot(
            id = stableId,
            sourceTargetId = sourceTargetId,
            sourceLabel = sourceLabel,
            latitude = centroidLatitude,
            longitude = centroidLongitude,
            startedAtMonoMs = startedAtMonoMs,
            startedAtWallMs = startedAtWallMs,
            updatedAtMonoMs = nowMonoMs,
            updatedAtWallMs = nowWallMs,
            startAltitudeMeters = startAltitudeMeters,
            maxAltitudeMeters = maxAltitudeMeters,
            maxAltitudeAtMonoMs = maxAltitudeAtMonoMs,
            maxClimbRateMps = maxClimbRateMps,
            averageClimbRateMps = averageClimbRateMps,
            averageBottomToTopClimbRateMps = averageBottomToTopClimbRateMps,
            snailColorIndex = climbRateToSnailColorIndex(maxClimbRateMps),
            state = state
        )
    }

    companion object {
        fun create(
            sourceTargetId: String,
            target: OgnTrafficTarget,
            nowMonoMs: Long,
            nowWallMs: Long,
            climbRateMps: Double,
            altitudeMeters: Double?
        ): ThermalTracker {
            return ThermalTracker(
                sourceTargetId = sourceTargetId,
                sourceLabel = target.displayLabel.ifBlank { target.callsign },
                startedAtMonoMs = nowMonoMs,
                startedAtWallMs = nowWallMs,
                lastSeenMonoMs = nowMonoMs,
                lastStrongClimbMonoMs = nowMonoMs,
                startAltitudeMeters = altitudeMeters,
                maxAltitudeMeters = altitudeMeters,
                maxAltitudeAtMonoMs = altitudeMeters?.let { nowMonoMs },
                sampleCount = 1,
                climbSumMps = climbRateMps,
                maxClimbRateMps = climbRateMps,
                previousTrackDegrees = normalizeTrackDegreesOrNullInternal(target.trackDegrees),
                accumulatedTurnDegrees = 0.0,
                centroidLatitudeSum = target.latitude,
                centroidLongitudeSum = target.longitude,
                centroidSampleCount = 1,
                confirmed = false,
                hotspotId = null
            )
        }

        private fun normalizeTrackDegreesOrNullInternal(trackDegrees: Double?): Double? {
            val finite = trackDegrees?.takeIf { it.isFinite() } ?: return null
            return ((finite % 360.0) + 360.0) % 360.0
        }

        private fun shortestTurnDeltaDegreesInternal(from: Double, to: Double): Double {
            val delta = ((to - from + 540.0) % 360.0) - 180.0
            return abs(delta)
        }
    }
}

internal fun hasFreshThermalSourceSamples(
    targets: List<OgnTrafficTarget>,
    processedSourceStateByTargetId: Map<String, ProcessedSourceState>,
    sourceSeenResetThresholdMs: Long
): Boolean {
    if (targets.isEmpty()) return false
    for (target in targets) {
        val targetKey = normalizeOgnAircraftKeyOrNull(target.canonicalKey) ?: continue
        val sourceSeenMonoMs = target.lastSeenMillis
        val previousSourceState = processedSourceStateByTargetId[targetKey]
        val hasFreshSourceSample = previousSourceState == null ||
            sourceSeenMonoMs > previousSourceState.lastSourceSeenMonoMs ||
            sourceSeenMonoMs + sourceSeenResetThresholdMs <
            previousSourceState.lastSourceSeenMonoMs
        if (hasFreshSourceSample) {
            return true
        }
    }
    return false
}

internal fun computeDisplayedThermalHotspots(
    hotspots: Collection<OgnThermalHotspot>,
    displayPercent: Int,
    nowMonoMs: Long,
    areaDedupRadiusMeters: Double,
    areaWinnerRecentWindowMs: Long,
    earthRadiusMeters: Double
): List<OgnThermalHotspot> {
    val hotspotsByPriority = hotspots.sortedWith { first, second ->
        val candidateComparison = compareAreaWinnerPriority(
            candidate = first,
            current = second,
            nowMonoMs = nowMonoMs,
            areaWinnerRecentWindowMs = areaWinnerRecentWindowMs
        )
        when {
            candidateComparison > 0 -> -1
            candidateComparison < 0 -> 1
            else -> 0
        }
    }
    val dedupedHotspots = ArrayList<OgnThermalHotspot>(hotspotsByPriority.size)
    for (hotspot in hotspotsByPriority) {
        val overlapsExistingArea = dedupedHotspots.any { existing ->
            distanceMeters(
                latitudeA = hotspot.latitude,
                longitudeA = hotspot.longitude,
                latitudeB = existing.latitude,
                longitudeB = existing.longitude,
                earthRadiusMeters = earthRadiusMeters
            ) < areaDedupRadiusMeters
        }
        if (!overlapsExistingArea) {
            dedupedHotspots += hotspot
        }
    }
    val clampedPercent = clampOgnHotspotsDisplayPercent(displayPercent)
    val keepCount = if (dedupedHotspots.isEmpty()) {
        0
    } else {
        max(1, ceil(dedupedHotspots.size * (clampedPercent / 100.0)).toInt())
    }
    return dedupedHotspots
        .take(keepCount)
        .sortedByDescending { it.updatedAtMonoMs }
}

internal fun startOfLocalDayWallMsForZone(nowWallMs: Long, zoneId: ZoneId): Long {
    val localDate = Instant.ofEpochMilli(nowWallMs)
        .atZone(zoneId)
        .toLocalDate()
    return localDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
}

internal fun startOfNextLocalDayWallMsForZone(nowWallMs: Long, zoneId: ZoneId): Long {
    val localDate = Instant.ofEpochMilli(nowWallMs)
        .atZone(zoneId)
        .toLocalDate()
        .plusDays(1)
    return localDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
}

internal fun pruneThermalSegmentIndexCache(
    segmentIndexByTargetId: MutableMap<String, Int>,
    trackerByTargetId: Map<String, ThermalTracker>,
    hotspotById: Map<String, OgnThermalHotspot>
) {
    if (segmentIndexByTargetId.isEmpty()) return
    val retainedTargetIds = HashSet<String>(trackerByTargetId.size + hotspotById.size)
    retainedTargetIds.addAll(trackerByTargetId.keys)
    for (hotspot in hotspotById.values) {
        retainedTargetIds += hotspot.sourceTargetId
    }
    val iterator = segmentIndexByTargetId.entries.iterator()
    while (iterator.hasNext()) {
        val entry = iterator.next()
        if (!retainedTargetIds.contains(entry.key)) {
            iterator.remove()
        }
    }
}

internal fun isThermalEntrySample(climbRateMps: Double?, entryThresholdMps: Double): Boolean {
    return climbRateMps != null && climbRateMps >= entryThresholdMps
}

internal fun nextThermalHotspotId(
    targetId: String,
    segmentIndexByTargetId: MutableMap<String, Int>
): String {
    val nextSegmentIndex = (segmentIndexByTargetId[targetId] ?: 0) + 1
    segmentIndexByTargetId[targetId] = nextSegmentIndex
    return "${targetId}-thermal-$nextSegmentIndex"
}

internal fun ensureThermalTrackerHotspotId(
    tracker: ThermalTracker,
    segmentIndexByTargetId: MutableMap<String, Int>
): String {
    val existing = tracker.hotspotId
    if (!existing.isNullOrBlank()) return existing
    val recovered = nextThermalHotspotId(tracker.sourceTargetId, segmentIndexByTargetId)
    tracker.hotspotId = recovered
    return recovered
}

internal fun qualifiesThermalTracker(
    tracker: ThermalTracker,
    nowMonoMs: Long,
    minConfirmDurationMs: Long,
    minConfirmPeakClimbMps: Double,
    minConfirmCumulativeTurnDegrees: Double,
    minConfirmAltitudeGainMeters: Double,
    minConfirmSampleCount: Int
): Boolean {
    val durationMs = nowMonoMs - tracker.startedAtMonoMs
    if (durationMs < minConfirmDurationMs) return false
    if (tracker.maxClimbRateMps < minConfirmPeakClimbMps) return false
    if (tracker.accumulatedTurnDegrees <= minConfirmCumulativeTurnDegrees) return false

    val startAltitudeMeters = tracker.startAltitudeMeters
    val maxAltitudeMeters = tracker.maxAltitudeMeters
    if (startAltitudeMeters != null && maxAltitudeMeters != null) {
        if (maxAltitudeMeters - startAltitudeMeters < minConfirmAltitudeGainMeters) {
            return false
        }
    }

    return tracker.sampleCount >= minConfirmSampleCount
}

private fun compareAreaWinnerPriority(
    candidate: OgnThermalHotspot,
    current: OgnThermalHotspot,
    nowMonoMs: Long,
    areaWinnerRecentWindowMs: Long
): Int {
    val candidateActive = candidate.state == OgnThermalHotspotState.ACTIVE
    val currentActive = current.state == OgnThermalHotspotState.ACTIVE
    if (candidateActive != currentActive) {
        return if (candidateActive) 1 else -1
    }

    val candidateRecent = isRecentAreaWinnerCandidate(candidate, nowMonoMs, areaWinnerRecentWindowMs)
    val currentRecent = isRecentAreaWinnerCandidate(current, nowMonoMs, areaWinnerRecentWindowMs)
    if (candidateRecent != currentRecent) {
        return if (candidateRecent) 1 else -1
    }

    val strengthComparison = compareHotspotStrength(candidate, current)
    if (strengthComparison != 0) return strengthComparison

    return current.id.compareTo(candidate.id)
}

private fun isRecentAreaWinnerCandidate(
    hotspot: OgnThermalHotspot,
    nowMonoMs: Long,
    areaWinnerRecentWindowMs: Long
): Boolean {
    val ageMs = (nowMonoMs - hotspot.updatedAtMonoMs).coerceAtLeast(0L)
    return ageMs <= areaWinnerRecentWindowMs
}

private fun compareHotspotStrength(
    candidate: OgnThermalHotspot,
    current: OgnThermalHotspot
): Int {
    val maxClimbComparison = candidate.maxClimbRateMps.compareTo(current.maxClimbRateMps)
    if (maxClimbComparison != 0) return maxClimbComparison
    val candidateBottomToTop = candidate.averageBottomToTopClimbRateMps ?: Double.NEGATIVE_INFINITY
    val currentBottomToTop = current.averageBottomToTopClimbRateMps ?: Double.NEGATIVE_INFINITY
    val bottomToTopComparison = candidateBottomToTop.compareTo(currentBottomToTop)
    if (bottomToTopComparison != 0) return bottomToTopComparison
    return candidate.updatedAtMonoMs.compareTo(current.updatedAtMonoMs)
}

private fun distanceMeters(
    latitudeA: Double,
    longitudeA: Double,
    latitudeB: Double,
    longitudeB: Double,
    earthRadiusMeters: Double
): Double {
    val latitudeARad = Math.toRadians(latitudeA)
    val latitudeBRad = Math.toRadians(latitudeB)
    val deltaLatitude = latitudeBRad - latitudeARad
    val deltaLongitude = Math.toRadians(longitudeB - longitudeA)
    val x = deltaLongitude * cos((latitudeARad + latitudeBRad) / 2.0)
    val y = deltaLatitude
    return earthRadiusMeters * sqrt(x * x + y * y)
}
