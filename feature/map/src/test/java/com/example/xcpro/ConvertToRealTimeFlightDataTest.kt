package com.example.xcpro

import android.hardware.SensorManager
import com.example.dfcards.calculations.ConfidenceLevel
import com.example.xcpro.common.geo.GeoPoint
import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.PressureHpa
import com.example.xcpro.common.units.SpeedMs
import com.example.xcpro.common.units.VerticalSpeedMs
import com.example.xcpro.glide.GlideSolution
import com.example.xcpro.sensors.BaroData
import com.example.xcpro.sensors.CompassData
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.weather.wind.model.WindSource
import com.example.xcpro.weather.wind.model.WindState
import com.example.xcpro.weather.wind.model.WindVector
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertToRealTimeFlightDataTest {

    @Test
    fun maps_core_fields_from_complete_data() {
        val gps = GPSData(
            position = GeoPoint(latitude = 37.5, longitude = -122.4),
            altitude = AltitudeM(1000.0),
            speed = SpeedMs(30.0),
            bearing = 123.0,
            accuracy = 5f,
            timestamp = 1_000L,
            monotonicTimestampMillis = 1_000L
        )
        val baro = BaroData(
            pressureHPa = PressureHpa(1010.0),
            timestamp = 900L,
            monotonicTimestampMillis = 900L
        )
        val compass = CompassData(
            heading = 200.0,
            accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
            timestamp = 950L,
            monotonicTimestampMillis = 950L
        )
        val complete = CompleteFlightData(
            gps = gps,
            baro = baro,
            compass = compass,
            baroAltitude = AltitudeM(1200.0),
            qnh = PressureHpa(1015.0),
            isQNHCalibrated = true,
            verticalSpeed = VerticalSpeedMs(1.2),
            displayVario = VerticalSpeedMs(1.1),
            displayNeedleVario = VerticalSpeedMs(1.0),
            displayNeedleVarioFast = VerticalSpeedMs(1.5),
            audioVario = VerticalSpeedMs(1.3),
            baselineVario = VerticalSpeedMs(0.8),
            baselineDisplayVario = VerticalSpeedMs(0.7),
            baselineVarioValid = true,
            bruttoVario = VerticalSpeedMs(1.2),
            bruttoAverage30s = VerticalSpeedMs(1.0),
            bruttoAverage30sValid = true,
            nettoAverage30s = VerticalSpeedMs(0.9),
            nettoAverage30sValid = true,
            varioSource = "TE",
            varioValid = true,
            pressureAltitude = AltitudeM(1100.0),
            navAltitude = AltitudeM(1190.0),
            baroGpsDelta = AltitudeM(5.0),
            baroConfidence = ConfidenceLevel.HIGH,
            qnhCalibrationAgeSeconds = 12L,
            agl = AltitudeM(100.0),
            thermalAverage = VerticalSpeedMs(2.5),
            thermalAverageCircle = VerticalSpeedMs(1.5),
            thermalAverageTotal = VerticalSpeedMs(1.2),
            thermalGain = AltitudeM(150.0),
            thermalGainValid = true,
            currentThermalLiftRate = VerticalSpeedMs(1.3),
            currentThermalValid = true,
            currentLD = 35f,
            currentLDValid = true,
            polarLdCurrentSpeed = 38f,
            polarLdCurrentSpeedValid = true,
            polarBestLd = 44f,
            polarBestLdValid = true,
            netto = VerticalSpeedMs(-0.5),
            displayNetto = VerticalSpeedMs(-0.4),
            nettoValid = true,
            trueAirspeed = SpeedMs(25.0),
            indicatedAirspeed = SpeedMs(23.0),
            airspeedSource = "TAS",
            tasValid = true,
            timestamp = 12_345L,
            dataQuality = "GPS+BARO",
            thermalAverageValid = true
        )

        val result = convertToRealTimeFlightData(
            completeData = complete,
            windState = null,
            isFlying = true,
            glideSolution = GlideSolution(
                valid = true,
                requiredGlideRatio = 28.4,
                arrivalHeightMeters = 130.0,
                requiredAltitudeMeters = 1_070.0,
                arrivalHeightMc0Meters = 170.0,
                distanceRemainingMeters = 12_345.0
            ),
            flightTime = "12:34",
            lastUpdateTimeMillis = 9_999L
        )

        assertEquals(37.5, result.latitude, 1e-6)
        assertEquals(-122.4, result.longitude, 1e-6)
        assertEquals(1200.0, result.baroAltitude, 1e-6)
        assertEquals(1015.0, result.qnh, 1e-6)
        assertEquals(true, result.isQNHCalibrated)
        assertEquals(1190.0, result.navAltitude, 1e-6)
        assertEquals(2.5f, result.thermalAverage, 1e-6f)
        assertEquals(0.9, result.nettoAverage30s, 1e-6)
        assertEquals(true, result.nettoAverage30sValid)
        assertEquals(true, result.currentLDValid)
        assertEquals(38f, result.polarLdCurrentSpeed, 1e-6f)
        assertEquals(true, result.polarLdCurrentSpeedValid)
        assertEquals(44f, result.polarBestLd, 1e-6f)
        assertEquals(true, result.polarBestLdValid)
        assertEquals(28.4, result.requiredGlideRatio, 1e-6)
        assertEquals(130.0, result.arrivalHeightM, 1e-6)
        assertEquals(1_070.0, result.requiredAltitudeM, 1e-6)
        assertEquals(170.0, result.arrivalHeightMc0M, 1e-6)
        assertEquals("12:34", result.flightTime)
        assertEquals(12_345L, result.timestamp)
        assertEquals(9_999L, result.lastUpdateTime)
        assertEquals("TE", result.varioSource)
    }

    @Test
    fun stale_wind_is_not_used_for_heading_resolution() {
        val gps = GPSData(
            position = GeoPoint(latitude = 37.5, longitude = -122.4),
            altitude = AltitudeM(1000.0),
            speed = SpeedMs(22.0),
            bearing = 100.0,
            accuracy = 5f,
            timestamp = 2_000L,
            monotonicTimestampMillis = 2_000L
        )
        val complete = CompleteFlightData(
            gps = gps,
            baro = null,
            compass = null,
            baroAltitude = AltitudeM(1200.0),
            qnh = PressureHpa(1015.0),
            isQNHCalibrated = true,
            verticalSpeed = VerticalSpeedMs(0.8),
            bruttoVario = VerticalSpeedMs(0.8),
            pressureAltitude = AltitudeM(1100.0),
            baroGpsDelta = null,
            baroConfidence = ConfidenceLevel.MEDIUM,
            qnhCalibrationAgeSeconds = 5L,
            agl = AltitudeM(100.0),
            thermalAverage = VerticalSpeedMs(0.0),
            currentLD = 20f,
            netto = VerticalSpeedMs(0.0),
            timestamp = 2_000L,
            dataQuality = "GPS",
            thermalAverageValid = false
        )
        val staleWind = WindState(
            vector = WindVector(east = 6.0, north = 0.0),
            source = WindSource.MANUAL,
            quality = 5,
            stale = true,
            confidence = 1.0
        )

        val result = convertToRealTimeFlightData(
            completeData = complete,
            windState = staleWind,
            isFlying = true
        )

        assertEquals("TRACK", result.headingSource)
    }

    @Test
    fun wind_not_selected_by_airspeed_source_is_not_used_for_heading_resolution() {
        val gps = GPSData(
            position = GeoPoint(latitude = 37.5, longitude = -122.4),
            altitude = AltitudeM(1000.0),
            speed = SpeedMs(22.0),
            bearing = 100.0,
            accuracy = 5f,
            timestamp = 2_000L,
            monotonicTimestampMillis = 2_000L
        )
        val complete = CompleteFlightData(
            gps = gps,
            baro = null,
            compass = null,
            baroAltitude = AltitudeM(1200.0),
            qnh = PressureHpa(1015.0),
            isQNHCalibrated = true,
            verticalSpeed = VerticalSpeedMs(0.8),
            bruttoVario = VerticalSpeedMs(0.8),
            pressureAltitude = AltitudeM(1100.0),
            baroGpsDelta = null,
            baroConfidence = ConfidenceLevel.MEDIUM,
            qnhCalibrationAgeSeconds = 5L,
            agl = AltitudeM(100.0),
            thermalAverage = VerticalSpeedMs(0.0),
            currentLD = 20f,
            netto = VerticalSpeedMs(0.0),
            airspeedSource = "GPS",
            timestamp = 2_000L,
            dataQuality = "GPS",
            thermalAverageValid = false
        )
        val availableWind = WindState(
            vector = WindVector(east = 6.0, north = 0.0),
            source = WindSource.MANUAL,
            quality = 5,
            stale = false,
            confidence = 1.0
        )

        val result = convertToRealTimeFlightData(
            completeData = complete,
            windState = availableWind,
            isFlying = true
        )

        assertEquals("TRACK", result.headingSource)
    }
}
