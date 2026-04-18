package com.trust3.xcpro.screens.navdrawer

import com.trust3.xcpro.ogn.OGN_THERMAL_RETENTION_DEFAULT_HOURS
import com.trust3.xcpro.ogn.OGN_HOTSPOTS_DISPLAY_PERCENT_DEFAULT

data class HotspotsSettingsUiState(
    val retentionHours: Int = OGN_THERMAL_RETENTION_DEFAULT_HOURS,
    val displayPercent: Int = OGN_HOTSPOTS_DISPLAY_PERCENT_DEFAULT
)
