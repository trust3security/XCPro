package com.example.xcpro.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightModeSelection
import com.example.xcpro.MapOrientationManagerFactory
import com.example.xcpro.MapOrientationSettingsRepository
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
import com.example.xcpro.vario.LevoVarioPreferencesRepository
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
import com.example.xcpro.orientation.OrientationClock
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
import com.example.xcpro.variometer.layout.VariometerLayoutUseCase
import com.example.xcpro.variometer.layout.VariometerWidgetRepository
import com.example.xcpro.map.ballast.BallastControllerFactory
import com.example.xcpro.core.time.FakeClock
import com.example.xcpro.ConfigurationRepository
import com.example.xcpro.hawk.HawkVarioUiState
import com.example.xcpro.hawk.HawkVarioUseCase
import com.example.xcpro.adsb.AdsbConnectionState
import com.example.xcpro.adsb.AdsbTrafficPreferencesRepository
import com.example.xcpro.adsb.AdsbTrafficRepository
import com.example.xcpro.adsb.AdsbTrafficSnapshot
import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.ogn.OgnConnectionState
import com.example.xcpro.ogn.OgnTrafficPreferencesRepository
import com.example.xcpro.ogn.OgnTrafficRepository
import com.example.xcpro.ogn.OgnTrafficSnapshot
import com.example.xcpro.ogn.OgnTrafficTarget
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
    private val testClock = FakeClock(monoMs = 0L, wallMs = 0L)
    private val orientationClock = object : OrientationClock {
        override fun nowMonoMs(): Long = testClock.nowMonoMs()
        override fun nowWallMs(): Long = testClock.nowWallMs()
    }
    private val cardPreferences = CardPreferences(context, testClock)
    private val flightDataManagerFactory = FlightDataManagerFactory(context, cardPreferences)
    private val configurationRepository = ConfigurationRepository(context)
    private val mapStyleRepository = MapStyleRepository(configurationRepository)
    private val mapStyleUseCase = MapStyleUseCase(mapStyleRepository)
    private val unitsRepository = UnitsRepository(context)
    private val unitsUseCase = UnitsPreferencesUseCase(unitsRepository)
    private val qnhRepository = FakeQnhRepository()
    private val qnhUseCase = QnhUseCase(qnhRepository)
    private val calibrateQnhUseCase = Mockito.mock(CalibrateQnhUseCase::class.java)
    private val trailSettingsUseCase = MapTrailSettingsUseCase(MapTrailPreferences(context))
    private val variometerLayoutUseCase =
        VariometerLayoutUseCase(VariometerWidgetRepository(context))
    private val varioServiceManager = Mockito.mock(VarioServiceManager::class.java)
    private val unifiedSensorManager = Mockito.mock(UnifiedSensorManager::class.java)
    private val flightStateFlow = MutableStateFlow(FlyingState())
    private val flightStateSource = object : FlightStateSource {
        override val flightState = flightStateFlow
    }
    private val orientationSettingsRepository = MapOrientationSettingsRepository(context)
    private val mapFeatureFlags = MapFeatureFlags()
    private val orientationManagerFactory = MapOrientationManagerFactory(
        orientationDataSourceFactory = OrientationDataSourceFactory(
            unifiedSensorManager = unifiedSensorManager,
            headingResolver = HeadingResolver(),
            flightStateSource = flightStateSource,
            clock = orientationClock,
            featureFlags = mapFeatureFlags
        ),
        settingsRepository = orientationSettingsRepository,
        clock = orientationClock
    )
    private val flightDataRepository = FlightDataRepository()
    private val flightDataUseCase = FlightDataUseCase(flightDataRepository)
    private val windRepository = Mockito.mock(WindSensorFusionRepository::class.java)
    private lateinit var windStateUseCase: WindStateUseCase
    private val windStateFlow = MutableStateFlow(com.example.xcpro.weather.wind.model.WindState())
    private val replayController = Mockito.mock(IgcReplayController::class.java)
    private val replaySessionFlow = MutableStateFlow(SessionState())
    private val replayEventsFlow = MutableSharedFlow<ReplayEvent>()
    private val gliderRepository = Mockito.mock(GliderRepository::class.java)
    private lateinit var gliderConfigUseCase: GliderConfigUseCase
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
    private val ballastControllerFactory =
        BallastControllerFactory(gliderRepository, mainDispatcherRule.dispatcher)
    private val levoVarioPreferencesRepository = LevoVarioPreferencesRepository(context)
    private val hawkVarioUseCase = Mockito.mock(HawkVarioUseCase::class.java)
    private val hawkVarioUiStateFlow = MutableStateFlow(HawkVarioUiState())

    @After
    fun tearDown() {
        // Clean up DataStore artifacts created by the test run.
        context.filesDir.resolve("datastore")?.takeIf { it.exists() }?.deleteRecursively()
    }

    init {
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
        Mockito.`when`(hawkVarioUseCase.hawkVarioUiState).thenReturn(hawkVarioUiStateFlow)
        windStateUseCase = WindStateUseCase(windRepository)
        gliderConfigUseCase = GliderConfigUseCase(gliderRepository)
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

        val viewModel = createViewModel(waypointLoader = loader)

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

        val viewModel = createViewModel(waypointLoader = loader)

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
        override suspend fun load(): List<WaypointData> = waypoints
    }

    private class FailingWaypointLoader(
        private val throwable: Throwable
    ) : WaypointLoader {
        override suspend fun load(): List<WaypointData> = throw throwable
    }


    private fun drainMain() {
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
    }

    private fun createViewModel(
        waypointLoader: WaypointLoader = SuccessfulWaypointLoader(emptyList())
    ): MapScreenViewModel {
        val localTaskManager = com.example.xcpro.tasks.TaskManagerCoordinator(null)
        val localTaskFeatureFlags = TaskFeatureFlags()
        val localTaskNavigationController = TaskNavigationController(
            taskManager = localTaskManager,
            stateStore = RacingNavigationStateStore(),
            advanceState = RacingAdvanceState(),
            engine = RacingNavigationEngine(),
            featureFlags = localTaskFeatureFlags
        )
        val mapWaypointsUseCase = MapWaypointsUseCase(waypointLoader)
        val mapSensorsUseCase = MapSensorsUseCase(varioServiceManager)
        val mapUiControllersUseCase = MapUiControllersUseCase(
            flightDataManagerFactory = flightDataManagerFactory,
            orientationManagerFactory = orientationManagerFactory,
            ballastControllerFactory = ballastControllerFactory
        )
        val mapReplayUseCase = MapReplayUseCase(replayController, RacingReplayLogBuilder())
        val mapTasksUseCase = MapTasksUseCase(localTaskManager, localTaskNavigationController)
        mapFeatureFlags.loadSavedTasksOnInit = false
        val mapFeatureFlagsUseCase = MapFeatureFlagsUseCase(mapFeatureFlags)
        val mapCardPreferencesUseCase = MapCardPreferencesUseCase(cardPreferences)
        val mapVarioPreferencesUseCase = MapVarioPreferencesUseCase(levoVarioPreferencesRepository)
        val ognTrafficRepository = object : OgnTrafficRepository {
            override val targets = MutableStateFlow<List<OgnTrafficTarget>>(emptyList())
            override val snapshot = MutableStateFlow(
                OgnTrafficSnapshot(
                    targets = emptyList(),
                    connectionState = OgnConnectionState.DISCONNECTED,
                    lastError = null,
                    subscriptionCenterLat = null,
                    subscriptionCenterLon = null,
                    receiveRadiusKm = 300,
                    ddbCacheAgeMs = null,
                    reconnectBackoffMs = null,
                    lastReconnectWallMs = null
                )
            )
            override val isEnabled = MutableStateFlow(false)

            override fun setEnabled(enabled: Boolean) {
                isEnabled.value = enabled
            }

            override fun updateCenter(latitude: Double, longitude: Double) = Unit

            override fun start() {
                setEnabled(true)
            }

            override fun stop() {
                setEnabled(false)
            }
        }
        val ognTrafficPreferencesRepository = OgnTrafficPreferencesRepository(context)
        val ognTrafficUseCase = OgnTrafficUseCase(
            repository = ognTrafficRepository,
            preferencesRepository = ognTrafficPreferencesRepository
        )
        val adsbTrafficRepository = object : AdsbTrafficRepository {
            override val targets = MutableStateFlow<List<AdsbTrafficUiModel>>(emptyList())
            override val snapshot = MutableStateFlow(
                AdsbTrafficSnapshot(
                    targets = emptyList(),
                    connectionState = AdsbConnectionState.Disabled,
                    centerLat = null,
                    centerLon = null,
                    receiveRadiusKm = 20,
                    fetchedCount = 0,
                    withinRadiusCount = 0,
                    displayedCount = 0,
                    lastHttpStatus = null,
                    remainingCredits = null,
                    lastPollMonoMs = null,
                    lastSuccessMonoMs = null,
                    lastError = null
                )
            )
            override val isEnabled = MutableStateFlow(false)

            override fun setEnabled(enabled: Boolean) {
                isEnabled.value = enabled
            }

            override fun updateCenter(latitude: Double, longitude: Double) = Unit

            override fun start() {
                setEnabled(true)
            }

            override fun stop() {
                setEnabled(false)
            }
        }
        val adsbTrafficPreferencesRepository = AdsbTrafficPreferencesRepository(context)
        val adsbTrafficUseCase = AdsbTrafficUseCase(
            repository = adsbTrafficRepository,
            preferencesRepository = adsbTrafficPreferencesRepository
        )

        return MapScreenViewModel(
            mapStyleUseCase = mapStyleUseCase,
            unitsUseCase = unitsUseCase,
            mapWaypointsUseCase = mapWaypointsUseCase,
            gliderConfigUseCase = gliderConfigUseCase,
            sensorsUseCase = mapSensorsUseCase,
            flightDataUseCase = flightDataUseCase,
            mapUiControllersUseCase = mapUiControllersUseCase,
            windStateUseCase = windStateUseCase,
            mapReplayUseCase = mapReplayUseCase,
            mapTasksUseCase = mapTasksUseCase,
            mapFeatureFlagsUseCase = mapFeatureFlagsUseCase,
            mapCardPreferencesUseCase = mapCardPreferencesUseCase,
            qnhUseCase = qnhUseCase,
            trailSettingsUseCase = trailSettingsUseCase,
            calibrateQnhUseCase = calibrateQnhUseCase,
            variometerLayoutUseCase = variometerLayoutUseCase,
            mapVarioPreferencesUseCase = mapVarioPreferencesUseCase,
            hawkVarioUseCase = hawkVarioUseCase,
            ognTrafficUseCase = ognTrafficUseCase,
            adsbTrafficUseCase = adsbTrafficUseCase
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


