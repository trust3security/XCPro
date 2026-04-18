package com.trust3.xcpro.map

import kotlinx.coroutines.flow.StateFlow

interface TrafficStreamingGatePort {
    val allowSensorStart: StateFlow<Boolean>
    val isMapVisible: StateFlow<Boolean>

    fun setMapVisible(isVisible: Boolean)
}

interface TrafficViewportPort {
    val currentZoom: StateFlow<Float>

    fun lastCameraTarget(): TrafficMapCoordinate?
}

interface TrafficOwnshipPort {
    val location: StateFlow<TrafficMapOwnshipLocation?>
    val isFlying: StateFlow<Boolean>
    val altitudeMeters: StateFlow<Double?>
    val isCircling: StateFlow<Boolean>
    val circlingFeatureEnabled: StateFlow<Boolean>
}

interface AdsbTrafficFilterPort {
    val maxDistanceKm: StateFlow<Int>
    val verticalAboveMeters: StateFlow<Double>
    val verticalBelowMeters: StateFlow<Double>
}

interface TrafficSelectionPort {
    val selectedOgnId: StateFlow<String?>
    val selectedThermalId: StateFlow<String?>
    val selectedThermalDetailsVisible: StateFlow<Boolean>
    val selectedAdsbId: StateFlow<Icao24?>

    fun setSelectedOgnId(id: String?)
    fun setSelectedThermalId(id: String?)
    fun setSelectedThermalDetailsVisible(visible: Boolean)
    fun setSelectedAdsbId(id: Icao24?)
}

interface TrafficUserMessagePort {
    suspend fun showToast(message: String)
}

data class TrafficMapCoordinate(
    val latitude: Double,
    val longitude: Double
)

data class TrafficMapOwnshipLocation(
    val latitude: Double,
    val longitude: Double,
    val speedMs: Double,
    val bearingDeg: Double,
    val bearingAccuracyDeg: Double? = null,
    val speedAccuracyMs: Double? = null,
    val sampleTimeMillis: Long? = null
)
