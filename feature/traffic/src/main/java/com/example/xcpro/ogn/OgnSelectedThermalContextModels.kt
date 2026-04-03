package com.example.xcpro.ogn

data class OgnThermalPoint(
    val latitude: Double,
    val longitude: Double
)

data class SelectedOgnThermalContext(
    val hotspot: OgnThermalHotspot,
    val hotspotPoint: OgnThermalPoint?,
    val highlightedSegments: List<OgnGliderTrailSegment>,
    val loopPoints: List<OgnThermalPoint>,
    val occupancyHullPoints: List<OgnThermalPoint>,
    val startPoint: OgnThermalPoint?,
    val latestPoint: OgnThermalPoint?,
    val driftBearingDeg: Double?,
    val driftDistanceMeters: Double?,
    val ageMs: Long?,
    val durationMs: Long?,
    val altitudeGainMeters: Double?
)

data class SelectedOgnThermalOverlayContext(
    val hotspotId: String,
    val snailColorIndex: Int,
    val hotspotPoint: OgnThermalPoint?,
    val highlightedSegments: List<OgnGliderTrailSegment>,
    val occupancyHullPoints: List<OgnThermalPoint>,
    val startPoint: OgnThermalPoint?,
    val latestPoint: OgnThermalPoint?
)

internal fun SelectedOgnThermalContext.toOverlayContext(): SelectedOgnThermalOverlayContext =
    SelectedOgnThermalOverlayContext(
        hotspotId = hotspot.id,
        snailColorIndex = hotspot.snailColorIndex,
        hotspotPoint = hotspotPoint,
        highlightedSegments = highlightedSegments,
        occupancyHullPoints = occupancyHullPoints,
        startPoint = startPoint,
        latestPoint = latestPoint
    )
