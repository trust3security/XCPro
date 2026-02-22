package com.example.xcpro.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.calculations.ConfidenceLevel
import com.example.xcpro.MapOrientationManagerFactory
import com.example.xcpro.MapOrientationSettingsRepository
import com.example.xcpro.OrientationDataSourceFactory
import com.example.xcpro.common.geo.GeoPoint
import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.PressureHpa
import com.example.xcpro.common.units.SpeedMs
import com.example.xcpro.common.glider.GliderConfig
import com.example.xcpro.common.glider.GliderModel
import com.example.xcpro.common.units.VerticalSpeedMs
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
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.sensors.FlightStateSource
import com.example.xcpro.sensors.GPSData
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
import com.example.xcpro.airspace.AirspaceUseCase
import com.example.xcpro.flightdata.WaypointFilesUseCase
import com.example.xcpro.orientation.HeadingResolver
import com.example.xcpro.orientation.OrientationClock
import com.example.xcpro.replay.IgcReplayController
import com.example.xcpro.replay.ReplayEvent
import com.example.xcpro.replay.SessionState
import com.example.xcpro.map.trail.MapTrailPreferences
import com.example.xcpro.map.trail.MapTrailSettingsUseCase
import com.example.xcpro.tasks.TaskFeatureFlags
import com.example.xcpro.tasks.TaskNavigationController
import com.example.xcpro.tasks.aat.AATTaskManager
import com.example.xcpro.tasks.racing.RacingTaskManager
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
import com.example.xcpro.adsb.ADSB_ICON_SIZE_DEFAULT_PX
import com.example.xcpro.adsb.ADSB_ICON_SIZE_MAX_PX
import com.example.xcpro.adsb.AdsbTrafficPreferencesRepository
import com.example.xcpro.adsb.AdsbTrafficRepository
import com.example.xcpro.adsb.AdsbTrafficSnapshot
import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.adsb.Icao24
import com.example.xcpro.adsb.metadata.domain.AdsbMetadataEnrichmentUseCase
import com.example.xcpro.adsb.metadata.domain.AircraftMetadata
import com.example.xcpro.adsb.metadata.domain.AircraftMetadataRepository
import com.example.xcpro.adsb.metadata.domain.AircraftMetadataSyncRepository
import com.example.xcpro.adsb.metadata.domain.AircraftMetadataSyncScheduler
import com.example.xcpro.adsb.metadata.domain.MetadataSyncRunResult
import com.example.xcpro.adsb.metadata.domain.MetadataSyncState
import com.example.xcpro.ogn.OgnConnectionState
import com.example.xcpro.ogn.OGN_ICON_SIZE_DEFAULT_PX
import com.example.xcpro.ogn.OGN_ICON_SIZE_MAX_PX
import com.example.xcpro.ogn.OgnTrafficPreferencesRepository
import com.example.xcpro.ogn.OgnTrafficRepository
import com.example.xcpro.ogn.OgnTrafficSnapshot
import com.example.xcpro.ogn.OgnTrafficTarget
import com.example.xcpro.ogn.OgnThermalHotspot
import com.example.xcpro.ogn.OgnThermalHotspotState
import com.example.xcpro.ogn.OgnThermalRepository
import com.example.xcpro.ogn.OgnGliderTrailRepository
import com.example.xcpro.ogn.OgnGliderTrailSegment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun adsbSelection_tracksSelectedIdFromCurrentTargetList() = runBlocking {
        val adsbRepository = FakeAdsbTrafficRepository()
        val viewModel = createViewModel(adsbRepositoryOverride = adsbRepository)
        val id = Icao24.from("abc123") ?: error("invalid test id")

        adsbRepository.targets.value = listOf(sampleAdsbTarget(id))
        drainMain()

        viewModel.onAdsbTargetSelected(id)
        drainMain()

        assertEquals(id, viewModel.selectedAdsbId.value)
        assertEquals(id, viewModel.selectedAdsbTarget.value?.id)
    }

    @Test
    fun adsbSelection_clearsWhenSelectedTargetDisappears() = runBlocking {
        val adsbRepository = FakeAdsbTrafficRepository()
        val viewModel = createViewModel(adsbRepositoryOverride = adsbRepository)
        val id = Icao24.from("abc123") ?: error("invalid test id")

        adsbRepository.targets.value = listOf(sampleAdsbTarget(id))
        drainMain()
        viewModel.onAdsbTargetSelected(id)
        drainMain()
        assertEquals(id, viewModel.selectedAdsbTarget.value?.id)

        adsbRepository.targets.value = emptyList()
        drainMain()

        assertNull(viewModel.selectedAdsbId.value)
        assertNull(viewModel.selectedAdsbTarget.value)
    }

    @Test
    fun onToggleAdsbTraffic_seedsCenterFromCameraSnapshotWhenGpsUnavailable() {
        val adsbRepository = FakeAdsbTrafficRepository()
        val viewModel = createViewModel(adsbRepositoryOverride = adsbRepository)

        viewModel.mapStateActions.updateCameraSnapshot(
            target = MapStateStore.MapPoint(latitude = -35.1234, longitude = 149.1234),
            zoom = 11.0,
            bearing = 0.0
        )
        drainMain()

        viewModel.onToggleAdsbTraffic()
        drainMain()

        assertEquals(-35.1234, adsbRepository.lastCenterLat ?: Double.NaN, 1e-6)
        assertEquals(149.1234, adsbRepository.lastCenterLon ?: Double.NaN, 1e-6)
    }

    @Test
    fun adsbCenter_updatesFromOwnshipGpsLocation() {
        val adsbRepository = FakeAdsbTrafficRepository()
        createViewModel(adsbRepositoryOverride = adsbRepository)
        drainMain()

        flightDataRepository.update(
            buildCompleteFlightData(
                gps = defaultGps(latitude = -34.5000, longitude = 150.5000)
            )
        )
        drainMain()

        assertEquals(-34.5000, adsbRepository.lastCenterLat ?: Double.NaN, 1e-6)
        assertEquals(150.5000, adsbRepository.lastCenterLon ?: Double.NaN, 1e-6)
        assertEquals(-34.5000, adsbRepository.lastOwnshipLat ?: Double.NaN, 1e-6)
        assertEquals(150.5000, adsbRepository.lastOwnshipLon ?: Double.NaN, 1e-6)
    }

    @Test
    fun adsbOwnshipReference_clearsWhenGpsBecomesUnavailable() {
        val adsbRepository = FakeAdsbTrafficRepository()
        createViewModel(adsbRepositoryOverride = adsbRepository)
        drainMain()
        val initialClearCalls = adsbRepository.clearOwnshipOriginCalls

        flightDataRepository.update(
            buildCompleteFlightData(
                gps = defaultGps(latitude = -34.5000, longitude = 150.5000)
            )
        )
        drainMain()
        assertEquals(-34.5000, adsbRepository.lastOwnshipLat ?: Double.NaN, 1e-6)
        assertEquals(150.5000, adsbRepository.lastOwnshipLon ?: Double.NaN, 1e-6)

        flightDataRepository.update(buildCompleteFlightData(gps = null))
        drainMain()

        assertEquals(initialClearCalls + 1, adsbRepository.clearOwnshipOriginCalls)
        assertNull(adsbRepository.lastOwnshipLat)
        assertNull(adsbRepository.lastOwnshipLon)
    }

    @Test
    fun ognCenter_updatesFromOwnshipGpsLocation() {
        val ognRepository = FakeOgnTrafficRepository()
        createViewModel(ognRepositoryOverride = ognRepository)
        drainMain()

        flightDataRepository.update(
            buildCompleteFlightData(
                gps = defaultGps(latitude = -34.5000, longitude = 150.5000)
            )
        )
        drainMain()

        assertEquals(-34.5000, ognRepository.lastCenterLat ?: Double.NaN, 1e-6)
        assertEquals(150.5000, ognRepository.lastCenterLon ?: Double.NaN, 1e-6)
    }

    @Test
    fun ognCenter_doesNotUpdateFromCameraSnapshotWithoutGps() {
        val ognRepository = FakeOgnTrafficRepository()
        val viewModel = createViewModel(ognRepositoryOverride = ognRepository)

        viewModel.mapStateActions.updateCameraSnapshot(
            target = MapStateStore.MapPoint(latitude = -35.1234, longitude = 149.1234),
            zoom = 11.0,
            bearing = 0.0
        )
        drainMain()

        assertNull(ognRepository.lastCenterLat)
        assertNull(ognRepository.lastCenterLon)
    }

    @Test
    fun ognSelection_tracksSelectedTargetFromCurrentTargetList() = runBlocking {
        val ognRepository = FakeOgnTrafficRepository()
        val viewModel = createViewModel(ognRepositoryOverride = ognRepository)
        val id = "OGN123"

        ognRepository.targets.value = listOf(sampleOgnTarget(id))
        drainMain()

        viewModel.onOgnTargetSelected(id)
        drainMain()

        assertEquals(id, viewModel.selectedOgnTarget.value?.id)
    }

    @Test
    fun ognSelection_clearsWhenSelectedTargetDisappears() = runBlocking {
        val ognRepository = FakeOgnTrafficRepository()
        val viewModel = createViewModel(ognRepositoryOverride = ognRepository)
        val id = "OGN123"

        ognRepository.targets.value = listOf(sampleOgnTarget(id))
        drainMain()
        viewModel.onOgnTargetSelected(id)
        drainMain()
        assertEquals(id, viewModel.selectedOgnTarget.value?.id)

        ognRepository.targets.value = emptyList()
        drainMain()

        assertNull(viewModel.selectedOgnTarget.value)
    }

    @Test
    fun selectingOgnTarget_clearsSelectedAdsbTarget() = runBlocking {
        val adsbRepository = FakeAdsbTrafficRepository()
        val ognRepository = FakeOgnTrafficRepository()
        val viewModel = createViewModel(
            adsbRepositoryOverride = adsbRepository,
            ognRepositoryOverride = ognRepository
        )
        val adsbId = Icao24.from("abc123") ?: error("invalid adsb id")
        val ognId = "OGN123"

        adsbRepository.targets.value = listOf(sampleAdsbTarget(adsbId))
        ognRepository.targets.value = listOf(sampleOgnTarget(ognId))
        drainMain()
        viewModel.onAdsbTargetSelected(adsbId)
        drainMain()
        assertEquals(adsbId, viewModel.selectedAdsbTarget.value?.id)

        viewModel.onOgnTargetSelected(ognId)
        drainMain()

        assertEquals(ognId, viewModel.selectedOgnTarget.value?.id)
        assertNull(viewModel.selectedAdsbTarget.value)
    }

    @Test
    fun selectingAdsbTarget_clearsSelectedOgnTarget() = runBlocking {
        val adsbRepository = FakeAdsbTrafficRepository()
        val ognRepository = FakeOgnTrafficRepository()
        val viewModel = createViewModel(
            adsbRepositoryOverride = adsbRepository,
            ognRepositoryOverride = ognRepository
        )
        val adsbId = Icao24.from("abc123") ?: error("invalid adsb id")
        val ognId = "OGN123"

        adsbRepository.targets.value = listOf(sampleAdsbTarget(adsbId))
        ognRepository.targets.value = listOf(sampleOgnTarget(ognId))
        drainMain()
        viewModel.onOgnTargetSelected(ognId)
        drainMain()
        assertEquals(ognId, viewModel.selectedOgnTarget.value?.id)

        viewModel.onAdsbTargetSelected(adsbId)
        drainMain()

        assertEquals(adsbId, viewModel.selectedAdsbTarget.value?.id)
        assertNull(viewModel.selectedOgnTarget.value)
    }

    @Test
    fun dismissSelectedOgnTarget_clearsSelectionState() = runBlocking {
        val ognRepository = FakeOgnTrafficRepository()
        val viewModel = createViewModel(ognRepositoryOverride = ognRepository)
        val id = "OGN123"

        ognRepository.targets.value = listOf(sampleOgnTarget(id))
        drainMain()
        viewModel.onOgnTargetSelected(id)
        drainMain()
        assertEquals(id, viewModel.selectedOgnTarget.value?.id)

        viewModel.dismissSelectedOgnTarget()
        drainMain()

        assertNull(viewModel.selectedOgnTarget.value)
    }

    @Test
    fun selectingThermal_clearsSelectedOgnAndAdsbTargets() = runBlocking {
        val adsbRepository = FakeAdsbTrafficRepository()
        val ognRepository = FakeOgnTrafficRepository()
        val thermalRepository = FakeOgnThermalRepository()
        val viewModel = createViewModel(
            adsbRepositoryOverride = adsbRepository,
            ognRepositoryOverride = ognRepository,
            ognThermalRepositoryOverride = thermalRepository
        )
        val adsbId = Icao24.from("abc123") ?: error("invalid adsb id")
        val ognId = "OGN123"
        val thermalId = "OGN123-thermal-1"

        adsbRepository.targets.value = listOf(sampleAdsbTarget(adsbId))
        ognRepository.targets.value = listOf(sampleOgnTarget(ognId))
        thermalRepository.hotspots.value = listOf(sampleThermalHotspot(thermalId, ognId))
        drainMain()

        viewModel.onAdsbTargetSelected(adsbId)
        drainMain()
        assertEquals(adsbId, viewModel.selectedAdsbTarget.value?.id)

        viewModel.onOgnTargetSelected(ognId)
        drainMain()
        assertEquals(ognId, viewModel.selectedOgnTarget.value?.id)
        assertNull(viewModel.selectedAdsbTarget.value)

        viewModel.onOgnThermalSelected(thermalId)
        drainMain()

        assertEquals(thermalId, viewModel.selectedOgnThermal.value?.id)
        assertNull(viewModel.selectedOgnTarget.value)
        assertNull(viewModel.selectedAdsbTarget.value)
    }

    @Test
    fun thermalSelection_clearsWhenHotspotDisappears() = runBlocking {
        val thermalRepository = FakeOgnThermalRepository()
        val viewModel = createViewModel(ognThermalRepositoryOverride = thermalRepository)
        val thermalId = "OGN123-thermal-1"

        thermalRepository.hotspots.value = listOf(sampleThermalHotspot(thermalId, "OGN123"))
        drainMain()
        viewModel.onOgnThermalSelected(thermalId)
        drainMain()
        assertEquals(thermalId, viewModel.selectedOgnThermal.value?.id)

        thermalRepository.hotspots.value = emptyList()
        drainMain()

        assertNull(viewModel.selectedOgnThermal.value)
    }

    @Test
    fun ognIconSize_defaultsToConfiguredDefaultPx() {
        val viewModel = createViewModel()

        assertEquals(OGN_ICON_SIZE_DEFAULT_PX, viewModel.ognIconSizePx.value)
    }

    @Test
    fun ognIconSize_readsPersistedPreferenceOnInit() = runBlocking {
        val preferencesRepository = OgnTrafficPreferencesRepository(context)
        preferencesRepository.setIconSizePx(OGN_ICON_SIZE_MAX_PX)

        val viewModel = createViewModel()
        drainMain()

        assertEquals(OGN_ICON_SIZE_MAX_PX, viewModel.ognIconSizePx.value)
    }

    @Test
    fun ognGliderTrails_readsPersistedPreferenceOnInit() = runBlocking {
        val preferencesRepository = OgnTrafficPreferencesRepository(context)
        preferencesRepository.setShowGliderTrailsEnabled(true)

        val viewModel = createViewModel()
        drainMain()

        assertEquals(true, viewModel.showOgnGliderTrailsEnabled.value)
    }

    @Test
    fun adsbIconSize_defaultsToConfiguredDefaultPx() {
        val viewModel = createViewModel()

        assertEquals(ADSB_ICON_SIZE_DEFAULT_PX, viewModel.adsbIconSizePx.value)
    }

    @Test
    fun adsbIconSize_readsPersistedPreferenceOnInit() = runBlocking {
        val preferencesRepository = AdsbTrafficPreferencesRepository(context)
        preferencesRepository.setIconSizePx(ADSB_ICON_SIZE_MAX_PX)

        val viewModel = createViewModel()
        drainMain()

        assertEquals(ADSB_ICON_SIZE_MAX_PX, viewModel.adsbIconSizePx.value)
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
        waypointLoader: WaypointLoader = SuccessfulWaypointLoader(emptyList()),
        ognRepositoryOverride: OgnTrafficRepository? = null,
        ognThermalRepositoryOverride: OgnThermalRepository? = null,
        adsbRepositoryOverride: AdsbTrafficRepository? = null
    ): MapScreenViewModel {
        val localTaskManager = com.example.xcpro.tasks.TaskManagerCoordinator(
            taskEnginePersistenceService = null,
            racingTaskEngine = null,
            aatTaskEngine = null,
            racingTaskManager = RacingTaskManager(null),
            aatTaskManager = AATTaskManager(null)
        )
        val localTaskFeatureFlags = TaskFeatureFlags()
        val localTaskNavigationController = TaskNavigationController(
            taskManager = localTaskManager,
            stateStore = RacingNavigationStateStore(),
            advanceState = RacingAdvanceState(),
            engine = RacingNavigationEngine(),
            featureFlags = localTaskFeatureFlags
        )
        val mapWaypointsUseCase = MapWaypointsUseCase(waypointLoader)
        val mapAirspaceUseCase = Mockito.mock(AirspaceUseCase::class.java)
        val mapWaypointFilesUseCase = Mockito.mock(WaypointFilesUseCase::class.java)
        val mapSensorsUseCase = MapSensorsUseCase(varioServiceManager)
        val mapUiControllersUseCase = MapUiControllersUseCase(
            flightDataManagerFactory = flightDataManagerFactory,
            orientationManagerFactory = orientationManagerFactory,
            ballastControllerFactory = ballastControllerFactory
        )
        val mapReplayUseCase = MapReplayUseCase(
            taskManager = localTaskManager,
            taskNavigationController = localTaskNavigationController,
            controller = replayController,
            racingReplayLogBuilder = RacingReplayLogBuilder()
        )
        val mapTasksUseCase = MapTasksUseCase(localTaskManager)
        mapFeatureFlags.loadSavedTasksOnInit = false
        val mapFeatureFlagsUseCase = MapFeatureFlagsUseCase(mapFeatureFlags)
        val mapCardPreferencesUseCase = MapCardPreferencesUseCase(cardPreferences)
        val mapVarioPreferencesUseCase = MapVarioPreferencesUseCase(levoVarioPreferencesRepository)
        val ognTrafficRepository = ognRepositoryOverride ?: FakeOgnTrafficRepository()
        val ognThermalRepository = ognThermalRepositoryOverride ?: FakeOgnThermalRepository()
        val ognGliderTrailRepository = FakeOgnGliderTrailRepository()
        val ognTrafficPreferencesRepository = OgnTrafficPreferencesRepository(context)
        val ognTrafficUseCase = OgnTrafficUseCase(
            repository = ognTrafficRepository,
            preferencesRepository = ognTrafficPreferencesRepository,
            thermalRepository = ognThermalRepository,
            gliderTrailRepository = ognGliderTrailRepository
        )
        val adsbTrafficRepository = adsbRepositoryOverride ?: object : AdsbTrafficRepository {
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

            override fun clearTargets() {
                targets.value = emptyList()
            }

            override fun updateCenter(latitude: Double, longitude: Double) = Unit
            override fun updateOwnshipOrigin(latitude: Double, longitude: Double) = Unit
            override fun clearOwnshipOrigin() = Unit
            override fun updateOwnshipAltitudeMeters(altitudeMeters: Double?) = Unit
            override fun updateDisplayFilters(
                maxDistanceKm: Int,
                verticalAboveMeters: Double,
                verticalBelowMeters: Double
            ) = Unit
            override fun reconnectNow() = Unit

            override fun start() {
                setEnabled(true)
            }

            override fun stop() {
                setEnabled(false)
            }
        }
        val adsbTrafficPreferencesRepository = AdsbTrafficPreferencesRepository(context)
        val metadataRepository = object : AircraftMetadataRepository {
            override val metadataRevision = MutableStateFlow(0L)

            override suspend fun getMetadataFor(icao24s: List<String>): Map<String, AircraftMetadata> {
                return emptyMap()
            }
        }
        val metadataSyncRepository = object : AircraftMetadataSyncRepository {
            override val syncState = MutableStateFlow<MetadataSyncState>(MetadataSyncState.Idle)

            override suspend fun onScheduled() {
                syncState.value = MetadataSyncState.Scheduled
            }

            override suspend fun onPausedByUser() {
                syncState.value = MetadataSyncState.PausedByUser(lastSuccessWallMs = null)
            }

            override suspend fun runSyncNow(): MetadataSyncRunResult = MetadataSyncRunResult.Skipped
        }
        val metadataSyncScheduler = object : AircraftMetadataSyncScheduler {
            override suspend fun onOverlayPreferenceChanged(enabled: Boolean) = Unit
            override suspend fun bootstrapForOverlayPreference(overlayEnabled: Boolean) = Unit
        }
        val adsbTrafficUseCase = AdsbTrafficUseCase(
            repository = adsbTrafficRepository,
            preferencesRepository = adsbTrafficPreferencesRepository,
            metadataSyncRepository = metadataSyncRepository,
            metadataSyncScheduler = metadataSyncScheduler
        )
        val adsbMetadataEnrichmentUseCase = AdsbMetadataEnrichmentUseCase(
            aircraftMetadataRepository = metadataRepository,
            metadataSyncRepository = metadataSyncRepository,
            ioDispatcher = mainDispatcherRule.dispatcher
        )

        return MapScreenViewModel(
            mapStyleUseCase = mapStyleUseCase,
            unitsUseCase = unitsUseCase,
            mapWaypointsUseCase = mapWaypointsUseCase,
            mapAirspaceUseCase = mapAirspaceUseCase,
            mapWaypointFilesUseCase = mapWaypointFilesUseCase,
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
            adsbTrafficUseCase = adsbTrafficUseCase,
            adsbMetadataEnrichmentUseCase = adsbMetadataEnrichmentUseCase
        )
    }

    private fun defaultGps(
        latitude: Double = 46.0,
        longitude: Double = 7.0,
        altitudeMeters: Double = 1000.0,
        speedMs: Double = 0.0,
        bearingDeg: Double = 0.0,
        accuracyMeters: Float = 5f,
        timestampMillis: Long = 1_000L,
        monotonicTimestampMillis: Long = 0L
    ): GPSData = GPSData(
        position = GeoPoint(latitude, longitude),
        altitude = AltitudeM(altitudeMeters),
        speed = SpeedMs(speedMs),
        bearing = bearingDeg,
        accuracy = accuracyMeters,
        timestamp = timestampMillis,
        monotonicTimestampMillis = monotonicTimestampMillis
    )

    private fun buildCompleteFlightData(
        gps: GPSData? = defaultGps(),
        baroAltitudeMeters: Double = 1_000.0,
        verticalSpeedMs: Double = 0.0,
        displayVarioMs: Double = 0.0,
        nettoMs: Double = 0.0,
        displayNettoMs: Double = 0.0,
        nettoValid: Boolean = false,
        baselineDisplayVarioMs: Double = 0.0,
        baselineVarioValid: Boolean = false,
        realIgcVarioMs: Double? = null,
        isCircling: Boolean = false,
        currentThermalValid: Boolean = false,
        thermalAverageValid: Boolean = false,
        timestampMillis: Long = 1_000L
    ): CompleteFlightData {
        return CompleteFlightData(
            gps = gps,
            baro = null,
            compass = null,
            baroAltitude = AltitudeM(baroAltitudeMeters),
            qnh = PressureHpa(1_013.25),
            isQNHCalibrated = false,
            verticalSpeed = VerticalSpeedMs(verticalSpeedMs),
            displayVario = VerticalSpeedMs(displayVarioMs),
            displayNeedleVario = VerticalSpeedMs(0.0),
            displayNeedleVarioFast = VerticalSpeedMs(0.0),
            audioVario = VerticalSpeedMs(0.0),
            baselineVario = VerticalSpeedMs(0.0),
            baselineDisplayVario = VerticalSpeedMs(baselineDisplayVarioMs),
            baselineVarioValid = baselineVarioValid,
            bruttoVario = VerticalSpeedMs(0.0),
            bruttoAverage30s = VerticalSpeedMs(0.0),
            bruttoAverage30sValid = false,
            nettoAverage30s = VerticalSpeedMs(0.0),
            varioSource = "TEST",
            varioValid = true,
            pressureAltitude = AltitudeM(0.0),
            baroGpsDelta = null,
            baroConfidence = ConfidenceLevel.LOW,
            qnhCalibrationAgeSeconds = -1,
            agl = AltitudeM(0.0),
            thermalAverage = VerticalSpeedMs(0.0),
            thermalAverageCircle = VerticalSpeedMs(0.0),
            thermalAverageTotal = VerticalSpeedMs(0.0),
            thermalGain = AltitudeM(0.0),
            thermalGainValid = false,
            currentThermalLiftRate = VerticalSpeedMs(0.0),
            currentThermalValid = currentThermalValid,
            currentLD = 0f,
            netto = VerticalSpeedMs(nettoMs),
            displayNetto = VerticalSpeedMs(displayNettoMs),
            nettoValid = nettoValid,
            trueAirspeed = SpeedMs(0.0),
            indicatedAirspeed = SpeedMs(0.0),
            airspeedSource = "UNKNOWN",
            tasValid = true,
            varioOptimized = VerticalSpeedMs(0.0),
            varioLegacy = VerticalSpeedMs(0.0),
            varioRaw = VerticalSpeedMs(0.0),
            varioGPS = VerticalSpeedMs(0.0),
            varioComplementary = VerticalSpeedMs(0.0),
            realIgcVario = realIgcVarioMs?.let { VerticalSpeedMs(it) },
            teAltitude = AltitudeM(0.0),
            macCready = 0.0,
            macCreadyRisk = 0.0,
            isCircling = isCircling,
            thermalAverageValid = thermalAverageValid,
            timestamp = timestampMillis,
            dataQuality = "TEST"
        )
    }

    private fun sampleAdsbTarget(id: Icao24): AdsbTrafficUiModel = AdsbTrafficUiModel(
        id = id,
        callsign = "TEST01",
        lat = -35.0,
        lon = 149.0,
        altitudeM = 1000.0,
        speedMps = 70.0,
        trackDeg = 180.0,
        climbMps = 0.5,
        ageSec = 2,
        isStale = false,
        distanceMeters = 1500.0,
        bearingDegFromUser = 220.0,
        positionSource = 0,
        category = 3,
        lastContactEpochSec = null
    )

    private fun sampleOgnTarget(id: String): OgnTrafficTarget = OgnTrafficTarget(
        id = id,
        callsign = "OGNTEST",
        destination = "APRS",
        latitude = -35.0,
        longitude = 149.0,
        altitudeMeters = 1200.0,
        trackDegrees = 180.0,
        groundSpeedMps = 40.0,
        verticalSpeedMps = 1.1,
        deviceIdHex = "ABC123",
        signalDb = 12.0,
        displayLabel = id,
        identity = null,
        rawComment = "sample",
        rawLine = "sample line",
        timestampMillis = 1_000L,
        lastSeenMillis = 1_000L
    )

    private fun sampleThermalHotspot(
        id: String,
        sourceTargetId: String
    ): OgnThermalHotspot = OgnThermalHotspot(
        id = id,
        sourceTargetId = sourceTargetId,
        sourceLabel = sourceTargetId,
        latitude = -35.0,
        longitude = 149.0,
        startedAtMonoMs = 1_000L,
        updatedAtMonoMs = 2_000L,
        startAltitudeMeters = 900.0,
        maxAltitudeMeters = 1200.0,
        maxAltitudeAtMonoMs = 2_000L,
        maxClimbRateMps = 2.3,
        averageClimbRateMps = 1.6,
        averageBottomToTopClimbRateMps = 1.2,
        snailColorIndex = 15,
        state = OgnThermalHotspotState.ACTIVE
    )

    private class FakeOgnTrafficRepository : OgnTrafficRepository {
        override val targets = MutableStateFlow<List<OgnTrafficTarget>>(emptyList())
        override val snapshot = MutableStateFlow(
            OgnTrafficSnapshot(
                targets = emptyList(),
                connectionState = OgnConnectionState.DISCONNECTED,
                lastError = null,
                subscriptionCenterLat = null,
                subscriptionCenterLon = null,
                receiveRadiusKm = 150,
                ddbCacheAgeMs = null,
                reconnectBackoffMs = null,
                lastReconnectWallMs = null
            )
        )
        override val isEnabled = MutableStateFlow(false)
        var lastCenterLat: Double? = null
        var lastCenterLon: Double? = null

        override fun setEnabled(enabled: Boolean) {
            isEnabled.value = enabled
        }

        override fun updateCenter(latitude: Double, longitude: Double) {
            lastCenterLat = latitude
            lastCenterLon = longitude
        }

        override fun start() {
            setEnabled(true)
        }

        override fun stop() {
            setEnabled(false)
        }
    }

    private class FakeOgnThermalRepository : OgnThermalRepository {
        override val hotspots = MutableStateFlow<List<OgnThermalHotspot>>(emptyList())
    }

    private class FakeOgnGliderTrailRepository : OgnGliderTrailRepository {
        override val segments = MutableStateFlow<List<OgnGliderTrailSegment>>(emptyList())
    }

    private class FakeAdsbTrafficRepository : AdsbTrafficRepository {
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
        var lastCenterLat: Double? = null
        var lastCenterLon: Double? = null
        var lastOwnshipLat: Double? = null
        var lastOwnshipLon: Double? = null
        var clearOwnshipOriginCalls: Int = 0
        var clearTargetsCalls: Int = 0

        override fun setEnabled(enabled: Boolean) {
            isEnabled.value = enabled
        }

        override fun clearTargets() {
            clearTargetsCalls += 1
            targets.value = emptyList()
        }

        override fun updateCenter(latitude: Double, longitude: Double) {
            lastCenterLat = latitude
            lastCenterLon = longitude
        }

        override fun updateOwnshipOrigin(latitude: Double, longitude: Double) {
            lastOwnshipLat = latitude
            lastOwnshipLon = longitude
        }

        override fun clearOwnshipOrigin() {
            clearOwnshipOriginCalls += 1
            lastOwnshipLat = null
            lastOwnshipLon = null
        }

        override fun updateOwnshipAltitudeMeters(altitudeMeters: Double?) = Unit

        override fun updateDisplayFilters(
            maxDistanceKm: Int,
            verticalAboveMeters: Double,
            verticalBelowMeters: Double
        ) = Unit

        override fun reconnectNow() = Unit

        override fun start() {
            setEnabled(true)
        }

        override fun stop() {
            setEnabled(false)
        }
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


