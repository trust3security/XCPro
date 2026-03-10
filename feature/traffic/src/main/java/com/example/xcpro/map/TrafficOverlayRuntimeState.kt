package com.example.xcpro.map

import org.maplibre.android.maps.MapLibreMap

/**
 * Map-free interface for overlay runtime state.
 *
 * This keeps traffic overlay runtime code independent from feature/map while still
 * allowing the map module to provide its own state holder instance.
 */
interface TrafficOverlayRuntimeState {
    val mapLibreMap: MapLibreMap?
    val blueLocationLayerId: String
    fun bringBlueLocationOverlayToFront()

    var ognTrafficOverlay: OgnTrafficOverlay?
    var ognTargetRingOverlay: OgnTargetRingOverlay?
    var ognTargetLineOverlay: OgnTargetLineOverlay?
    var ognThermalOverlay: OgnThermalOverlay?
    var ognGliderTrailOverlay: OgnGliderTrailOverlay?
    var adsbTrafficOverlay: AdsbTrafficOverlay?
}

/** Lightweight coordinate for OGN target visuals while staying out of map model dependency. */
data class OverlayCoordinate(
    val latitude: Double,
    val longitude: Double
)
