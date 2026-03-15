package com.example.dfcards

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed

internal val essentialCards = listOf(
    CardDefinition(
        id = "gps_alt",
        title = "ASL ALT",
        description = "Altitude above mean sea level (Barometric when calibrated, GPS fallback)",
        category = CardCategory.ESSENTIAL,
        icon = Icons.Filled.Home,
        unit = "ft",
        primaryFontSize = 14,
        unitFontSize = 10
    ),
    CardDefinition(
        id = "baro_alt",
        title = "BARO ALT",
        description = "Barometric altitude",
        category = CardCategory.ESSENTIAL,
        icon = Icons.Filled.Info,
        unit = "ft",
        primaryFontSize = 14,
        unitFontSize = 10
    ),
    CardDefinition(
        id = "agl",
        title = "AGL",
        description = "Height above ground level",
        category = CardCategory.ESSENTIAL,
        icon = Icons.Filled.Place,
        unit = "ft",
        primaryFontSize = 14,
        unitFontSize = 10
    ),
    CardDefinition(
        id = "vario",
        title = "VARIO (Default)",
        description = "Current active vario (same as Optimized)",
        category = CardCategory.ESSENTIAL,
        icon = Icons.Filled.PlayArrow,
        unit = "m/s",
        primaryFontSize = 15,
        unitFontSize = 9
    ),
    CardDefinition(
        id = "ias",
        title = "IAS",
        description = "Indicated airspeed",
        category = CardCategory.ESSENTIAL,
        icon = Icons.AutoMirrored.Filled.Send,
        unit = "kt",
        primaryFontSize = 14,
        unitFontSize = 9
    ),
    CardDefinition(
        id = "tas",
        title = "TAS",
        description = "True airspeed (estimated from ground speed and wind)",
        category = CardCategory.ESSENTIAL,
        icon = Icons.Filled.Speed,
        unit = "kt",
        primaryFontSize = 14,
        unitFontSize = 9
    ),
    CardDefinition(
        id = "ground_speed",
        title = "SPEED GS",
        description = "Ground speed over terrain",
        category = CardCategory.ESSENTIAL,
        icon = Icons.Filled.PlayArrow,
        unit = "kt",
        primaryFontSize = 14,
        unitFontSize = 9
    )
)
