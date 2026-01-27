package com.example.xcpro.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightModeSelection
import com.example.xcpro.MapOrientationManagerFactory
import com.example.xcpro.OrientationDataSourceFactory
import com.example.xcpro.common.glider.GliderConfig
import com.example.xcpro.common.glider.GliderModel
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.common.waypoint.WaypointLoader
import com.example.xcpro.common.units.UnitsRepository
import com.example.xcpro.glider.GliderRepository
import com.example.xcpro.qnh.CalibrateQnhUseCase
import com.example.xcpro.qnh.QnhCalibrationState
import com.example.xcpro.qnh.QnhRepository
import com.example.xcpro.qnh.QnhValue
import com.example.xcpro.qnh.QnhSource
import com.example.xcpro.qnh.QnhConfidence
import com.example.xcpro.sensors.AttitudeData
import com.example.xcpro.sensors.CompassData
import com.example.xcpro.sensors.FlightStateSource
import com.example.xcpro.sensors.GpsStatus
import com.example.xcpro.sensors.SensorStatus
import com.example.xcpro.sensors.UnifiedSensorManager
import com.example.xcpro.sensors.domain.FlyingState
import com.example.xcpro.vario.VarioServiceManager
import com.example.xcpro.weather.wind.data.WindSensorFusionRepository
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.testing.MainDispatcherRule
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import com.example.xcpro.map.domain.MapWaypointError
import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.map.replay.RacingReplayLogBuilder
import com.example.xcpro.orientation.HeadingResolver
import com.example.xcpro.orientation.SystemOrientationClock
import com.example.xcpro.replay.IgcReplayController
import com.example.xcpro.replay.ReplayEvent
import com.example.xcpro.replay.SessionState
import com.example.xcpro.map.trail.MapTrailPreferences
import com.example.xcpro.map.trail.MapTrailSettingsUseCase
import com.example.xcpro.tasks.TaskFeatureFlags
import com.example.xcpro.tasks.TaskNavigationController
import com.example.xcpro.tasks.racing.navigation.RacingAdvanceState
import com.example.xcpro.tasks.racing.navigation.RacingNavigationEngine
import com.example.xcpro.tasks.racing.navigation.RacingNavigationStateStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.mockito.Mockito
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class MapScreenViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val taskManager = com.example.xcpro.tasks.TaskManagerCoordinator(context)
    private val taskNavigationController = TaskNavigationController(
        taskManager = taskManager,
        stateStore = RacingNavigationStateStore(),
        advanceState = RacingAdvanceState(),
        engine = RacingNavigationEngine(),
        featureFlags = TaskFeatureFlags
    )
    private val cardPreferences = CardPreferences(context)
    private val mapStyleRepository = MapStyleRepository(context)
    private val unitsRepository = UnitsRepository(context)
    private val qnhRepository = FakeQnhRepository()
    private val calibrateQnhUseCase = Mockito.mock(CalibrateQnhUseCase::class.java)
    private val trailSettingsUseCase = MapTrailSettingsUseCase(MapTrailPreferences(context))
    private val varioServiceManager = Mockito.mock(VarioServiceManager::class.java)
    private val unifiedSensorManager = Mockito.mock(UnifiedSensorManager::class.java)
    private val flightStateFlow = MutableStateFlow(FlyingState())
    private val flightStateSource = object : FlightStateSource {
        override val flightState = flightStateFlow
    }
    private val orientationManagerFactory = MapOrientationManagerFactory(
        context = context,
        orientationDataSourceFactory = OrientationDataSourceFactory(
            unifiedSensorManager = unifiedSensorManager,
            headingResolver = HeadingResolver(),
            flightStateSource = flightStateSource
        ),
        clock = SystemOrientationClock()
    )
    private val flightDataRepository = FlightDataRepository()
    private val windRepository = Mockito.mock(WindSensorFusionRepository::class.java)
    private val windStateFlow = MutableStateFlow(com.example.xcpro.weather.wind.model.WindState())
    private val replayController = Mockito.mock(IgcReplayController::class.java)
    private val replaySessionFlow = MutableStateFlow(SessionState())
    private val replayEventsFlow = MutableSharedFlow<ReplayEvent>()
    private val gliderRepository = Mockito.mock(GliderRepository::class.java)
    private val gliderConfigFlow = MutableStateFlow(GliderConfig())
    private val gliderModelFlow = MutableStateFlow<GliderModel?>(null)
    private val gpsStatusFlow = MutableStateFlow<GpsStatus>(GpsStatus.Searching)
    private val compassFlow = MutableStateFlow<CompassData?>(null)
    private val attitudeFlow = MutableStateFlow<AttitudeData?>(null)
    private val sensorStatus = SensorStatus(
        gpsAvailable = false,
        gpsStarted = false,
        baroAvailable = false,
        baroStarted = false,
        compassAvailable = false,
        compassStarted = false,
        accelAvailable = false,
        accelStarted = false,
        rotationAvailable = false,
        rotationStarted = false,
        hasLocationPermissions = false
    )

    @After
    fun tearDown() {
        MapFeatureFlags.loadSavedTasksOnInit = true
        // Clean up DataStore artifacts created by the test run.
        context.filesDir.resolve("datastore")?.takeIf { it.exists() }?.deleteRecursively()
    }

    init {
        MapFeatureFlags.loadSavedTasksOnInit = false
        Mockito.`when`(replayController.session).thenReturn(replaySessionFlow)
        Mockito.`when`(replayController.events).thenReturn(replayEventsFlow)
        Mockito.`when`(varioServiceManager.unifiedSensorManager).thenReturn(unifiedSensorManager)
        Mockito.`when`(varioServiceManager.flightStateSource).thenReturn(flightStateSource)
        Mockito.`when`(unifiedSensorManager.gpsStatusFlow).thenReturn(gpsStatusFlow)
        Mockito.`when`(unifiedSensorManager.compassFlow).thenReturn(compassFlow)
        Mockito.`when`(unifiedSensorManager.attitudeFlow).thenReturn(attitudeFlow)
        Mockito.`when`(unifiedSensorManager.getSensorStatus()).thenReturn(sensorStatus)
        Mockito.`when`(windRepository.windState).thenReturn(windStateFlow)
        Mockito.`when`(gliderRepository.config).thenReturn(gliderConfigFlow)
        Mockito.`when`(gliderRepository.selectedModel).thenReturn(gliderModelFlow)
    }

    @Ignore("GliderRepository + TaskManager persistence hangs under Robolectric until injected abstractions are provided")
    @Test
    fun refreshWaypoints_success_updatesState() = runBlocking {
        val expected = listOf(
            WaypointData(
                name = "Test Field",
                code = "TEST",
                country = "NZ",
                latitude = -45.0,
                longitude = 170.0,
                elevation = "500m",
                style = 4,
                runwayDirection = "09/27",
                runwayLength = "1200m",
                frequency = "118.700",
                description = "Glider base"
            )
        )
        val loader = SuccessfulWaypointLoader(expected)

        val viewModel = MapScreenViewModel(
            appContext = context,
            taskManager = taskManager,
            taskNavigationController = taskNavigationController,
            cardPreferences = cardPreferences,
            mapStyleRepository = mapStyleRepository,
            unitsRepository = unitsRepository,
            waypointLoader = loader,
            gliderRepository = GliderRepository.getInstance(context),
            varioServiceManager = varioServiceManager,
            flightDataRepository = flightDataRepository,
            windRepository = windRepository,
            igcReplayController = replayController,
            racingReplayLogBuilder = RacingReplayLogBuilder(),
            orientationManagerFactory = orientationManagerFactory,
            defaultDispatcher = mainDispatcherRule.dispatcher,
            qnhRepository = qnhRepository,
            trailSettingsUseCase = trailSettingsUseCase,
            calibrateQnhUseCase = calibrateQnhUseCase
        )

        drainMain()

        val state = viewModel.uiState.value
        assertEquals(expected, state.waypoints)
        assertTrue(state.isLoadingWaypoints.not())
        assertNull(state.waypointError)
    }

    @Ignore("GliderRepository + TaskManager persistence hangs under Robolectric until injected abstractions are provided")
    @Test
    fun refreshWaypoints_failure_setsError() = runBlocking {
        val loader = FailingWaypointLoader(IllegalStateException("Failed to read waypoints"))

        val viewModel = MapScreenViewModel(
            appContext = context,
            taskManager = taskManager,
            taskNavigationController = taskNavigationController,
            cardPreferences = cardPreferences,
            mapStyleRepository = mapStyleRepository,
            unitsRepository = unitsRepository,
            waypointLoader = loader,
            gliderRepository = GliderRepository.getInstance(context),
            varioServiceManager = varioServiceManager,
            flightDataRepository = flightDataRepository,
            windRepository = windRepository,
            igcReplayController = replayController,
            racingReplayLogBuilder = RacingReplayLogBuilder(),
            orientationManagerFactory = orientationManagerFactory,
            defaultDispatcher = mainDispatcherRule.dispatcher,
            qnhRepository = qnhRepository,
            trailSettingsUseCase = trailSettingsUseCase,
            calibrateQnhUseCase = calibrateQnhUseCase
        )

        drainMain()

        val state = viewModel.uiState.value
        assertTrue(state.waypoints.isEmpty())
        assertTrue(state.isLoadingWaypoints.not())
        val error = state.waypointError
        assertTrue(error is MapWaypointError.LoadFailed)
        assertEquals("Failed to read waypoints", (error as MapWaypointError.LoadFailed).recoveryHint)
    }

    @Test
    fun setMapStyle_emitsCommandAndUpdatesStore() = runBlocking {
        val viewModel = createViewModel()

        val nextStyle = if (viewModel.mapState.mapStyleName.value == "Satellite") "Topo" else "Satellite"
        val commandDeferred = async(start = CoroutineStart.UNDISPATCHED) { viewModel.mapCommands.first() }
        viewModel.setMapStyle(nextStyle)

        assertEquals(MapCommand.SetStyle(nextStyle), commandDeferred.await())
        assertEquals(nextStyle, viewModel.mapState.mapStyleName.value)
    }

    @Test
    fun setFlightMode_updatesStore() {
        val viewModel = createViewModel()

        viewModel.setFlightMode(com.example.xcpro.common.flight.FlightMode.THERMAL)

        assertEquals(com.example.xcpro.common.flight.FlightMode.THERMAL, viewModel.mapState.currentMode.value)
        assertEquals(FlightModeSelection.THERMAL, viewModel.mapState.currentFlightMode.value)
    }

    @Test
    fun toggleDistanceCircles_updatesStore() {
        val viewModel = createViewModel()

        assertTrue(viewModel.mapState.showDistanceCircles.value.not())

        viewModel.mapStateActions.toggleDistanceCircles()

        assertTrue(viewModel.mapState.showDistanceCircles.value)
    }

    @Test
    fun updateCurrentZoom_updatesStore() {
        val viewModel = createViewModel()

        viewModel.mapStateActions.updateCurrentZoom(14.5f)

        assertEquals(14.5f, viewModel.mapState.currentZoom.value)
    }

    @Test
    fun setTarget_updatesStore() {
        val viewModel = createViewModel()

        val target = MapStateStore.MapPoint(1.23, 4.56)

        viewModel.mapStateActions.setTarget(target, 12.0f)

        assertEquals(target, viewModel.mapState.targetLatLng.value)
        assertEquals(12.0f, viewModel.mapState.targetZoom.value)
    }

    @Test
    fun saveLocation_updatesStore() {
        val viewModel = createViewModel()

        val location = MapStateStore.MapPoint(10.0, -20.0)

        viewModel.mapStateActions.saveLocation(location, 9.0, 180.0)

        assertEquals(location, viewModel.mapState.savedLocation.value)
        assertEquals(9.0, viewModel.mapState.savedZoom.value)
        assertEquals(180.0, viewModel.mapState.savedBearing.value)
    }

    private class SuccessfulWaypointLoader(
        private val waypoints: List<WaypointData>
    ) : WaypointLoader {
        override suspend fun load(context: Context): List<WaypointData> = waypoints
    }

    private class FailingWaypointLoader(
        private val throwable: Throwable
    ) : WaypointLoader {
        override suspend fun load(context: Context): List<WaypointData> = throw throwable
    }


    private fun drainMain() {
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
    }

    private fun createViewModel(
        waypointLoader: WaypointLoader = SuccessfulWaypointLoader(emptyList())
    ): MapScreenViewModel {
        val localTaskManager = com.example.xcpro.tasks.TaskManagerCoordinator(null)
        val localTaskNavigationController = TaskNavigationController(
            taskManager = localTaskManager,
            stateStore = RacingNavigationStateStore(),
            advanceState = RacingAdvanceState(),
            engine = RacingNavigationEngine(),
            featureFlags = TaskFeatureFlags
        )
        return MapScreenViewModel(
            appContext = context,
            taskManager = localTaskManager,
            taskNavigationController = localTaskNavigationController,
            cardPreferences = cardPreferences,
            mapStyleRepository = mapStyleRepository,
            unitsRepository = unitsRepository,
            waypointLoader = waypointLoader,
            gliderRepository = gliderRepository,
            varioServiceManager = varioServiceManager,
            flightDataRepository = flightDataRepository,
            windRepository = windRepository,
            igcReplayController = replayController,
            racingReplayLogBuilder = RacingReplayLogBuilder(),
            orientationManagerFactory = orientationManagerFactory,
            defaultDispatcher = mainDispatcherRule.dispatcher,
            qnhRepository = qnhRepository,
            trailSettingsUseCase = trailSettingsUseCase,
            calibrateQnhUseCase = calibrateQnhUseCase
        )
    }

    private class FakeQnhRepository : QnhRepository {
        private val initialValue = QnhValue(
            hpa = 1013.25,
            source = QnhSource.STANDARD,
            calibratedAtMillis = 0L,
            confidence = QnhConfidence.LOW
        )
        private val qnhFlow = MutableStateFlow(initialValue)
        private val calibrationFlow = MutableStateFlow<QnhCalibrationState>(QnhCalibrationState.Idle)
        override val qnhState = qnhFlow
        override val calibrationState = calibrationFlow

        override suspend fun setManualQnh(hpa: Double) {
            qnhFlow.value = initialValue.copy(hpa = hpa, source = QnhSource.MANUAL)
        }

        override suspend fun resetToStandard() {
            qnhFlow.value = initialValue
        }

        override suspend fun applyAutoQnh(value: QnhValue) {
            qnhFlow.value = value
        }

        override fun updateCalibrationState(state: QnhCalibrationState) {
            calibrationFlow.value = state
        }
    }
}


