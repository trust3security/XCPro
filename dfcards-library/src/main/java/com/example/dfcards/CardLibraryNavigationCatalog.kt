package com.example.dfcards

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timeline

internal val navigationCards = listOf(
    CardDefinition(
        id = "track",
        title = "TRACK",
        description = "Ground track direction",
        category = CardCategory.NAVIGATION,
        icon = Icons.Filled.LocationOn,
        unit = "",
        primaryFontSize = 14,
        unitFontSize = 8
    ),
    CardDefinition(
        id = "wpt_dist",
        title = "WPT DIST",
        description = "Distance to next waypoint",
        category = CardCategory.NAVIGATION,
        icon = Icons.Filled.Place,
        unit = "km",
        primaryFontSize = 13,
        unitFontSize = 9
    ),
    CardDefinition(
        id = "wpt_brg",
        title = "WPT BRG",
        description = "Bearing to next waypoint",
        category = CardCategory.NAVIGATION,
        icon = Icons.AutoMirrored.Filled.Send,
        unit = "",
        primaryFontSize = 14,
        unitFontSize = 8
    ),
    CardDefinition(
        id = "final_gld",
        title = "FINAL GLD",
        description = "Final glide ratio required",
        category = CardCategory.NAVIGATION,
        icon = Icons.Filled.Share,
        unit = ":1",
        primaryFontSize = 14,
        unitFontSize = 8
    ),
    CardDefinition(
        id = "arr_alt",
        title = "ARR ALT",
        description = "Predicted arrival height at finish using the active MacCready setting",
        category = CardCategory.NAVIGATION,
        icon = Icons.Filled.FlightTakeoff,
        unit = "ft",
        primaryFontSize = 13,
        unitFontSize = 8
    ),
    CardDefinition(
        id = "req_alt",
        title = "REQ ALT",
        description = "Current altitude required to reach finish with the configured finish rule",
        category = CardCategory.NAVIGATION,
        icon = Icons.Filled.Home,
        unit = "ft",
        primaryFontSize = 13,
        unitFontSize = 8
    ),
    CardDefinition(
        id = "arr_mc0",
        title = "ARR MC0",
        description = "Predicted arrival height at finish with MacCready set to zero",
        category = CardCategory.NAVIGATION,
        icon = Icons.Filled.Timeline,
        unit = "ft",
        primaryFontSize = 13,
        unitFontSize = 8
    ),
    CardDefinition(
        id = "wpt_eta",
        title = "WPT ETA",
        description = "Estimated time to waypoint",
        category = CardCategory.NAVIGATION,
        icon = Icons.Filled.Notifications,
        primaryFontSize = 13,
        unitFontSize = 8
    )
)
