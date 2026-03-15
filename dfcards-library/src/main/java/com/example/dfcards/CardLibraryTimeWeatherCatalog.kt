package com.example.dfcards

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Timer

internal val timeWeatherCards = listOf(
    CardDefinition(
        id = "wind_spd",
        title = "WIND SPD",
        description = "Wind speed",
        category = CardCategory.TIME_WEATHER,
        icon = Icons.AutoMirrored.Filled.Send,
        unit = "kt",
        primaryFontSize = 14,
        unitFontSize = 9
    ),
    CardDefinition(
        id = "wind_dir",
        title = "WIND DIR",
        description = "Wind direction",
        category = CardCategory.TIME_WEATHER,
        icon = Icons.Filled.LocationOn,
        unit = "",
        primaryFontSize = 13,
        unitFontSize = 8
    ),
    CardDefinition(
        id = "wind_arrow",
        title = "WIND",
        description = "Arrow showing wind direction plus head/crosswind",
        category = CardCategory.TIME_WEATHER,
        icon = Icons.Filled.Radar,
        unit = "",
        primaryFontSize = 18,
        unitFontSize = 8
    ),
    CardDefinition(
        id = "local_time",
        title = "Time",
        description = "Current local time",
        category = CardCategory.TIME_WEATHER,
        icon = Icons.Filled.Notifications,
        primaryFontSize = 14,
        unitFontSize = 8,
        secondaryFontSize = 9
    ),
    CardDefinition(
        id = "flight_time",
        title = "FLIGHT TIME",
        description = "Total flight duration",
        category = CardCategory.TIME_WEATHER,
        icon = Icons.Filled.Timer,
        primaryFontSize = 14,
        unitFontSize = 8
    )
)
