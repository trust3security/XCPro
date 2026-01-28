package com.example.dfcards

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Satellite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Timeline

private val essentialCards = listOf(
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

private val varioCards = listOf(
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
        id = "real_igc_vario",
        title = "REAL IGC",
        description = "Raw lift/sink directly from the active IGC replay sample",
        category = CardCategory.VARIO,
        icon = Icons.Filled.Route,
        unit = "m/s",
        primaryFontSize = 15,
        unitFontSize = 9
    )
)

private val navigationCards = listOf(
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
        id = "wpt_eta",
        title = "WPT ETA",
        description = "Estimated time to waypoint",
        category = CardCategory.NAVIGATION,
        icon = Icons.Filled.Notifications,
        primaryFontSize = 13,
        unitFontSize = 8
    )
)

private val performanceCards = listOf(
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
        id = "ld_curr",
        title = "L/D CURR",
        description = "Current lift to drag ratio",
        category = CardCategory.PERFORMANCE,
        icon = Icons.Filled.Timeline,
        unit = ":1",
        primaryFontSize = 13,
        unitFontSize = 8
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
    )
)

private val timeWeatherCards = listOf(
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

private val competitionCards = listOf(
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

private val advancedCards = listOf(
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

internal val cardCatalogSections: List<Pair<CardCategory, List<CardDefinition>>> = listOf(
    CardCategory.ESSENTIAL to essentialCards,
    CardCategory.VARIO to varioCards,
    CardCategory.NAVIGATION to navigationCards,
    CardCategory.PERFORMANCE to performanceCards,
    CardCategory.TIME_WEATHER to timeWeatherCards,
    CardCategory.COMPETITION to competitionCards,
    CardCategory.ADVANCED to advancedCards
)

internal val allCardDefinitions: List<CardDefinition> =
    cardCatalogSections.flatMap { it.second }

internal val cardsByCategory: Map<CardCategory, List<CardDefinition>> =
    cardCatalogSections.toMap()
