package com.example.xcpro.profiles

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.example.dfcards.FlightTemplate
import com.example.xcpro.FlightMode

object ProfileAwareTemplates {
    
    fun getTemplatesForAircraft(aircraftType: AircraftType): List<FlightTemplate> {
        return when (aircraftType) {
            AircraftType.PARAGLIDER -> getParagliderTemplates()
            AircraftType.HANG_GLIDER -> getHangGliderTemplates()  
            AircraftType.SAILPLANE -> getSailplaneTemplates()
            AircraftType.GLIDER -> getGliderTemplates()
        }
    }
    
    fun getTemplatesForFlightMode(aircraftType: AircraftType, flightMode: FlightMode): List<FlightTemplate> {
        val allTemplates = getTemplatesForAircraft(aircraftType)
        return allTemplates.filter { template ->
            when (aircraftType to flightMode) {
                AircraftType.PARAGLIDER to FlightMode.THERMAL -> 
                    template.id in listOf("thermal", "paraglider_thermal", "beginner")
                AircraftType.PARAGLIDER to FlightMode.CRUISE -> 
                    template.id in listOf("cross_country", "paraglider_xc", "essential")
                AircraftType.SAILPLANE to FlightMode.FINAL_GLIDE -> 
                    template.id in listOf("competition", "sailplane_final", "cross_country")
                AircraftType.SAILPLANE to FlightMode.THERMAL -> 
                    template.id in listOf("thermal", "sailplane_thermal")
                AircraftType.SAILPLANE to FlightMode.CRUISE -> 
                    template.id in listOf("cross_country", "sailplane_cruise", "essential")
                else -> template.id in listOf("essential", "thermal", "cross_country")
            }
        }
    }
    
    private fun getParagliderTemplates(): List<FlightTemplate> {
        return listOf(
            FlightTemplate(
                id = "paraglider_thermal",
                name = "Paraglider Thermal",
                description = "Optimize thermal climbing",
                cardIds = listOf("vario", "thermal_avg", "agl", "wind_spd", "ias", "climb_rate"),
                icon = Icons.Filled.TrendingUp,
                isPreset = true
            ),
            FlightTemplate(
                id = "paraglider_xc",
                name = "Paraglider XC",
                description = "Cross-country navigation",
                cardIds = listOf("gps_alt", "ground_speed", "track", "wpt_dist", "wpt_brg", "thermal_avg", "wind_spd"),
                icon = Icons.Filled.Explore,
                isPreset = true
            ),
            FlightTemplate(
                id = "paraglider_safety",
                name = "Safety Focus",
                description = "Safety-critical information",
                cardIds = listOf("agl", "wind_spd", "ias", "gps_alt", "emergency_freq"),
                icon = Icons.Filled.Security,
                isPreset = true
            ),
            // Include common templates
            FlightTemplate(
                id = "essential",
                name = "Essential",
                description = "Basic flight instruments",
                cardIds = listOf("gps_alt", "baro_alt", "agl", "vario", "ias", "ground_speed"),
                icon = Icons.Filled.Star,
                isPreset = true
            ),
            FlightTemplate(
                id = "beginner",
                name = "Beginner",
                description = "Simple, essential cards",
                cardIds = listOf("gps_alt", "ias", "vario", "agl"),
                icon = Icons.Filled.School,
                isPreset = true
            )
        )
    }
    
    private fun getHangGliderTemplates(): List<FlightTemplate> {
        return listOf(
            FlightTemplate(
                id = "hangglider_ridge",
                name = "Ridge Soaring",
                description = "Ridge lift optimization",
                cardIds = listOf("ias", "agl", "wind_spd", "wind_dir", "gps_alt", "ground_speed"),
                icon = Icons.Filled.Landscape,
                isPreset = true
            ),
            FlightTemplate(
                id = "hangglider_thermal",
                name = "Hang Glider Thermal",
                description = "Thermal climbing",
                cardIds = listOf("vario", "thermal_avg", "ias", "agl", "climb_rate", "netto"),
                icon = Icons.Filled.TrendingUp,
                isPreset = true
            ),
            FlightTemplate(
                id = "hangglider_xc",
                name = "Hang Glider XC",
                description = "Cross-country performance",
                cardIds = listOf("gps_alt", "ld_curr", "track", "wpt_dist", "final_gld", "ground_speed"),
                icon = Icons.Filled.Explore,
                isPreset = true
            ),
            // Include common templates
            FlightTemplate(
                id = "essential",
                name = "Essential",
                description = "Basic flight instruments",
                cardIds = listOf("gps_alt", "baro_alt", "agl", "vario", "ias", "ground_speed"),
                icon = Icons.Filled.Star,
                isPreset = true
            )
        )
    }
    
    private fun getSailplaneTemplates(): List<FlightTemplate> {
        return listOf(
            FlightTemplate(
                id = "sailplane_thermal",
                name = "Sailplane Thermal",
                description = "Advanced thermal climbing",
                cardIds = listOf("vario", "netto", "thermal_avg", "mc_speed", "ld_inst", "agl"),
                icon = Icons.Filled.TrendingUp,
                isPreset = true
            ),
            FlightTemplate(
                id = "sailplane_cruise",
                name = "Sailplane Cruise",
                description = "Inter-thermal cruise",
                cardIds = listOf("ias", "mc_speed", "ld_curr", "ld_avg", "wind_spd", "track"),
                icon = Icons.Filled.FlightTakeoff,
                isPreset = true
            ),
            FlightTemplate(
                id = "sailplane_final",
                name = "Final Glide",
                description = "Final glide optimization",
                cardIds = listOf("final_gld", "wpt_alt_req", "wpt_dist", "mc_speed", "ld_req", "safety_alt"),
                icon = Icons.Filled.Flag,
                isPreset = true
            ),
            FlightTemplate(
                id = "competition",
                name = "Competition",
                description = "Racing and task management",
                cardIds = listOf("task_spd", "task_dist", "start_alt", "wpt_eta", "final_gld", "ld_curr", "mc_speed", "flight_time"),
                icon = Icons.Filled.EmojiEvents,
                isPreset = true
            ),
            FlightTemplate(
                id = "cross_country",
                name = "Cross Country",
                description = "Navigation and performance",
                cardIds = listOf("gps_alt", "track", "wpt_dist", "wpt_brg", "final_gld", "ground_speed", "thermal_avg", "ld_curr"),
                icon = Icons.Filled.LocationOn,
                isPreset = true
            ),
            FlightTemplate(
                id = "essential",
                name = "Essential",
                description = "Basic flight instruments",
                cardIds = listOf("gps_alt", "baro_alt", "agl", "vario", "ias", "ground_speed"),
                icon = Icons.Filled.Star,
                isPreset = true
            )
        )
    }
    
    private fun getGliderTemplates(): List<FlightTemplate> {
        return getSailplaneTemplates() // Gliders use same templates as sailplanes
    }
    
    fun getCardConfigurationForMode(aircraftType: AircraftType, flightMode: FlightMode): List<String> {
        return when (aircraftType to flightMode) {
            // Paraglider configurations
            AircraftType.PARAGLIDER to FlightMode.THERMAL -> 
                listOf("vario", "thermal_avg", "agl", "wind_spd", "ias", "climb_rate")
            AircraftType.PARAGLIDER to FlightMode.CRUISE -> 
                listOf("gps_alt", "ground_speed", "track", "wpt_dist", "wind_spd", "thermal_avg")
                
            // Hang Glider configurations
            AircraftType.HANG_GLIDER to FlightMode.THERMAL -> 
                listOf("vario", "thermal_avg", "ias", "agl", "climb_rate", "netto")
            AircraftType.HANG_GLIDER to FlightMode.CRUISE -> 
                listOf("ias", "agl", "wind_spd", "ground_speed", "ld_curr", "track")
                
            // Sailplane configurations
            AircraftType.SAILPLANE to FlightMode.THERMAL -> 
                listOf("vario", "netto", "thermal_avg", "mc_speed", "ld_inst", "agl")
            AircraftType.SAILPLANE to FlightMode.CRUISE -> 
                listOf("ias", "mc_speed", "ld_curr", "ld_avg", "wind_spd", "track")
            AircraftType.SAILPLANE to FlightMode.FINAL_GLIDE -> 
                listOf("final_gld", "wpt_alt_req", "wpt_dist", "mc_speed", "ld_req", "safety_alt")
                
            // Glider configurations (same as sailplane)
            AircraftType.GLIDER to FlightMode.THERMAL -> 
                listOf("vario", "netto", "thermal_avg", "mc_speed", "ld_inst", "agl")
            AircraftType.GLIDER to FlightMode.CRUISE -> 
                listOf("ias", "mc_speed", "ld_curr", "ld_avg", "wind_spd", "track")
            AircraftType.GLIDER to FlightMode.FINAL_GLIDE -> 
                listOf("final_gld", "wpt_alt_req", "wpt_dist", "mc_speed", "ld_req", "safety_alt")
                
            // Default fallback
            else -> listOf("gps_alt", "baro_alt", "vario", "ias", "ground_speed", "agl")
        }
    }
}