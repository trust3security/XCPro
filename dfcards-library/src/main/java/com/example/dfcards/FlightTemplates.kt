package com.example.dfcards

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

data class FlightTemplate(
    val id: String,
    val name: String,
    val description: String,
    val cardIds: List<String>,
    val icon: ImageVector = Icons.Default.Star,
    val isPreset: Boolean = false, // NEW: Track if it's a built-in template
    val createdAt: Long = 0L
)

enum class LayoutMode(
    val displayName: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    AUTO_GRID("Grid", Icons.Filled.GridView),
    FREE_FORM("Free", Icons.Filled.OpenWith),
    TEMPLATE("Template", Icons.Filled.ViewModule)
}

object FlightTemplates {
    fun getDefaultTemplates(): List<FlightTemplate> {
        return listOf(
            FlightTemplate(
                id = "id01",
                name = "Cruise",
                description = "Single AGL card for cruise mode",
                cardIds = listOf("agl"),
                icon = Icons.Filled.Flight,
                isPreset = true
            ),
            FlightTemplate(
                id = "id02",
                name = "Thermal",
                description = "Core thermal efficiency cards",
                cardIds = listOf(
                    "thermal_tc_gain",
                    "thermal_tc_avg",
                    "thermal_t_avg"
                ),
                icon = Icons.Filled.Refresh,
                isPreset = true
            ),
            FlightTemplate(
                id = "id03",
                name = "Glide",
                description = "Core glide performance cards with live polar metrics",
                cardIds = listOf("gps_alt", "polar_ld", "best_ld", "mc_speed"),
                icon = Icons.Filled.Terrain,
                isPreset = true
            ),
            FlightTemplate(
                id = "id04",
                name = "Cross Country",
                description = "Live cruise and glide-performance metrics",
                cardIds = listOf(
                    "gps_alt",
                    "track",
                    "ground_speed",
                    "final_gld",
                    "wind_arrow",
                    "polar_ld",
                    "best_ld",
                    "mc_speed",
                    "thermal_t_avg",
                    "thermal_tc_avg",
                    "ld_curr"
                ),
                icon = Icons.Filled.LocationOn,
                isPreset = true
            ),
            FlightTemplate(
                id = "id05", 
                name = "Performance",
                description = "Live energy, glide, and speed-to-fly metrics",
                cardIds = listOf("ld_curr", "polar_ld", "best_ld", "netto", "netto_avg30", "levo_netto", "mc_speed", "flight_time"),
                icon = Icons.Filled.EmojiEvents,
                isPreset = true
            )
        )
    }
}
