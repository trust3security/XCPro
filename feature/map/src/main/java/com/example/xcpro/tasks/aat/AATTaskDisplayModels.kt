package com.example.xcpro.tasks.aat

import com.example.xcpro.tasks.aat.models.AATLatLng

data class DisplayPolygon(
    val name: String,
    val points: List<AATLatLng>,
    val areaType: String,
    val centerPoint: AATLatLng
)

data class DisplayLineString(
    val name: String,
    val points: List<AATLatLng>,
    val pathType: String
)

data class DisplayMarker(
    val name: String,
    val position: AATLatLng,
    val type: String,
    val details: Map<String, String> = emptyMap()
)

sealed class DisplayGeometry {
    data class Line(val points: List<AATLatLng>) : DisplayGeometry()
    data class Polygon(val points: List<AATLatLng>) : DisplayGeometry()
}

data class DisplayColors(
    val areaFill: String,
    val areaBorder: String,
    val taskPath: String,
    val startMarker: String,
    val finishMarker: String,
    val areaCenter: String,
    val startFinishLine: String,
    val completedArea: String,
    val activeArea: String,
    val nextArea: String
)

data class MapLabel(
    val text: String,
    val position: AATLatLng,
    val type: String,
    val priority: Int = 0
)
