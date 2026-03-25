package com.example.xcpro.glide

import com.example.dfcards.calculations.ConfidenceLevel
import com.example.xcpro.common.geo.GeoPoint
import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.PressureHpa
import com.example.xcpro.common.units.SpeedMs
import com.example.xcpro.common.units.VerticalSpeedMs
import com.example.xcpro.glider.SpeedBoundsMs
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.tasks.TaskRuntimeSnapshot
import com.example.xcpro.tasks.core.RacingFinishCustomParams
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.navigation.NavigationRouteInvalidReason
import com.example.xcpro.tasks.navigation.NavigationRouteKind
import com.example.xcpro.tasks.navigation.NavigationRoutePoint
import com.example.xcpro.tasks.navigation.NavigationRouteSnapshot
import com.example.xcpro.tasks.racing.RacingGeometryUtils
import com.example.xcpro.weather.wind.model.WindState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlideComputationRepositoryTest {

    @Test
    fun glide_uses_task_owned_route_seam_for_distance_remaining() = runTest {
        val routePoint = NavigationRoutePoint(lat = 0.0, lon = 0.08, label = "Boundary Finish")
        val repository = GlideComputationRepository(
            flightDataFlow = MutableStateFlow(completeFlightData()),
            windStateFlow = MutableStateFlow(WindState()),
            taskSnapshotFlow = MutableStateFlow(racingTaskSnapshot()),
            routeFlow = MutableStateFlow(
                NavigationRouteSnapshot(
                    kind = NavigationRouteKind.TASK_FINISH,
                    label = "Finish",
                    remainingWaypoints = listOf(routePoint),
                    valid = true
                )
            ),
            finalGlideUseCase = FinalGlideUseCase(fakePolarSinkProvider())
        )

        val solution = repository.glide.first()
        val expectedDistanceMeters = RacingGeometryUtils.haversineDistanceMeters(
            0.0,
            0.0,
            routePoint.lat,
            routePoint.lon
        )
        val legacyCenterDistanceMeters = RacingGeometryUtils.haversineDistanceMeters(
            0.0,
            0.0,
            0.0,
            0.1
        )

        assertTrue(solution.valid)
        assertEquals(expectedDistanceMeters, solution.distanceRemainingMeters, 1e-3)
        assertTrue(solution.distanceRemainingMeters < legacyCenterDistanceMeters)
    }

    @Test
    fun glide_returns_prestart_when_route_has_not_started() = runTest {
        val repository = GlideComputationRepository(
            flightDataFlow = MutableStateFlow(completeFlightData()),
            windStateFlow = MutableStateFlow(WindState()),
            taskSnapshotFlow = MutableStateFlow(racingTaskSnapshot()),
            routeFlow = MutableStateFlow(
                NavigationRouteSnapshot(
                    kind = NavigationRouteKind.TASK_FINISH,
                    label = "Finish",
                    remainingWaypoints = listOf(
                        NavigationRoutePoint(lat = 0.0, lon = 0.1, label = "Finish")
                    ),
                    valid = false,
                    invalidReason = NavigationRouteInvalidReason.PRESTART
                )
            ),
            finalGlideUseCase = FinalGlideUseCase(fakePolarSinkProvider())
        )

        val solution = repository.glide.first()

        assertFalse(solution.valid)
        assertEquals(GlideInvalidReason.PRESTART, solution.invalidReason)
    }

    @Test
    fun glide_requires_finish_altitude_rule_from_task_runtime() = runTest {
        val repository = GlideComputationRepository(
            flightDataFlow = MutableStateFlow(completeFlightData()),
            windStateFlow = MutableStateFlow(WindState()),
            taskSnapshotFlow = MutableStateFlow(racingTaskSnapshot(finishMinAltitudeMeters = null)),
            routeFlow = MutableStateFlow(
                NavigationRouteSnapshot(
                    kind = NavigationRouteKind.TASK_FINISH,
                    label = "Finish",
                    remainingWaypoints = listOf(
                        NavigationRoutePoint(lat = 0.0, lon = 0.1, label = "Finish")
                    ),
                    valid = true
                )
            ),
            finalGlideUseCase = FinalGlideUseCase(fakePolarSinkProvider())
        )

        val solution = repository.glide.first()

        assertFalse(solution.valid)
        assertEquals(GlideInvalidReason.NO_FINISH_ALTITUDE, solution.invalidReason)
    }

    @Test
    fun glide_is_deterministic_for_equivalent_runtime_inputs() = runTest {
        val firstRepository = GlideComputationRepository(
            flightDataFlow = MutableStateFlow(completeFlightData(navAltitudeMeters = 1_350.0, macCready = 2.0)),
            windStateFlow = MutableStateFlow(WindState()),
            taskSnapshotFlow = MutableStateFlow(racingTaskSnapshot()),
            routeFlow = MutableStateFlow(
                NavigationRouteSnapshot(
                    kind = NavigationRouteKind.TASK_FINISH,
                    label = "Finish",
                    remainingWaypoints = listOf(
                        NavigationRoutePoint(lat = 0.0, lon = 0.09, label = "Boundary Finish")
                    ),
                    valid = true
                )
            ),
            finalGlideUseCase = FinalGlideUseCase(fakePolarSinkProvider())
        )
        val secondRepository = GlideComputationRepository(
            flightDataFlow = MutableStateFlow(completeFlightData(navAltitudeMeters = 1_350.0, macCready = 2.0)),
            windStateFlow = MutableStateFlow(WindState()),
            taskSnapshotFlow = MutableStateFlow(racingTaskSnapshot()),
            routeFlow = MutableStateFlow(
                NavigationRouteSnapshot(
                    kind = NavigationRouteKind.TASK_FINISH,
                    label = "Finish",
                    remainingWaypoints = listOf(
                        NavigationRoutePoint(lat = 0.0, lon = 0.09, label = "Boundary Finish")
                    ),
                    valid = true
                )
            ),
            finalGlideUseCase = FinalGlideUseCase(fakePolarSinkProvider())
        )

        assertEquals(firstRepository.glide.first(), secondRepository.glide.first())
    }

    private fun racingTaskSnapshot(finishMinAltitudeMeters: Double? = 900.0): TaskRuntimeSnapshot {
        val finishParams = mutableMapOf<String, Any>()
        RacingFinishCustomParams(minAltitudeMeters = finishMinAltitudeMeters).applyTo(finishParams)
        return TaskRuntimeSnapshot(
            task = Task(
                id = "task-1",
                waypoints = listOf(
                    waypoint("start", 0.0, 0.0, WaypointRole.START),
                    waypoint("tp1", 0.0, 0.05, WaypointRole.TURNPOINT),
                    waypoint("finish", 0.0, 0.1, WaypointRole.FINISH, finishParams)
                )
            ),
            taskType = TaskType.RACING,
            activeLeg = 0
        )
    }

    private fun waypoint(
        id: String,
        lat: Double,
        lon: Double,
        role: WaypointRole,
        customParameters: Map<String, Any> = emptyMap()
    ): TaskWaypoint = TaskWaypoint(
        id = id,
        title = id.replaceFirstChar(Char::titlecase),
        subtitle = "",
        lat = lat,
        lon = lon,
        role = role,
        customParameters = customParameters
    )

    private fun completeFlightData(
        navAltitudeMeters: Double = 1_250.0,
        macCready: Double = 1.0,
        isQnhCalibrated: Boolean = true
    ): CompleteFlightData = CompleteFlightData(
        gps = GPSData(
            position = GeoPoint(latitude = 0.0, longitude = 0.0),
            altitude = AltitudeM(navAltitudeMeters),
            speed = SpeedMs(30.0),
            bearing = 90.0,
            accuracy = 5f,
            timestamp = 1_000L,
            monotonicTimestampMillis = 1_000L
        ),
        baro = null,
        compass = null,
        baroAltitude = AltitudeM(navAltitudeMeters),
        qnh = PressureHpa(1013.25),
        isQNHCalibrated = isQnhCalibrated,
        verticalSpeed = VerticalSpeedMs(0.0),
        bruttoVario = VerticalSpeedMs(0.0),
        pressureAltitude = AltitudeM(navAltitudeMeters),
        navAltitude = AltitudeM(navAltitudeMeters),
        baroGpsDelta = null,
        baroConfidence = ConfidenceLevel.HIGH,
        qnhCalibrationAgeSeconds = 0L,
        agl = AltitudeM(200.0),
        thermalAverage = VerticalSpeedMs(0.0),
        currentLD = 30f,
        netto = VerticalSpeedMs(0.0),
        timestamp = 1_000L,
        dataQuality = "TEST",
        macCready = macCready
    )

    private fun fakePolarSinkProvider(): StillAirSinkProvider = object : StillAirSinkProvider {
        override fun sinkAtSpeed(airspeedMs: Double): Double {
            val centered = airspeedMs - 17.0
            return 0.55 + (centered * centered * 0.01)
        }

        override fun iasBoundsMs(): SpeedBoundsMs = SpeedBoundsMs(minMs = 12.0, maxMs = 25.0)
    }
}
