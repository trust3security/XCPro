package com.example.xcpro.tasks.aat

import com.example.xcpro.tasks.aat.models.AATTask
import com.example.xcpro.tasks.aat.models.AATTaskDistance
import com.example.xcpro.tasks.aat.models.AATTaskValidation
import java.time.LocalDateTime

data class AATTaskAnalysis(
    val task: AATTask,
    val validation: AATTaskValidation,
    val distances: AATTaskDistance?,
    val calculatedAt: LocalDateTime
)

data class AATDisplayPackage(
    val areaPolygons: List<DisplayPolygon>,
    val taskPath: DisplayLineString,
    val startFinishMarkers: List<DisplayMarker>,
    val areaMarkers: List<DisplayMarker>,
    val startGeometry: DisplayGeometry?,
    val finishGeometry: DisplayGeometry?,
    val areaLabels: List<MapLabel>,
    val colors: DisplayColors,
    val flightTrack: DisplayLineString?,
    val creditedFixMarkers: List<DisplayMarker>
)
