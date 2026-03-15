package com.example.dfcards

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Satellite

internal val advancedCards = listOf(
    CardDefinition(
        id = "g_force",
        title = "G FORCE",
        description = "Current G-force acceleration",
        category = CardCategory.ADVANCED,
        icon = Icons.Filled.Contrast,
        unit = "G",
        primaryFontSize = 14,
        unitFontSize = 9
    ),
    CardDefinition(
        id = "flarm",
        title = "FLARM",
        description = "FLARM traffic status",
        category = CardCategory.ADVANCED,
        icon = Icons.Filled.Radar,
        primaryFontSize = 12,
        unitFontSize = 8
    ),
    CardDefinition(
        id = "qnh",
        title = "QNH",
        description = "Barometric pressure setting",
        category = CardCategory.ADVANCED,
        icon = Icons.Filled.Compress,
        primaryFontSize = 13,
        unitFontSize = 8
    ),
    CardDefinition(
        id = "satelites",
        title = "SATELLITES",
        description = "Number of GPS satellites",
        category = CardCategory.ADVANCED,
        icon = Icons.Filled.Satellite,
        primaryFontSize = 14,
        unitFontSize = 8
    ),
    CardDefinition(
        id = "gps_accuracy",
        title = "GPS ACC",
        description = "GPS position accuracy",
        category = CardCategory.ADVANCED,
        icon = Icons.Filled.GpsFixed,
        unit = "m",
        primaryFontSize = 13,
        unitFontSize = 9
    )
)
