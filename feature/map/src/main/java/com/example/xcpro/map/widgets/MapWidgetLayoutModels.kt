package com.example.xcpro.map.widgets

import com.example.xcpro.core.common.geometry.OffsetPx

enum class MapWidgetId {
    SIDE_HAMBURGER,
    FLIGHT_MODE,
    SETTINGS_SHORTCUT,
    BALLAST
}

data class MapWidgetOffsets(
    val sideHamburger: OffsetPx,
    val flightMode: OffsetPx,
    val settingsShortcut: OffsetPx,
    val ballast: OffsetPx
)
