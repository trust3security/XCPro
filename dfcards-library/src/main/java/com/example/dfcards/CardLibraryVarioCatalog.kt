package com.example.dfcards

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Satellite
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline

internal val varioCards = listOf(
    CardDefinition(
        id = "vario_optimized",
        title = "VARIO (Opt)",
        description = "Optimized Kalman filter (Priority 1: R=0.5m)",
        category = CardCategory.VARIO,
        icon = Icons.AutoMirrored.Filled.TrendingUp,
        unit = "m/s",
        primaryFontSize = 15,
        unitFontSize = 9
    ),
    CardDefinition(
        id = "vario_legacy",
        title = "VARIO (Leg)",
        description = "Legacy Kalman filter (Old: R=2.0m)",
        category = CardCategory.VARIO,
        icon = Icons.Filled.Timeline,
        unit = "m/s",
        primaryFontSize = 15,
        unitFontSize = 9
    ),
    CardDefinition(
        id = "vario_raw",
        title = "VARIO (Raw)",
        description = "Raw barometer differentiation (no filtering)",
        category = CardCategory.VARIO,
        icon = Icons.Filled.BarChart,
        unit = "m/s",
        primaryFontSize = 15,
        unitFontSize = 9
    ),
    CardDefinition(
        id = "vario_gps",
        title = "VARIO (GPS)",
        description = "GPS-based vertical speed",
        category = CardCategory.VARIO,
        icon = Icons.Filled.Satellite,
        unit = "m/s",
        primaryFontSize = 15,
        unitFontSize = 9
    ),
    CardDefinition(
        id = "vario_complementary",
        title = "VARIO (Comp)",
        description = "Complementary filter (future - Priority 3)",
        category = CardCategory.VARIO,
        icon = Icons.Filled.Speed,
        unit = "m/s",
        primaryFontSize = 15,
        unitFontSize = 9
    ),
    CardDefinition(
        id = "hawk_vario",
        title = "HAWK Vario",
        description = "HAWK-style baro-gated vario (display only)",
        category = CardCategory.VARIO,
        icon = Icons.AutoMirrored.Filled.TrendingUp,
        unit = "m/s",
        primaryFontSize = 15,
        unitFontSize = 9
    ),
    CardDefinition(
        id = "real_igc_vario",
        title = "CONDOR VARIO",
        description = "Live Condor total-energy variometer (replay fallback)",
        category = CardCategory.VARIO,
        icon = Icons.Filled.Route,
        unit = "m/s",
        primaryFontSize = 15,
        unitFontSize = 9
    )
)
