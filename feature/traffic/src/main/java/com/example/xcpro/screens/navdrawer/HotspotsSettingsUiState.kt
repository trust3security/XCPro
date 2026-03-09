package com.example.xcpro.screens.navdrawer

import com.example.xcpro.ogn.OGN_THERMAL_RETENTION_DEFAULT_HOURS
import com.example.xcpro.ogn.OGN_HOTSPOTS_DISPLAY_PERCENT_DEFAULT

data class HotspotsSettingsUiState(
    val retentionHours: Int = OGN_THERMAL_RETENTION_DEFAULT_HOURS,
    val displayPercent: Int = OGN_HOTSPOTS_DISPLAY_PERCENT_DEFAULT
)
