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
    val createdAt: Long = System.currentTimeMillis()
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
                description = "Single track card for cruise mode",
                cardIds = listOf("track"),
                icon = Icons.Filled.Flight,
                isPreset = true
            ),
            FlightTemplate(
                id = "id02",
                name = "Thermal",
                description = "Single track card for thermal mode",
                cardIds = listOf(
                    "thermal_tc_avg",
                    "thermal_t_avg",
                    "thermal_tc_gain",
                    "netto_avg30",
                    "thermal_avg"
                ),
                icon = Icons.Filled.Refresh,
                isPreset = true
            ),
            FlightTemplate(
                id = "id03",
                name = "Final Glide",
                description = "Three cards for final glide mode",
                cardIds = listOf("gps_alt", "final_gld", "ground_speed"),
                icon = Icons.Filled.Terrain,
                isPreset = true
            ),
            FlightTemplate(
                id = "id04",
                name = "Cross Country",
                description = "Navigation and performance",
                cardIds = listOf(
                    "gps_alt",
                    "track",
                    "wpt_dist",
                    "wpt_brg",
                    "final_gld",
                    "ground_speed",
                    "thermal_t_avg",
                    "thermal_tc_avg",
                    "ld_curr"
                ),
                icon = Icons.Filled.LocationOn,
                isPreset = true
            ),
            FlightTemplate(
                id = "id05", 
                name = "Competition",
                description = "Racing and task management",
                cardIds = listOf("task_spd", "task_dist", "start_alt", "wpt_eta", "final_gld", "ld_curr", "mc_speed", "flight_time"),
                icon = Icons.Filled.EmojiEvents,
                isPreset = true
            )
        )
    }
}
