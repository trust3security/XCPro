package com.example.dfcards

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timeline

internal val performanceCards = listOf(
    CardDefinition(
        id = "thermal_avg",
        title = "TC 30s",
        description = "Thermal climb, last 30 s",
        category = CardCategory.PERFORMANCE,
        icon = Icons.AutoMirrored.Filled.TrendingUp,
        unit = "m/s",
        primaryFontSize = 14,
        unitFontSize = 9
    ),
    CardDefinition(
        id = "thermal_tc_avg",
        title = "TC AVG",
        description = "Current circle climb rate (10 s window)",
        category = CardCategory.PERFORMANCE,
        icon = Icons.Filled.Timeline,
        unit = "m/s",
        primaryFontSize = 14,
        unitFontSize = 9
    ),
    CardDefinition(
        id = "thermal_t_avg",
        title = "T AVG",
        description = "Thermal average (entire climb)",
        category = CardCategory.PERFORMANCE,
        icon = Icons.Filled.Star,
        unit = "m/s",
        primaryFontSize = 14,
        unitFontSize = 9
    ),
    CardDefinition(
        id = "thermal_tc_gain",
        title = "TC GAIN",
        description = "Altitude gained in this thermal",
        category = CardCategory.PERFORMANCE,
        icon = Icons.AutoMirrored.Filled.TrendingUp,
        unit = "ft",
        primaryFontSize = 14,
        unitFontSize = 9
    ),
    CardDefinition(
        id = "netto",
        title = "NETTO",
        description = "Air mass vertical movement",
        category = CardCategory.PERFORMANCE,
        icon = Icons.Filled.Air,
        unit = "m/s",
        primaryFontSize = 14,
        unitFontSize = 9
    ),
    CardDefinition(
        id = "netto_avg30",
        title = "NETTO 30S",
        description = "30-second average netto vario",
        category = CardCategory.PERFORMANCE,
        icon = Icons.Filled.Timeline,
        unit = "m/s",
        primaryFontSize = 14,
        unitFontSize = 9
    ),
    CardDefinition(
        id = "levo_netto",
        title = "LEVO NETTO",
        description = "Glide-netto (straight flight, distance window)",
        category = CardCategory.PERFORMANCE,
        icon = Icons.Filled.Air,
        unit = "m/s",
        primaryFontSize = 14,
        unitFontSize = 9
    ),
    CardDefinition(
        id = "ld_curr",
        title = "L/D CURR",
        description = "Fused pilot-facing current glide ratio with wind-aware zero-wind fallback",
        category = CardCategory.PERFORMANCE,
        icon = Icons.Filled.Timeline,
        unit = ":1",
        primaryFontSize = 13,
        unitFontSize = 8
    ),
    CardDefinition(
        id = "ld_vario",
        title = "L/D VARIO",
        description = "Measured through-air glide ratio from true airspeed and TE vario",
        category = CardCategory.PERFORMANCE,
        icon = Icons.Filled.Timeline,
        unit = ":1",
        primaryFontSize = 13,
        unitFontSize = 8
    ),
    CardDefinition(
        id = "polar_ld",
        title = "POLAR L/D",
        description = "Theoretical still-air glide ratio at the current indicated airspeed",
        category = CardCategory.PERFORMANCE,
        icon = Icons.Filled.Timeline,
        unit = ":1",
        primaryFontSize = 13,
        unitFontSize = 8
    ),
    CardDefinition(
        id = "best_ld",
        title = "BEST L/D",
        description = "Best theoretical still-air glide ratio from the active polar",
        category = CardCategory.PERFORMANCE,
        icon = Icons.Filled.Star,
        unit = ":1",
        primaryFontSize = 13,
        unitFontSize = 8
    ),
    CardDefinition(
        id = "mc",
        title = "MC",
        description = "MacCready setting",
        category = CardCategory.PERFORMANCE,
        icon = Icons.Filled.Speed,
        unit = "m/s",
        primaryFontSize = 14,
        unitFontSize = 9
    ),
    CardDefinition(
        id = "mc_speed",
        title = "MC SPEED",
        description = "MacCready speed to fly",
        category = CardCategory.PERFORMANCE,
        icon = Icons.Filled.Speed,
        unit = "kt",
        primaryFontSize = 14,
        unitFontSize = 9
    ),
    CardDefinition(
        id = "bugs",
        title = "BUGS",
        description = "Live bug degradation",
        category = CardCategory.PERFORMANCE,
        icon = Icons.Filled.Timeline,
        unit = "%",
        primaryFontSize = 14,
        unitFontSize = 9
    ),
    CardDefinition(
        id = "ballast_factor",
        title = "BALLAST",
        description = "Live ballast overload factor",
        category = CardCategory.PERFORMANCE,
        icon = Icons.Filled.Star,
        unit = "x",
        primaryFontSize = 14,
        unitFontSize = 9
    )
)
