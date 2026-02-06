package com.example.xcpro.profiles

import com.example.xcpro.common.flight.FlightMode
import java.util.UUID

enum class AircraftType(
    val displayName: String,
    val defaultModes: List<FlightMode>
) {
    PARAGLIDER(
        displayName = "Paraglider",
        defaultModes = listOf(FlightMode.CRUISE, FlightMode.THERMAL)
    ),
    HANG_GLIDER(
        displayName = "Hang Glider",
        defaultModes = listOf(FlightMode.CRUISE, FlightMode.THERMAL)
    ),
    SAILPLANE(
        displayName = "Sailplane",
        defaultModes = listOf(FlightMode.CRUISE, FlightMode.THERMAL, FlightMode.FINAL_GLIDE)
    ),
    GLIDER(
        displayName = "Glider",
        defaultModes = listOf(FlightMode.CRUISE, FlightMode.THERMAL, FlightMode.FINAL_GLIDE)
    )
}

data class UIPosition(
    val x: Float,
    val y: Float
)

data class UILayout(
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
}

data class ProfileCreationRequest(
    val name: String,
    val aircraftType: AircraftType,
    val aircraftModel: String? = null,
    val description: String? = null,
    val copyFromProfile: UserProfile? = null
)

