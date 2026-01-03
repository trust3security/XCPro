package com.example.xcpro.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightModeSelection
import com.example.xcpro.common.glider.GliderConfig
import com.example.xcpro.common.glider.GliderModel
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.common.waypoint.WaypointLoader
import com.example.xcpro.common.units.UnitsRepository
import com.example.xcpro.glider.GliderRepository
import com.example.xcpro.sensors.AttitudeData
import com.example.xcpro.sensors.CompassData
import com.example.xcpro.sensors.GpsStatus
import com.example.xcpro.sensors.SensorStatus
import com.example.xcpro.sensors.UnifiedSensorManager
import com.example.xcpro.vario.VarioServiceManager
import com.example.xcpro.weather.wind.data.WindRepository
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import com.example.xcpro.map.domain.MapWaypointError
import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.replay.IgcReplayController
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
    private val cardPreferences = CardPreferences(context)
    private val mapStyleRepository = MapStyleRepository(context)
    private val unitsRepository = UnitsRepository(context)
    private val qnhPreferencesRepository = QnhPreferencesRepository(context)
    private val varioServiceManager = Mockito.mock(VarioServiceManager::class.java)
    private val unifiedSensorManager = Mockito.mock(UnifiedSensorManager::class.java)
    private val flightDataRepository = FlightDataRepository()
    private val windRepository = Mockito.mock(WindRepository::class.java)
    private val windStateFlow = MutableStateFlow(com.example.xcpro.weather.wind.data.WindState())
    private val replayController = Mockito.mock(IgcReplayController::class.java)
    private val replaySessionFlow = MutableStateFlow(IgcReplayController.SessionState())
    private val replayEventsFlow = MutableSharedFlow<IgcReplayController.ReplayEvent>()
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
    fun refreshWaypoints_success_updatesState() = runTest(mainDispatcherRule.dispatcher) {
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
            cardPreferences = cardPreferences,
            mapStyleRepository = mapStyleRepository,
            unitsRepository = unitsRepository,
            qnhPreferencesRepository = qnhPreferencesRepository,
            waypointLoader = loader,
            gliderRepository = GliderRepository.getInstance(context),
            varioServiceManager = varioServiceManager,
            flightDataRepository = flightDataRepository,
            windRepository = windRepository,
            igcReplayController = replayController,
            defaultDispatcher = mainDispatcherRule.dispatcher
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(expected, state.waypoints)
        assertTrue(state.isLoadingWaypoints.not())
        assertNull(state.waypointError)
    }

    @Ignore("GliderRepository + TaskManager persistence hangs under Robolectric until injected abstractions are provided")
    @Test
    fun refreshWaypoints_failure_setsError() = runTest(mainDispatcherRule.dispatcher) {
        val loader = FailingWaypointLoader(IllegalStateException("Failed to read waypoints"))

        val viewModel = MapScreenViewModel(
            appContext = context,
            taskManager = taskManager,
            cardPreferences = cardPreferences,
            mapStyleRepository = mapStyleRepository,
            unitsRepository = unitsRepository,
            qnhPreferencesRepository = qnhPreferencesRepository,
            waypointLoader = loader,
            gliderRepository = GliderRepository.getInstance(context),
            varioServiceManager = varioServiceManager,
            flightDataRepository = flightDataRepository,
            windRepository = windRepository,
            igcReplayController = replayController,
            defaultDispatcher = mainDispatcherRule.dispatcher
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.waypoints.isEmpty())
        assertTrue(state.isLoadingWaypoints.not())
        val error = state.waypointError
        assertTrue(error is MapWaypointError.LoadFailed)
        assertEquals("Failed to read waypoints", (error as MapWaypointError.LoadFailed).recoveryHint)
    }

    @Test
    fun setMapStyle_emitsCommandAndUpdatesStore() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val commandDeferred = async { viewModel.mapCommands.first() }
        viewModel.setMapStyle("Satellite")

        assertEquals(MapCommand.SetStyle("Satellite"), commandDeferred.await())
        assertEquals("Satellite", viewModel.mapState.mapStyleName.value)
    }

    @Test
    fun setFlightMode_updatesStore() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setFlightMode(com.example.xcpro.common.flight.FlightMode.THERMAL)

        assertEquals(com.example.xcpro.common.flight.FlightMode.THERMAL, viewModel.mapState.currentMode.value)
        assertEquals(FlightModeSelection.THERMAL, viewModel.mapState.currentFlightMode.value)
    }

    @Test
    fun toggleDistanceCircles_updatesStore() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.mapState.showDistanceCircles.value.not())

        viewModel.toggleDistanceCircles()

        assertTrue(viewModel.mapState.showDistanceCircles.value)
    }

    @Test
    fun updateCurrentZoom_updatesStore() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateCurrentZoom(14.5f)

        assertEquals(14.5f, viewModel.mapState.currentZoom.value)
    }

    @Test
    fun setTarget_updatesStore() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val target = MapStateStore.MapPoint(1.23, 4.56)

        viewModel.setTarget(target, 12.0f)

        assertEquals(target, viewModel.mapState.targetLatLng.value)
        assertEquals(12.0f, viewModel.mapState.targetZoom.value)
    }

    @Test
    fun saveLocation_updatesStore() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val location = MapStateStore.MapPoint(10.0, -20.0)

        viewModel.saveLocation(location, 9.0, 180.0)

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

    private fun createViewModel(
        waypointLoader: WaypointLoader = SuccessfulWaypointLoader(emptyList())
    ): MapScreenViewModel = MapScreenViewModel(
        appContext = context,
        taskManager = com.example.xcpro.tasks.TaskManagerCoordinator(null),
        cardPreferences = cardPreferences,
        mapStyleRepository = mapStyleRepository,
        unitsRepository = unitsRepository,
        qnhPreferencesRepository = qnhPreferencesRepository,
        waypointLoader = waypointLoader,
        gliderRepository = gliderRepository,
        varioServiceManager = varioServiceManager,
        flightDataRepository = flightDataRepository,
        windRepository = windRepository,
        igcReplayController = replayController,
        defaultDispatcher = mainDispatcherRule.dispatcher
    )
}
