package com.example.dfcards

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed

internal val competitionCards = listOf(
    CardDefinition(
        id = "task_spd",
        title = "TASK SPD",
        description = "Current task speed",
        category = CardCategory.COMPETITION,
        icon = Icons.Filled.Speed,
        unit = "km/h",
        primaryFontSize = 13,
        unitFontSize = 8
    ),
    CardDefinition(
        id = "task_dist",
        title = "TASK DIST",
        description = "Distance covered since accepted task start",
        category = CardCategory.COMPETITION,
        icon = Icons.Filled.Route,
        unit = "km",
        primaryFontSize = 13,
        unitFontSize = 9
    ),
    CardDefinition(
        id = "task_remain_dist",
        title = "TASK REMAIN DIST",
        description = "Remaining distance on the authoritative task route",
        category = CardCategory.COMPETITION,
        icon = Icons.Filled.Route,
        unit = "km",
        primaryFontSize = 13,
        unitFontSize = 9
    ),
    CardDefinition(
        id = "task_remain_time",
        title = "TASK REMAIN TIME",
        description = "Remaining task time based on achieved task speed",
        category = CardCategory.COMPETITION,
        icon = Icons.Filled.AccessTime,
        primaryFontSize = 14,
        unitFontSize = 8,
        secondaryFontSize = 8
    ),
    CardDefinition(
        id = "start_alt",
        title = "START ALT",
        description = "Authoritative altitude at accepted task start",
        category = CardCategory.COMPETITION,
        icon = Icons.Filled.FlightTakeoff,
        unit = "ft",
        primaryFontSize = 13,
        unitFontSize = 9
    )
)
