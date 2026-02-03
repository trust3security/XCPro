package com.example.xcpro.map.widgets

import com.example.xcpro.core.common.geometry.OffsetPx

enum class MapWidgetId {
    SIDE_HAMBURGER,
    FLIGHT_MODE,
    BALLAST
}

data class MapWidgetOffsets(
    val sideHamburger: OffsetPx,
    val flightMode: OffsetPx,
    val ballast: OffsetPx
)
