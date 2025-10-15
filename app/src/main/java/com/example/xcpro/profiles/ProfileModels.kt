package com.example.xcpro.profiles

import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui1.icons.Glider
import com.example.ui1.icons.Paraglider
import com.example.ui1.icons.Sailplane
import com.example.ui1.icons.Hangglider
import com.example.xcpro.FlightMode
import com.example.dfcards.FlightTemplate
import java.util.UUID

enum class AircraftType(
    val displayName: String,
    val icon: ImageVector,
    val defaultModes: List<FlightMode>
) {
    PARAGLIDER(
        displayName = "Paraglider",
        icon = Paraglider,
        defaultModes = listOf(FlightMode.CRUISE, FlightMode.THERMAL)
    ),
    HANG_GLIDER(
        displayName = "Hang Glider", 
        icon = Hangglider,
        defaultModes = listOf(FlightMode.CRUISE, FlightMode.THERMAL)
    ),
    SAILPLANE(
        displayName = "Sailplane",
        icon = Sailplane,
        defaultModes = listOf(FlightMode.CRUISE, FlightMode.THERMAL, FlightMode.FINAL_GLIDE)
    ),
    GLIDER(
        displayName = "Glider",
        icon = Glider,
        defaultModes = listOf(FlightMode.CRUISE, FlightMode.THERMAL, FlightMode.FINAL_GLIDE)
    )
}

data class UIPosition(
    val x: Float,
    val y: Float
)

data class UILayout(
    val hamburgerPosition: UIPosition = UIPosition(10f, 10f), // Default top-left
    val hamburgerSize: Float = 48f, // Default size in dp
    val variometerPosition: UIPosition = UIPosition(50f, 50f), // Default position
    val variometerSize: Float = 150f // Default size in pixels
)

data class ProfilePreferences(
    val units: UnitSystem = UnitSystem.METRIC,
    val theme: String = "default",
    val autoSwitchModes: Boolean = true,
    val cardAnimations: Boolean = true,
    val uiLayout: UILayout = UILayout()
)

enum class UnitSystem(val displayName: String) {
    METRIC("Metric (m/s, m)"),
    IMPERIAL("Imperial (ft/min, ft)"),
    MIXED("Mixed")
}

data class UserProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val aircraftType: AircraftType,
    val aircraftModel: String? = null,
    val description: String? = null,
    val flightTemplateIds: List<String> = emptyList(), // Store only IDs, not full templates
    val cardConfigurations: Map<FlightMode, List<String>> = emptyMap(),
    val preferences: ProfilePreferences = ProfilePreferences(),
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long = System.currentTimeMillis()
) {
    fun getDisplayName(): String {
        return if (aircraftModel != null) {
            "$name ($aircraftModel)"
        } else {
            "$name - ${aircraftType.displayName}"
        }
    }
    
    fun getAvailableModes(): List<FlightMode> {
        return aircraftType.defaultModes
    }
    
    fun getFlightTemplates(): List<FlightTemplate> {
        return ProfileAwareTemplates.getTemplatesForAircraft(aircraftType)
            .filter { template -> flightTemplateIds.contains(template.id) }
            .ifEmpty { ProfileAwareTemplates.getTemplatesForAircraft(aircraftType) }
    }
}

data class ProfileCreationRequest(
    val name: String,
    val aircraftType: AircraftType,
    val aircraftModel: String? = null,
    val description: String? = null,
    val copyFromProfile: UserProfile? = null
)