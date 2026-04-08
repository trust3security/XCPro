package com.example.xcpro.navigation

import com.example.xcpro.core.flight.calculations.ConfidenceLevel
import com.example.xcpro.common.geo.GeoPoint
import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.PressureHpa
import com.example.xcpro.common.units.SpeedMs
import com.example.xcpro.common.units.VerticalSpeedMs
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.tasks.navigation.NavigationRouteInvalidReason
import com.example.xcpro.tasks.navigation.NavigationRoutePoint
import com.example.xcpro.tasks.navigation.NavigationRouteSnapshot
import com.example.xcpro.tasks.racing.RacingGeometryUtils
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaypointNavigationRepositoryTest {

    @Test
    fun waypoint_metrics_use_task_owned_boundary_target_and_groundspeed_eta() = runTest {
        val routePoint = NavigationRoutePoint(lat = 0.0, lon = 0.08, label = "Boundary Finish")
        val repository = WaypointNavigationRepository(
            flightDataFlow = MutableStateFlow(completeFlightData(speedMs = 30.0)),
            routeFlow = MutableStateFlow(
                NavigationRouteSnapshot(
                    label = "Finish",
                    remainingWaypoints = listOf(routePoint),
                    valid = true
                )
            )
        )

        val snapshot = repository.waypointNavigation.first()
        val expectedDistance = RacingGeometryUtils.haversineDistanceMeters(0.0, 0.0, routePoint.lat, routePoint.lon)
        val expectedBearing = RacingGeometryUtils.calculateBearing(0.0, 0.0, routePoint.lat, routePoint.lon)
        val expectedEta = 1_000L + ((expectedDistance / 30.0) * 1_000.0).roundToLong()
        val legacyCenterDistance = RacingGeometryUtils.haversineDistanceMeters(0.0, 0.0, 0.0, 0.1)

        assertTrue(snapshot.valid)
        assertEquals("Boundary Finish", snapshot.targetLabel)
        assertEquals(expectedDistance, snapshot.distanceMeters, 1e-3)
        assertEquals(expectedBearing, snapshot.bearingTrueDegrees, 1e-3)
        assertTrue(snapshot.etaValid)
        assertEquals(WaypointEtaSource.GROUND_SPEED, snapshot.etaSource)
        assertEquals(expectedEta, snapshot.etaEpochMillis)
        assertTrue(snapshot.distanceMeters < legacyCenterDistance)
    }

    @Test
    fun waypoint_metrics_propagate_no_task_from_route_seam() = runTest {
        val repository = WaypointNavigationRepository(
            flightDataFlow = MutableStateFlow(completeFlightData()),
            routeFlow = MutableStateFlow(
                NavigationRouteSnapshot(
                    valid = false,
                    invalidReason = NavigationRouteInvalidReason.NO_TASK
                )
            )
        )

        val snapshot = repository.waypointNavigation.first()

        assertFalse(snapshot.valid)
        assertEquals(WaypointNavigationInvalidReason.NO_TASK, snapshot.invalidReason)
        assertFalse(snapshot.etaValid)
        assertEquals(WaypointNavigationInvalidReason.NO_TASK, snapshot.etaInvalidReason)
    }

    @Test
    fun waypoint_eta_is_static_when_groundspeed_is_too_low() = runTest {
        val repository = WaypointNavigationRepository(
            flightDataFlow = MutableStateFlow(completeFlightData(speedMs = 1.5)),
            routeFlow = MutableStateFlow(
                NavigationRouteSnapshot(
                    label = "Turnpoint",
                    remainingWaypoints = listOf(
                        NavigationRoutePoint(lat = 0.0, lon = 0.05, label = "Boundary TP")
                    ),
                    valid = true
                )
            )
        )

        val snapshot = repository.waypointNavigation.first()

        assertTrue(snapshot.valid)
        assertFalse(snapshot.etaValid)
        assertEquals(WaypointNavigationInvalidReason.STATIC, snapshot.etaInvalidReason)
    }

    @Test
    fun waypoint_metrics_are_deterministic_for_equivalent_runtime_inputs() = runTest {
        val firstRepository = WaypointNavigationRepository(
            flightDataFlow = MutableStateFlow(completeFlightData(speedMs = 25.0)),
            routeFlow = MutableStateFlow(
                NavigationRouteSnapshot(
                    label = "Turnpoint",
                    remainingWaypoints = listOf(
                        NavigationRoutePoint(lat = 0.0, lon = 0.05, label = "Boundary TP")
                    ),
                    valid = true
                )
            )
        )
        val secondRepository = WaypointNavigationRepository(
            flightDataFlow = MutableStateFlow(completeFlightData(speedMs = 25.0)),
            routeFlow = MutableStateFlow(
                NavigationRouteSnapshot(
                    label = "Turnpoint",
                    remainingWaypoints = listOf(
                        NavigationRoutePoint(lat = 0.0, lon = 0.05, label = "Boundary TP")
                    ),
                    valid = true
                )
            )
        )

        assertEquals(firstRepository.waypointNavigation.first(), secondRepository.waypointNavigation.first())
    }

    private fun completeFlightData(
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        speedMs: Double = 20.0
    ): CompleteFlightData = CompleteFlightData(
        gps = GPSData(
            position = GeoPoint(latitude = latitude, longitude = longitude),
            altitude = AltitudeM(1_250.0),
            speed = SpeedMs(speedMs),
            bearing = 90.0,
            accuracy = 5f,
            timestamp = 1_000L,
            monotonicTimestampMillis = 1_000L
        ),
        baro = null,
        compass = null,
        baroAltitude = AltitudeM(1_250.0),
        qnh = PressureHpa(1013.25),
        isQNHCalibrated = true,
        verticalSpeed = VerticalSpeedMs(0.0),
        bruttoVario = VerticalSpeedMs(0.0),
        pressureAltitude = AltitudeM(1_250.0),
        navAltitude = AltitudeM(1_250.0),
        baroGpsDelta = null,
        baroConfidence = ConfidenceLevel.HIGH,
        qnhCalibrationAgeSeconds = 0L,
        agl = AltitudeM(200.0),
        thermalAverage = VerticalSpeedMs(0.0),
        currentLD = 30f,
        netto = VerticalSpeedMs(0.0),
        timestamp = 1_000L,
        dataQuality = "TEST"
    )
}
