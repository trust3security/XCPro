package com.example.dfcards

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.abs
import kotlin.math.roundToInt

data class CardDefinition(
    val id: String,
    val title: String,
    val description: String,
    val category: CardCategory,
    val icon: ImageVector,
    val unit: String = "",
    val labelFontSize: Int = 9,
    val primaryFontSize: Int = 14,
    val secondaryFontSize: Int = 7,
    val unitFontSize: Int = 9,
    val unitFontWeight: FontWeight = FontWeight.Medium
)

enum class CardCategory(
    val displayName: String,
    val icon: ImageVector,
    val color: androidx.compose.ui.graphics.Color
) {
    ESSENTIAL("Essential", Icons.Filled.Star, androidx.compose.ui.graphics.Color(0xFF4CAF50)),
    VARIO("Variometers", Icons.Filled.TrendingUp, androidx.compose.ui.graphics.Color(0xFF00BCD4)),  // NEW: Vario testing category
    NAVIGATION("Navigation", Icons.Filled.LocationOn, androidx.compose.ui.graphics.Color(0xFF2196F3)),
    PERFORMANCE("Performance", Icons.Filled.ThumbUp, androidx.compose.ui.graphics.Color(0xFFFF9800)),
    TIME_WEATHER("Time & Weather", Icons.Filled.Notifications, androidx.compose.ui.graphics.Color(0xFF9C27B0)),
    COMPETITION("Competition", Icons.Filled.Star, androidx.compose.ui.graphics.Color(0xFFF44336)),
    ADVANCED("Advanced", Icons.Filled.Settings, androidx.compose.ui.graphics.Color(0xFF607D8B))
}

object CardLibrary {
    val allCards = listOf(
        // ESSENTIAL CARDS
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
        // VARIO CARDS - Testing different implementations
        CardDefinition(
            id = "vario_optimized",
            title = "VARIO (Opt)",
            description = "Optimized Kalman filter (Priority 1: R=0.5m)",
            category = CardCategory.VARIO,
            icon = Icons.Filled.TrendingUp,
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
            icon = Icons.Filled.Send,
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
        ),

        // NAVIGATION CARDS
        CardDefinition(
            id = "track",
            title = "TRACK",
            description = "Ground track direction",
            category = CardCategory.NAVIGATION,
            icon = Icons.Filled.LocationOn,
            unit = "°",
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
            icon = Icons.Filled.Send,
            unit = "°",
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
            primaryFontSize = 13,
            unitFontSize = 8
        ),
        CardDefinition(
            id = "wpt_eta",
            title = "WPT ETA",
            description = "Estimated time to waypoint",
            category = CardCategory.NAVIGATION,
            icon = Icons.Filled.Notifications,
            unit = "",
            primaryFontSize = 13,
            unitFontSize = 8
        ),

        // PERFORMANCE CARDS
        CardDefinition(
            id = "thermal_avg",
            title = "THERMAL AVG",
            description = "Average thermal climb rate",
            category = CardCategory.PERFORMANCE,
            icon = Icons.Filled.TrendingUp,
            unit = "m/s",
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
        ),

        // TIME & WEATHER CARDS
        CardDefinition(
            id = "wind_spd",
            title = "WIND SPD",
            description = "Wind speed",
            category = CardCategory.TIME_WEATHER,
            icon = Icons.Filled.Send,
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
            unit = "°",
            primaryFontSize = 13,
            unitFontSize = 8
        ),
        CardDefinition(
            id = "local_time",
            title = "Time",
            description = "Current local time",
            category = CardCategory.TIME_WEATHER,
            icon = Icons.Filled.Notifications,
            unit = "",
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
            unit = "",
            primaryFontSize = 14,
            unitFontSize = 8
        ),

        // COMPETITION CARDS
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
        ),

        // ADVANCED CARDS
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
            unit = "",
            primaryFontSize = 12,
            unitFontSize = 8
        ),
        CardDefinition(
            id = "qnh",
            title = "QNH",
            description = "Barometric pressure setting",
            category = CardCategory.ADVANCED,
            icon = Icons.Filled.Compress,
            unit = "hPa",
            primaryFontSize = 13,
            unitFontSize = 8
        ),
        CardDefinition(
            id = "satelites",
            title = "SATELLITES",
            description = "Number of GPS satellites",
            category = CardCategory.ADVANCED,
            icon = Icons.Filled.Satellite,
            unit = "",
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

    fun getCardsByCategory(category: CardCategory): List<CardDefinition> {
        return allCards.filter { it.category == category }
    }

    fun searchCards(query: String): List<CardDefinition> {
        return allCards.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true)
        }
    }

    // ✅ NO FILTERING - JUST SHOW THE VALUES
    fun mapLiveDataToCard(
        cardId: String,
        liveData: RealTimeFlightData?
    ): Pair<String, String?> {
        if (liveData == null) return Pair("--", "NO DATA")

        return when (cardId) {
            "gps_alt" -> {
                // ✅ PRIORITY: Use barometric altitude when QNH is calibrated (more accurate!)
                // GPS vertical accuracy: ±30-50m (poor)
                // Barometric accuracy (calibrated): ±8-20m (excellent)
                // This matches real aviation instruments - primary ALT uses barometric, not GPS
                if (liveData.isQNHCalibrated && liveData.currentPressureHPa > 0) {
                    // Use calibrated barometric altitude (more accurate)
                    val altFt = (liveData.baroAltitude * 3.28084).roundToInt()
                    Pair("$altFt ft", "BARO")
                } else if (liveData.gpsAltitude != 0.0) {
                    // Fallback to GPS altitude (less accurate, but better than nothing)
                    val altFt = (liveData.gpsAltitude * 3.28084).roundToInt()
                    Pair("$altFt ft", "GPS (±50m)")
                } else {
                    Pair("-- ft", "NO DATA")
                }
            }
            // In CardLibrary.mapLiveDataToCard function, update the "baro_alt" case:

            "baro_alt" -> {
                if (liveData.currentPressureHPa > 0) {
                    val altFt = (liveData.baroAltitude * 3.28084).roundToInt()
                    val qnh = liveData.qnh.roundToInt()
                    // ✅ FIX: Show calibration status, not value proximity to standard
                    val status = if (liveData.isQNHCalibrated) "QNH $qnh" else "STD"
                    Pair("$altFt ft", status)
                } else {
                    Pair("-- ft", "NO BARO")
                }
            }

            "agl" -> {
                val secondary = if (liveData.isQNHCalibrated) {
                    "QNH ${liveData.qnh.roundToInt()}"
                } else {
                    "NO QNH"
                }

                if (liveData.agl.isNaN()) {
                    Pair("-- ft", secondary)
                } else {
                    val aglFt = (liveData.agl * 3.28084).roundToInt()
                    Pair("${aglFt} ft", secondary)
                }
            }
            // VARIO VARIANTS - Side-by-side testing
            "vario" -> {
                // Default vario (uses verticalSpeed field)
                val varioMs = (liveData.verticalSpeed * 10).roundToInt() / 10.0
                val sign = if (varioMs >= 0) "+" else ""
                Pair("$sign${varioMs}", "OPT")
            }
            "vario_optimized" -> {
                // Optimized Kalman filter (Priority 1: R=0.5m)
                val varioMs = (liveData.varioOptimized * 10).roundToInt() / 10.0
                val sign = if (varioMs >= 0) "+" else ""
                Pair("$sign${varioMs}", "R=0.5")
            }
            "vario_legacy" -> {
                // Legacy Kalman filter (Old: R=2.0m)
                val varioMs = (liveData.varioLegacy * 10).roundToInt() / 10.0
                val sign = if (varioMs >= 0) "+" else ""
                Pair("$sign${varioMs}", "R=2.0")
            }
            "vario_raw" -> {
                // Raw barometer differentiation (no filtering)
                val varioMs = (liveData.varioRaw * 10).roundToInt() / 10.0
                val sign = if (varioMs >= 0) "+" else ""
                Pair("$sign${varioMs}", "RAW")
            }
            "vario_gps" -> {
                // GPS vertical speed
                val varioMs = (liveData.varioGPS * 10).roundToInt() / 10.0
                val sign = if (varioMs >= 0) "+" else ""
                Pair("$sign${varioMs}", "GPS")
            }
            "vario_complementary" -> {
                // Complementary filter (future - Priority 3)
                val varioMs = (liveData.varioComplementary * 10).roundToInt() / 10.0
                val sign = if (varioMs >= 0) "+" else ""
                Pair("$sign${varioMs}", "COMP")
            }
            "ias" -> {
                // ❌ IAS requires pitot tube sensor - phones don't have this!
                // Showing fake calculated values is DANGEROUS for pilots
                Pair("-- kt", "NO SENSOR")
            }
            "ground_speed" -> {
                val speedKt = liveData.groundSpeed.roundToInt()
                Pair("${speedKt} kt", "GPS")
            }
            "track" -> {
                if (liveData.groundSpeed > 2.0) {
                    val trackDeg = liveData.track.roundToInt()
                    Pair("${trackDeg}°", "MAG")
                } else {
                    Pair("--°", "STATIC")
                }
            }
            "wpt_dist" -> Pair("-- km", "NO WPT")
            "wpt_brg" -> Pair("--°", "NO WPT")
            "final_gld" -> Pair("--:1", "NO WPT")
            "wpt_eta" -> Pair("--:--", "NO WPT")
            "ld_curr" -> {
                if (liveData.currentLD > 1f) {
                    Pair("${liveData.currentLD.roundToInt()}:1", "LIVE")
                } else {
                    Pair("--:1", "NO DATA")
                }
            }
            "thermal_avg" -> {
                if (liveData.thermalAverage > 0.1f) {
                    Pair("+${String.format("%.1f", liveData.thermalAverage)} m/s", "AVG")
                } else {
                    Pair("-- m/s", "NO THERMAL")
                }
            }
            "netto" -> {
                if (liveData.groundSpeed > 15.0) {
                    val nettoMs = liveData.netto
                    val sign = if (nettoMs >= 0) "+" else ""
                    Pair("$sign${String.format("%.1f", nettoMs)} m/s", "NETTO")
                } else {
                    Pair("-- m/s", "TOO SLOW")
                }
            }
            "mc_speed" -> {
                // ❌ MacCready speed requires glider polar curve + settings
                // Lookup table is FAKE data - needs proper MC calculation
                Pair("-- kt", "NO MC")
            }
            "flight_time" -> {
                Pair(liveData.flightTime, "FLIGHT")
            }
            "wind_spd" -> {
                if (liveData.windSpeed > 0.5f) {
                    val windKt = liveData.windSpeed.roundToInt()
                    val confidence = if (liveData.groundSpeed > 20) "CALC" else "EST"
                    Pair("${windKt} kt", confidence)
                } else {
                    Pair("-- kt", "NO WIND")
                }
            }
            "wind_dir" -> {
                if (liveData.windSpeed > 1.0f) {
                    val windDir = liveData.windDirection.roundToInt()
                    Pair("${windDir}°", "FROM")
                } else {
                    Pair("--°", "NO WIND")
                }
            }
            "local_time" -> {
                // ✅ Time card now uses independent timer in FlightDataViewModel
                // This mapping is kept for backward compatibility but not actively used
                val currentTime = System.currentTimeMillis()
                val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(currentTime))
                val seconds = java.text.SimpleDateFormat("ss", java.util.Locale.getDefault())
                    .format(java.util.Date(currentTime))
                Pair(time, seconds)
            }
            "task_spd" -> Pair("-- km/h", "NO TASK")
            "task_dist" -> Pair("-- km", "NO TASK")
            "start_alt" -> Pair("-- ft", "NO START")
            "g_force" -> {
                // ❌ G-force requires accelerometer sensor
                // Calculating from vertical speed is WRONG and DANGEROUS
                Pair("-- G", "NO ACCEL")
            }
            "flarm" -> Pair("NO FLARM", "---")
            "qnh" -> {
                if (liveData.currentPressureHPa > 0) {
                    val qnhValue = liveData.qnh.roundToInt()
                    Pair("${qnhValue} hPa", "CALC")
                } else {
                    Pair("-- hPa", "NO BARO")
                }
            }
            "satelites" -> {
                if (liveData.satelliteCount > 0) {
                    val quality = when {
                        liveData.satelliteCount >= 8 -> "GOOD"
                        liveData.satelliteCount >= 6 -> "OK"
                        liveData.satelliteCount >= 4 -> "WEAK"
                        else -> "POOR"
                    }
                    Pair("${liveData.satelliteCount}", quality)
                } else {
                    Pair("--", "NO SATS")
                }
            }
            "gps_accuracy" -> {
                val accM = liveData.accuracy.roundToInt()
                val quality = when {
                    accM < 3 -> "EXCELLENT"
                    accM < 10 -> "GOOD"
                    accM < 20 -> "OK"
                    else -> "POOR"
                }
                Pair("${accM} m", quality)
            }
            // ✅ DEFAULT: Unknown card types
            else -> Pair("--", "UNKNOWN")
        }
    }

    // ✅ Helper functions for calculations
    private fun calculateGForce(verticalSpeed: Double): Float {
        val baseG = 1.0f
        val additionalG = (kotlin.math.abs(verticalSpeed) / 10.0).toFloat() * 0.1f
        return (baseG + additionalG).coerceIn(0.5f, 3.0f)
    }
}
