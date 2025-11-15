package com.example.dfcards

import androidx.compose.runtime.*
import com.example.dfcards.calculations.ConfidenceLevel

/**
 * ✅ PHASE 3: FlightDataProvider - simplified interface (fallback removed)
 *
 * This composable receives a data provider lambda that emits RealTimeFlightData.
 * The conversion from CompleteFlightData (new system) happens in the app module.
 *
 * FLOW: FlightDataCalculator → CompleteFlightData → [Adapter in app module] → RealTimeFlightData → Cards
 */
@Composable
fun FlightDataProvider(
    dataProvider: suspend ((RealTimeFlightData) -> Unit) -> Unit,
    onDataReceived: (RealTimeFlightData) -> Unit
) {
    LaunchedEffect(Unit) {
        dataProvider { data ->
            onDataReceived(data)
        }
    }
}


// Data classes stay the same
data class RealTimeFlightData(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val gpsAltitude: Double = 0.0,
    val baroAltitude: Double = 0.0,
    val agl: Double = 0.0,
    val verticalSpeed: Double = 0.0,  // Default vario (currently optimized)
    val displayVario: Double = 0.0,
    val pressureAltitude: Double = 0.0,
    val baroGpsDelta: Double? = null,
    val baroConfidence: ConfidenceLevel = ConfidenceLevel.LOW,
    val qnhCalibrationAgeSeconds: Long = -1,
    val groundSpeed: Double = 0.0,
    val track: Double = 0.0,
    val accuracy: Double = 0.0,
    val satelliteCount: Int = 0,
    val flightTime: String = "00:00",
    val timestamp: Long = System.currentTimeMillis(),

    val currentPressureHPa: Double = 1013.25,
    val qnh: Double = 1013.25,
    val isQNHCalibrated: Boolean = false,  // Whether QNH was calibrated by GPS

    val windSpeed: Float = 0f,
    val windDirection: Float = 0f,
    val thermalAverage: Float = 0f,
    val thermalAverageCircle: Float = 0f,
    val thermalAverageTotal: Float = 0f,
    val thermalGain: Double = 0.0,
    val currentLD: Float = 0f,
    val netto: Float = 0f,
    val displayNetto: Double = 0.0,
    val nettoValid: Boolean = false,
    val trueAirspeed: Double = 0.0,
    val indicatedAirspeed: Double = 0.0,
    val windQuality: Int = 0,
    val windSource: String = "",
    val windHeadwind: Double = 0.0,
    val windCrosswind: Double = 0.0,
    // NEW: Multiple vario implementations for testing (VARIO_IMPROVEMENTS.md)
    val varioOptimized: Double = 0.0,      // Optimized Kalman (R=0.5m)
    val varioLegacy: Double = 0.0,         // Legacy Kalman (R=2.0m)
    val varioRaw: Double = 0.0,            // Raw barometer differentiation
    val varioGPS: Double = 0.0,            // GPS vertical speed
    val varioComplementary: Double = 0.0,  // Complementary filter (future)
    val bruttoAverage30s: Double = 0.0,
    val nettoAverage30s: Double = 0.0,
    val varioSource: String = "UNKNOWN",
    val varioValid: Boolean = false,

    val lastUpdateTime: Long = System.currentTimeMillis(),
    val calculationSource: String = "GPS+BARO+AGL",
    val airspeedSource: String = "UNKNOWN"
)

// ✅ PHASE 3: All old calculation classes removed (FlightDataManager, WindData)
// Now using UnifiedSensorManager + FlightDataCalculator in app module
