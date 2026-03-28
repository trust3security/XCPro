package com.example.xcpro.taskperformance

import com.example.dfcards.calculations.ConfidenceLevel
import com.example.xcpro.common.geo.GeoPoint
import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.PressureHpa
import com.example.xcpro.common.units.SpeedMs
import com.example.xcpro.common.units.VerticalSpeedMs
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.tasks.TaskRuntimeSnapshot
import com.example.xcpro.tasks.core.RacingAltitudeReference
import com.example.xcpro.tasks.core.RacingStartCustomParams
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.navigation.NavigationRoutePoint
import com.example.xcpro.tasks.navigation.NavigationRouteSnapshot
import com.example.xcpro.tasks.navigation.TaskPerformanceDistanceProjector
import com.example.xcpro.tasks.racing.RacingGeometryUtils
import com.example.xcpro.tasks.racing.navigation.RacingNavigationFix
import com.example.xcpro.tasks.racing.navigation.RacingNavigationState
import com.example.xcpro.tasks.racing.navigation.RacingNavigationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToLong

class TaskPerformanceRepositoryTest {

    @Test
    fun task_performance_uses_authoritative_start_route_and_start_altitude_truth() = runTest {
        val distanceProjector = TaskPerformanceDistanceProjector()
        val taskSnapshot = racingTaskSnapshot(startAltitudeReference = RacingAltitudeReference.QNH)
        val routePoints = listOf(
            NavigationRoutePoint(lat = 0.0, lon = 0.08, label = "Finish Boundary")
        )
        val acceptedStartFix = RacingNavigationFix(
            lat = 0.0,
            lon = 0.01,
            timestampMillis = 2_000L,
            altitudeMslMeters = 1_100.0,
            altitudeQnhMeters = 1_120.0
        )
        val repository = TaskPerformanceRepository(
            flightDataFlow = MutableStateFlow(completeFlightData(longitude = 0.06, monotonicTimestampMillis = 102_000L)),
            taskSnapshotFlow = MutableStateFlow(taskSnapshot),
            routeFlow = MutableStateFlow(
                NavigationRouteSnapshot(
                    label = "Finish",
                    remainingWaypoints = routePoints,
                    valid = true
                )
            ),
            racingStateFlow = MutableStateFlow(
                RacingNavigationState(
                    status = RacingNavigationStatus.IN_PROGRESS,
                    currentLegIndex = 2,
                    acceptedStartTimestampMillis = 2_000L,
                    acceptedStartFix = acceptedStartFix,
                    acceptedStartAltitudeReference = RacingAltitudeReference.QNH,
                    lastFix = RacingNavigationFix(lat = 0.0, lon = 0.06, timestampMillis = 102_000L)
                )
            ),
            distanceProjector = distanceProjector
        )

        val snapshot = repository.taskPerformance.first()
        val expectedStartReference = distanceProjector.startReferenceDistanceMeters(taskSnapshot, acceptedStartFix)!!
        val expectedRemaining = routeDistanceMeters(
            startLat = 0.0,
            startLon = 0.06,
            routePoints = routePoints
        )
        val expectedTaskDistance = expectedStartReference - expectedRemaining
        val expectedTaskSpeed = expectedTaskDistance / 100.0
        val expectedRemainingTimeMillis = ((expectedRemaining / expectedTaskSpeed) * 1_000.0).roundToLong()

        assertTrue(snapshot.taskSpeedValid)
        assertEquals(expectedTaskSpeed, snapshot.taskSpeedMs, 1e-3)
        assertEquals(TaskPerformanceInvalidReason.NONE, snapshot.taskSpeedInvalidReason)
        assertTrue(snapshot.taskDistanceValid)
        assertEquals(expectedTaskDistance, snapshot.taskDistanceMeters, 1e-3)
        assertTrue(snapshot.taskRemainingDistanceValid)
        assertEquals(expectedRemaining, snapshot.taskRemainingDistanceMeters, 1e-3)
        assertTrue(snapshot.taskRemainingTimeValid)
        assertEquals(expectedRemainingTimeMillis, snapshot.taskRemainingTimeMillis)
        assertEquals(TaskRemainingTimeBasis.ACHIEVED_TASK_SPEED, snapshot.taskRemainingTimeBasis)
        assertTrue(snapshot.startAltitudeValid)
        assertEquals(1_120.0, snapshot.startAltitudeMeters, 1e-6)
    }

    @Test
    fun prestart_metrics_are_explicitly_invalid() = runTest {
        val repository = TaskPerformanceRepository(
            flightDataFlow = MutableStateFlow(completeFlightData()),
            taskSnapshotFlow = MutableStateFlow(racingTaskSnapshot()),
            routeFlow = MutableStateFlow(NavigationRouteSnapshot(valid = false)),
            racingStateFlow = MutableStateFlow(
                RacingNavigationState(status = RacingNavigationStatus.PENDING_START)
            ),
            distanceProjector = TaskPerformanceDistanceProjector()
        )

        val snapshot = repository.taskPerformance.first()

        assertFalse(snapshot.taskSpeedValid)
        assertEquals(TaskPerformanceInvalidReason.PRESTART, snapshot.taskSpeedInvalidReason)
        assertFalse(snapshot.taskDistanceValid)
        assertEquals(TaskPerformanceInvalidReason.PRESTART, snapshot.taskDistanceInvalidReason)
        assertFalse(snapshot.taskRemainingDistanceValid)
        assertEquals(TaskPerformanceInvalidReason.PRESTART, snapshot.taskRemainingDistanceInvalidReason)
        assertFalse(snapshot.taskRemainingTimeValid)
        assertEquals(TaskPerformanceInvalidReason.PRESTART, snapshot.taskRemainingTimeInvalidReason)
        assertFalse(snapshot.startAltitudeValid)
        assertEquals(TaskPerformanceInvalidReason.PRESTART, snapshot.startAltitudeInvalidReason)
    }

    @Test
    fun missing_accepted_start_keeps_start_based_metrics_invalid_without_guessing() = runTest {
        val repository = TaskPerformanceRepository(
            flightDataFlow = MutableStateFlow(completeFlightData(navAltitudeMeters = 1_450.0)),
            taskSnapshotFlow = MutableStateFlow(racingTaskSnapshot()),
            routeFlow = MutableStateFlow(
                NavigationRouteSnapshot(
                    label = "Finish",
                    remainingWaypoints = listOf(
                        NavigationRoutePoint(lat = 0.0, lon = 0.05, label = "Tp1"),
                        NavigationRoutePoint(lat = 0.0, lon = 0.10, label = "Finish")
                    ),
                    valid = true
                )
            ),
            racingStateFlow = MutableStateFlow(
                RacingNavigationState(
                    status = RacingNavigationStatus.IN_PROGRESS,
                    currentLegIndex = 1
                )
            ),
            distanceProjector = TaskPerformanceDistanceProjector()
        )

        val snapshot = repository.taskPerformance.first()

        assertTrue(snapshot.taskRemainingDistanceValid)
        assertFalse(snapshot.taskDistanceValid)
        assertEquals(TaskPerformanceInvalidReason.NO_START, snapshot.taskDistanceInvalidReason)
        assertFalse(snapshot.taskSpeedValid)
        assertEquals(TaskPerformanceInvalidReason.NO_START, snapshot.taskSpeedInvalidReason)
        assertFalse(snapshot.startAltitudeValid)
        assertEquals(TaskPerformanceInvalidReason.NO_START, snapshot.startAltitudeInvalidReason)
    }

    @Test
    fun finished_task_freezes_final_distance_speed_and_zeroes_remaining() = runTest {
        val distanceProjector = TaskPerformanceDistanceProjector()
        val taskSnapshot = racingTaskSnapshot()
        val acceptedStartFix = RacingNavigationFix(
            lat = 0.0,
            lon = 0.01,
            timestampMillis = 2_000L,
            altitudeMslMeters = 1_100.0
        )
        val repository = TaskPerformanceRepository(
            flightDataFlow = MutableStateFlow(completeFlightData(longitude = 0.09, monotonicTimestampMillis = 200_000L)),
            taskSnapshotFlow = MutableStateFlow(taskSnapshot),
            routeFlow = MutableStateFlow(NavigationRouteSnapshot(valid = false)),
            racingStateFlow = MutableStateFlow(
                RacingNavigationState(
                    status = RacingNavigationStatus.FINISHED,
                    currentLegIndex = 2,
                    acceptedStartTimestampMillis = 2_000L,
                    acceptedStartFix = acceptedStartFix,
                    acceptedStartAltitudeReference = RacingAltitudeReference.MSL,
                    finishCrossingTimeMillis = 122_000L,
                    lastTransitionTimeMillis = 122_000L
                )
            ),
            distanceProjector = distanceProjector
        )

        val snapshot = repository.taskPerformance.first()
        val expectedTaskDistance = distanceProjector.startReferenceDistanceMeters(taskSnapshot, acceptedStartFix)!!
        val expectedTaskSpeed = expectedTaskDistance / 120.0

        assertTrue(snapshot.taskDistanceValid)
        assertEquals(expectedTaskDistance, snapshot.taskDistanceMeters, 1e-3)
        assertTrue(snapshot.taskSpeedValid)
        assertEquals(expectedTaskSpeed, snapshot.taskSpeedMs, 1e-3)
        assertTrue(snapshot.taskRemainingDistanceValid)
        assertEquals(0.0, snapshot.taskRemainingDistanceMeters, 1e-6)
        assertTrue(snapshot.taskRemainingTimeValid)
        assertEquals(0L, snapshot.taskRemainingTimeMillis)
    }

    @Test
    fun remaining_time_is_invalid_when_achieved_task_speed_is_not_credible() = runTest {
        val acceptedStartFix = RacingNavigationFix(
            lat = 0.0,
            lon = 0.029,
            timestampMillis = 2_000L,
            altitudeMslMeters = 1_100.0
        )
        val repository = TaskPerformanceRepository(
            flightDataFlow = MutableStateFlow(completeFlightData(longitude = 0.03, monotonicTimestampMillis = 102_000L)),
            taskSnapshotFlow = MutableStateFlow(racingTaskSnapshot()),
            routeFlow = MutableStateFlow(
                NavigationRouteSnapshot(
                    label = "Finish",
                    remainingWaypoints = listOf(
                        NavigationRoutePoint(lat = 0.0, lon = 0.05, label = "Tp1"),
                        NavigationRoutePoint(lat = 0.0, lon = 0.10, label = "Finish")
                    ),
                    valid = true
                )
            ),
            racingStateFlow = MutableStateFlow(
                RacingNavigationState(
                    status = RacingNavigationStatus.IN_PROGRESS,
                    currentLegIndex = 1,
                    acceptedStartTimestampMillis = 2_000L,
                    acceptedStartFix = acceptedStartFix
                )
            ),
            distanceProjector = TaskPerformanceDistanceProjector()
        )

        val snapshot = repository.taskPerformance.first()

        assertTrue(snapshot.taskSpeedValid)
        assertTrue(snapshot.taskSpeedMs < 2.0)
        assertFalse(snapshot.taskRemainingTimeValid)
        assertEquals(TaskPerformanceInvalidReason.STATIC, snapshot.taskRemainingTimeInvalidReason)
    }

    private fun racingTaskSnapshot(
        startAltitudeReference: RacingAltitudeReference = RacingAltitudeReference.MSL
    ): TaskRuntimeSnapshot {
        val startParams = mutableMapOf<String, Any>()
        RacingStartCustomParams(altitudeReference = startAltitudeReference).applyTo(startParams)
        return TaskRuntimeSnapshot(
            taskType = TaskType.RACING,
            task = Task(
                id = "task-1",
                waypoints = listOf(
                    waypoint("start", 0.0, 0.0, WaypointRole.START, startParams),
                    waypoint("tp1", 0.0, 0.05, WaypointRole.TURNPOINT),
                    waypoint("finish", 0.0, 0.10, WaypointRole.FINISH)
                )
            ),
            activeLeg = 1
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
        longitude: Double = 0.03,
        monotonicTimestampMillis: Long = 102_000L,
        navAltitudeMeters: Double = 1_300.0
    ): CompleteFlightData = CompleteFlightData(
        gps = GPSData(
            position = GeoPoint(latitude = 0.0, longitude = longitude),
            altitude = AltitudeM(navAltitudeMeters),
            speed = SpeedMs(30.0),
            bearing = 90.0,
            accuracy = 5f,
            timestamp = monotonicTimestampMillis,
            monotonicTimestampMillis = monotonicTimestampMillis
        ),
        baro = null,
        compass = null,
        baroAltitude = AltitudeM(navAltitudeMeters),
        qnh = PressureHpa(1013.25),
        isQNHCalibrated = true,
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
        timestamp = monotonicTimestampMillis,
        dataQuality = "TEST"
    )

    private fun routeDistanceMeters(
        startLat: Double,
        startLon: Double,
        routePoints: List<NavigationRoutePoint>
    ): Double {
        var totalMeters = 0.0
        var previousLat = startLat
        var previousLon = startLon
        routePoints.forEach { point ->
            totalMeters += RacingGeometryUtils.haversineDistanceMeters(
                previousLat,
                previousLon,
                point.lat,
                point.lon
            )
            previousLat = point.lat
            previousLon = point.lon
        }
        return totalMeters
    }
}
