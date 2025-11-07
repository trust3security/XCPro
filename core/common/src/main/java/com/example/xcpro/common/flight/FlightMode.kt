package com.example.xcpro.common.flight

/**
 * Shared flight-mode taxonomy used across the map, profiles, and flight-data features.
 * Keeps enum in core/common so feature modules can depend on the same source of truth.
 */
enum class FlightMode(val number: Int, val displayName: String) {
    CRUISE(number = 1, displayName = "Cruise"),
    THERMAL(number = 2, displayName = "Thermal"),
    FINAL_GLIDE(number = 3, displayName = "Final Glide"),
    HAWK(number = 4, displayName = "XCPro V1")
}
