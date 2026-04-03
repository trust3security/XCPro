package com.example.xcpro.ogn

import com.example.xcpro.adsb.AdsbGeoMath
import com.example.xcpro.core.time.Clock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.combine

@OptIn(ExperimentalCoroutinesApi::class)
internal fun observeSelectedOgnThermalContext(
    selectedThermalId: Flow<String?>,
    hotspots: Flow<List<OgnThermalHotspot>>,
    rawSegments: Flow<List<OgnGliderTrailSegment>>,
    clock: Clock,
    ageTickIntervalMs: Long = AGE_TICK_INTERVAL_MS
): Flow<SelectedOgnThermalContext?> = selectedThermalId
    .distinctUntilChanged()
    .flatMapLatest { selectedId ->
        if (selectedId.isNullOrBlank()) {
            flowOf(null)
        } else {
            combine(
                hotspots,
                rawSegments,
                wallClockTicker(clock = clock, intervalMs = ageTickIntervalMs)
            ) { hotspotList, segmentList, nowWallMs ->
                buildSelectedOgnThermalContext(
                    selectedId = selectedId,
                    hotspots = hotspotList,
                    rawSegments = segmentList,
                    nowWallMs = nowWallMs
                )
            }
        }
    }
    .distinctUntilChanged()

internal fun buildSelectedOgnThermalContext(
    selectedId: String,
    hotspots: List<OgnThermalHotspot>,
    rawSegments: List<OgnGliderTrailSegment>,
    nowWallMs: Long
): SelectedOgnThermalContext? {
    val hotspot = hotspots.firstOrNull { it.id == selectedId } ?: return null
    val hotspotPoint = hotspot.toPointOrNull()
    val highlightedSegments = rawSegments
        .asSequence()
        .filter { it.sourceTargetId == hotspot.sourceTargetId }
        .filter { it.timestampMonoMs in hotspot.startedAtMonoMs..hotspot.updatedAtMonoMs }
        .sortedBy { it.timestampMonoMs }
        .toList()
    val loopPoints = buildLoopPoints(highlightedSegments)
    val distinctLoopPoints = distinctPoints(loopPoints)
    val occupancyHullPoints = convexHull(distinctLoopPoints)
    val startPoint = loopPoints.firstOrNull()
    val latestPoint = loopPoints.lastOrNull()
    val driftDistanceMeters = if (startPoint != null && latestPoint != null) {
        AdsbGeoMath.haversineMeters(
            startPoint.latitude,
            startPoint.longitude,
            latestPoint.latitude,
            latestPoint.longitude
        ).takeIf { it.isFinite() && it > 0.0 }
    } else {
        null
    }
    val driftBearingDeg = if (
        startPoint != null &&
        latestPoint != null &&
        driftDistanceMeters != null
    ) {
        AdsbGeoMath.bearingDegrees(
            startPoint.latitude,
            startPoint.longitude,
            latestPoint.latitude,
            latestPoint.longitude
        ).takeIf { it.isFinite() }
    } else {
        null
    }
    val ageMs = if (hotspot.updatedAtWallMs > 0L) {
        (nowWallMs - hotspot.updatedAtWallMs).coerceAtLeast(0L)
    } else {
        null
    }
    val durationMs = (hotspot.updatedAtMonoMs - hotspot.startedAtMonoMs)
        .takeIf { it >= 0L }
    val altitudeGainMeters = if (
        hotspot.startAltitudeMeters?.isFinite() == true &&
        hotspot.maxAltitudeMeters?.isFinite() == true
    ) {
        hotspot.maxAltitudeMeters - hotspot.startAltitudeMeters
    } else {
        null
    }

    return SelectedOgnThermalContext(
        hotspot = hotspot,
        hotspotPoint = hotspotPoint,
        highlightedSegments = highlightedSegments,
        loopPoints = loopPoints,
        occupancyHullPoints = occupancyHullPoints,
        startPoint = startPoint,
        latestPoint = latestPoint,
        driftBearingDeg = driftBearingDeg,
        driftDistanceMeters = driftDistanceMeters,
        ageMs = ageMs,
        durationMs = durationMs,
        altitudeGainMeters = altitudeGainMeters
    )
}

private fun wallClockTicker(clock: Clock, intervalMs: Long): Flow<Long> = flow {
    val safeIntervalMs = intervalMs.coerceAtLeast(250L)
    emit(clock.nowWallMs())
    while (currentCoroutineContext().isActive) {
        delay(safeIntervalMs)
        emit(clock.nowWallMs())
    }
}

private fun buildLoopPoints(segments: List<OgnGliderTrailSegment>): List<OgnThermalPoint> {
    if (segments.isEmpty()) return emptyList()
    val points = ArrayList<OgnThermalPoint>(segments.size + 1)
    for ((index, segment) in segments.withIndex()) {
        if (index == 0) {
            segment.startPointOrNull()?.let(points::add)
        }
        segment.endPointOrNull()?.let(points::add)
    }
    return points
}

private fun distinctPoints(points: List<OgnThermalPoint>): List<OgnThermalPoint> {
    if (points.isEmpty()) return emptyList()
    val distinct = ArrayList<OgnThermalPoint>(points.size)
    for (point in points) {
        if (distinct.any { it.latitude == point.latitude && it.longitude == point.longitude }) {
            continue
        }
        distinct += point
    }
    return distinct
}

private fun convexHull(points: List<OgnThermalPoint>): List<OgnThermalPoint> {
    if (points.size < 3) return emptyList()
    val sorted = points.sortedWith(compareBy<OgnThermalPoint>({ it.longitude }, { it.latitude }))
    val lower = ArrayList<OgnThermalPoint>()
    for (point in sorted) {
        while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], point) <= 0.0) {
            lower.removeAt(lower.lastIndex)
        }
        lower += point
    }
    val upper = ArrayList<OgnThermalPoint>()
    for (index in sorted.indices.reversed()) {
        val point = sorted[index]
        while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], point) <= 0.0) {
            upper.removeAt(upper.lastIndex)
        }
        upper += point
    }
    lower.removeAt(lower.lastIndex)
    upper.removeAt(upper.lastIndex)
    val hull = lower + upper
    return if (hull.size >= 3) hull else emptyList()
}

private fun cross(a: OgnThermalPoint, b: OgnThermalPoint, c: OgnThermalPoint): Double =
    (b.longitude - a.longitude) * (c.latitude - a.latitude) -
        (b.latitude - a.latitude) * (c.longitude - a.longitude)

private fun OgnThermalHotspot.toPointOrNull(): OgnThermalPoint? =
    if (AdsbGeoMath.isValidCoordinate(latitude, longitude)) {
        OgnThermalPoint(latitude = latitude, longitude = longitude)
    } else {
        null
    }

private fun OgnGliderTrailSegment.startPointOrNull(): OgnThermalPoint? =
    if (AdsbGeoMath.isValidCoordinate(startLatitude, startLongitude)) {
        OgnThermalPoint(latitude = startLatitude, longitude = startLongitude)
    } else {
        null
    }

private fun OgnGliderTrailSegment.endPointOrNull(): OgnThermalPoint? =
    if (AdsbGeoMath.isValidCoordinate(endLatitude, endLongitude)) {
        OgnThermalPoint(latitude = endLatitude, longitude = endLongitude)
    } else {
        null
    }

private const val AGE_TICK_INTERVAL_MS = 1_000L
