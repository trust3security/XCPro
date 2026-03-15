package com.example.dfcards

import androidx.compose.material.icons.Icons
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
        description = "Distance covered in task",
        category = CardCategory.COMPETITION,
        icon = Icons.Filled.Route,
        unit = "km",
        primaryFontSize = 13,
        unitFontSize = 9
    ),
    CardDefinition(
        id = "start_alt",
        title = "START ALT",
        description = "Altitude at task start",
        category = CardCategory.COMPETITION,
        icon = Icons.Filled.FlightTakeoff,
        unit = "ft",
        primaryFontSize = 13,
        unitFontSize = 9
    )
)
