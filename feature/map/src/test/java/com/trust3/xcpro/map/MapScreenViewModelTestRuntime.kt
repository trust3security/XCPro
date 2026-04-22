package com.trust3.xcpro.map
import android.content.Context
import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightModeSelection
import com.trust3.xcpro.core.flight.calculations.ConfidenceLevel
import com.trust3.xcpro.MapOrientationManagerFactory
import com.trust3.xcpro.MapOrientationHeadingPolicy
import com.trust3.xcpro.MapOrientationSensorInputSource
import com.trust3.xcpro.MapOrientationSettingsRepository
import com.trust3.xcpro.OrientationDataSourceFactory
import com.trust3.xcpro.common.geo.GeoPoint
import com.trust3.xcpro.common.units.AltitudeM
import com.trust3.xcpro.common.units.PressureHpa
import com.trust3.xcpro.common.units.SpeedMs
import com.trust3.xcpro.common.glider.GliderConfig
import com.trust3.xcpro.common.glider.GliderModel
import com.trust3.xcpro.common.units.VerticalSpeedMs
import com.trust3.xcpro.common.waypoint.WaypointData
import com.trust3.xcpro.common.waypoint.WaypointLoader
import com.trust3.xcpro.common.units.UnitsRepository
import com.trust3.xcpro.glider.StillAirSinkProvider
import com.trust3.xcpro.glider.GliderRepository
import com.trust3.xcpro.glide.GlideComputationRepository
import com.trust3.xcpro.glide.GlideTargetProjector
import com.trust3.xcpro.glide.FinalGlideUseCase
import com.trust3.xcpro.livesource.LiveSourceStatePort
import com.trust3.xcpro.livesource.LiveSourceStatus
import com.trust3.xcpro.livesource.ResolvedLiveSourceState
import com.trust3.xcpro.navigation.WaypointNavigationRepository
import com.trust3.xcpro.taskperformance.TaskPerformanceRepository
import com.trust3.xcpro.tasks.navigation.NavigationRouteRepository
import com.trust3.xcpro.tasks.navigation.TaskPerformanceDistanceProjector
import com.trust3.xcpro.qnh.CalibrateQnhUseCase
import com.trust3.xcpro.qnh.QnhCalibrationState
import com.trust3.xcpro.qnh.QnhRepository
import com.trust3.xcpro.qnh.QnhValue
import com.trust3.xcpro.qnh.QnhSource
import com.trust3.xcpro.qnh.QnhConfidence
import com.trust3.xcpro.sensors.AttitudeData
import com.trust3.xcpro.sensors.CompassData
import com.trust3.xcpro.sensors.CompleteFlightData
import com.trust3.xcpro.sensors.FlightStateSource
import com.trust3.xcpro.sensors.GPSData
import com.trust3.xcpro.sensors.GpsStatus
import com.trust3.xcpro.sensors.SensorStatus
import com.trust3.xcpro.sensors.SensorFusionRepository
import com.trust3.xcpro.sensors.UnifiedSensorManager
import com.trust3.xcpro.sensors.domain.FlyingState
import com.trust3.xcpro.vario.LevoVarioPreferencesRepository
import com.trust3.xcpro.weather.wind.data.WindSensorFusionRepository
import com.trust3.xcpro.flightdata.FlightDataRepository
import com.trust3.xcpro.testing.MainDispatcherRule
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import com.trust3.xcpro.map.domain.MapWaypointError
import com.trust3.xcpro.map.config.MapFeatureFlags
import com.trust3.xcpro.map.replay.RacingReplayLogBuilder
import com.trust3.xcpro.airspace.AirspaceUseCase
import com.trust3.xcpro.flightdata.WaypointFilesUseCase
import com.trust3.xcpro.orientation.HeadingResolver
import com.trust3.xcpro.orientation.OrientationClock
import com.trust3.xcpro.replay.IgcReplayController
import com.trust3.xcpro.replay.ReplayEvent
import com.trust3.xcpro.replay.SessionState
import com.trust3.xcpro.map.trail.MapTrailPreferences
import com.trust3.xcpro.map.trail.MapTrailSettingsUseCase
import com.trust3.xcpro.tasks.TaskFeatureFlags
import com.trust3.xcpro.tasks.TaskFlightSurfaceUseCase
import com.trust3.xcpro.tasks.TaskNavigationController
import com.trust3.xcpro.tasks.aat.AATTaskManager
import com.trust3.xcpro.tasks.racing.RacingTaskManager
import com.trust3.xcpro.tasks.racing.navigation.RacingAdvanceState
import com.trust3.xcpro.tasks.racing.navigation.RacingNavigationEngine
import com.trust3.xcpro.tasks.racing.navigation.RacingNavigationStateStore
import com.trust3.xcpro.variometer.layout.VariometerLayoutUseCase
import com.trust3.xcpro.variometer.layout.VariometerWidgetRepository
import com.trust3.xcpro.map.ballast.BallastControllerFactory
import com.trust3.xcpro.core.time.FakeClock
import com.trust3.xcpro.ConfigurationRepository
import com.trust3.xcpro.currentld.PilotCurrentLdRepository
import com.trust3.xcpro.map.AdsbProximityTier
import com.trust3.xcpro.hawk.HawkVarioUiState
import com.trust3.xcpro.hawk.HawkVarioUseCase
import com.trust3.xcpro.map.AdsbConnectionState
import com.trust3.xcpro.map.ADSB_ICON_SIZE_DEFAULT_PX
import com.trust3.xcpro.map.ADSB_ICON_SIZE_MAX_PX
import com.trust3.xcpro.map.ADSB_MAX_DISTANCE_DEFAULT_KM
import com.trust3.xcpro.map.ADSB_VERTICAL_FILTER_ABOVE_DEFAULT_METERS
import com.trust3.xcpro.map.ADSB_VERTICAL_FILTER_BELOW_DEFAULT_METERS
import com.trust3.xcpro.map.AdsbTrafficPreferencesRepository
import com.trust3.xcpro.map.AdsbTrafficRepository
import com.trust3.xcpro.map.AdsbTrafficSnapshot
import com.trust3.xcpro.map.AdsbTrafficUiModel
import com.trust3.xcpro.map.Icao24
import com.trust3.xcpro.map.AdsbMetadataEnrichmentUseCase
import com.trust3.xcpro.map.AircraftMetadata
import com.trust3.xcpro.map.AircraftMetadataRepository
import com.trust3.xcpro.map.AircraftMetadataSyncRepository
import com.trust3.xcpro.map.AircraftMetadataSyncScheduler
import com.trust3.xcpro.map.MetadataSyncRunResult
import com.trust3.xcpro.map.MetadataSyncState
import com.trust3.xcpro.map.OgnConnectionState
import com.trust3.xcpro.map.OgnDisplayUpdateMode
import com.trust3.xcpro.map.OGN_ICON_SIZE_DEFAULT_PX
import com.trust3.xcpro.map.OGN_ICON_SIZE_MAX_PX
import com.trust3.xcpro.map.OgnTrafficPreferencesRepository
import com.trust3.xcpro.map.OgnTrafficRepository
import com.trust3.xcpro.map.OgnTrafficSnapshot
import com.trust3.xcpro.map.OgnTrafficTarget
import com.trust3.xcpro.map.OgnThermalHotspot
import com.trust3.xcpro.map.OgnThermalHotspotState
import com.trust3.xcpro.map.OgnThermalRepository
import com.trust3.xcpro.map.OgnGliderTrailRepository
import com.trust3.xcpro.map.OgnGliderTrailSegment
import com.trust3.xcpro.map.OgnTrailSelectionPreferencesRepository
import com.trust3.xcpro.ogn.OgnSciaStartupResetCoordinator
import com.trust3.xcpro.thermalling.ThermallingModeCoordinator
import com.trust3.xcpro.thermalling.ThermallingModePreferencesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import org.mockito.Mockito
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
@OptIn(ExperimentalCoroutinesApi::class)
abstract class MapScreenViewModelTestBase {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    protected val context: Context = ApplicationProvider.getApplicationContext()
    protected val testClock = FakeClock(monoMs = 0L, wallMs = 0L)
    protected val orientationClock = object : OrientationClock {
        override fun nowMonoMs(): Long = testClock.nowMonoMs()
        override fun nowWallMs(): Long = testClock.nowWallMs()
    }
    protected val cardPreferences = CardPreferences(context, testClock)
    protected val flightDataManagerFactory = FlightDataManagerFactory()
    protected val configurationRepository = ConfigurationRepository(context)
    protected val mapStyleRepository = MapStyleRepository(configurationRepository)
    protected val unitsRepository = UnitsRepository(context)
    protected val qnhRepository = FakeQnhRepository()
    protected val calibrateQnhUseCase = Mockito.mock(CalibrateQnhUseCase::class.java)
    protected val trailSettingsUseCase = MapTrailSettingsUseCase(MapTrailPreferences(context))
    protected val variometerLayoutUseCase =
        VariometerLayoutUseCase(VariometerWidgetRepository(context))
    protected val unifiedSensorManager = Mockito.mock(UnifiedSensorManager::class.java)
    protected val sensorFusionRepository = Mockito.mock(SensorFusionRepository::class.java)
    protected val flightStateFlow = MutableStateFlow(FlyingState())
    protected val flightStateSource = object : FlightStateSource {
        override val flightState = flightStateFlow
    }
    protected val orientationSettingsRepository = MapOrientationSettingsRepository(context)
    protected val mapFeatureFlags = MapFeatureFlags()
    protected val orientationManagerFactory = MapOrientationManagerFactory(
        orientationDataSourceFactory = OrientationDataSourceFactory(
            sensorInputSource = MapOrientationSensorInputSource(unifiedSensorManager),
            headingResolver = HeadingResolver(),
            flightStateSource = flightStateSource,
            clock = orientationClock,
            stationaryHeadingPolicy = MapOrientationHeadingPolicy(mapFeatureFlags)
        ),
        settingsRepository = orientationSettingsRepository,
        clock = orientationClock
    )
    protected val flightDataRepository = FlightDataRepository()
    protected val windRepository = Mockito.mock(WindSensorFusionRepository::class.java)
    protected val windStateFlow = MutableStateFlow(com.trust3.xcpro.weather.wind.model.WindState())
    protected val replayController = Mockito.mock(IgcReplayController::class.java)
    protected val replaySessionFlow = MutableStateFlow(SessionState())
    protected val replayEventsFlow = MutableSharedFlow<ReplayEvent>()
    protected val gliderRepository = Mockito.mock(GliderRepository::class.java)
    protected val gliderConfigFlow = MutableStateFlow(GliderConfig())
    protected val gliderModelFlow = MutableStateFlow<GliderModel?>(null)
    protected val gpsStatusFlow = MutableStateFlow<GpsStatus>(GpsStatus.Searching)
    protected val compassFlow = MutableStateFlow<CompassData?>(null)
    protected val attitudeFlow = MutableStateFlow<AttitudeData?>(null)
    protected val sensorStatus = SensorStatus(
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
    protected val ballastControllerFactory =
        BallastControllerFactory(gliderRepository, mainDispatcherRule.dispatcher)
    protected val levoVarioPreferencesRepository = LevoVarioPreferencesRepository(context)
    protected val hawkVarioUseCase = Mockito.mock(HawkVarioUseCase::class.java)
    protected val hawkVarioUiStateFlow = MutableStateFlow(HawkVarioUiState())
    protected val testPrefsCounter = AtomicInteger(0)

    init {
        Mockito.`when`(replayController.session).thenReturn(replaySessionFlow)
        Mockito.`when`(replayController.events).thenReturn(replayEventsFlow)
        Mockito.`when`(unifiedSensorManager.gpsStatusFlow).thenReturn(gpsStatusFlow)
        Mockito.`when`(unifiedSensorManager.compassFlow).thenReturn(compassFlow)
        Mockito.`when`(unifiedSensorManager.attitudeFlow).thenReturn(attitudeFlow)
        Mockito.`when`(unifiedSensorManager.getSensorStatus()).thenReturn(sensorStatus)
        Mockito.`when`(windRepository.windState).thenReturn(windStateFlow)
        Mockito.`when`(gliderRepository.config).thenReturn(gliderConfigFlow)
        Mockito.`when`(gliderRepository.selectedModel).thenReturn(gliderModelFlow)
        Mockito.`when`(hawkVarioUseCase.hawkVarioUiState).thenReturn(hawkVarioUiStateFlow)
    }

    @Before
    fun resetTrafficPreferences() {
        // Individual tests explicitly drive ADS-B/OGN state via ensure* helpers.
        // Avoid per-test DataStore writes here because they can stall Robolectric workers on Windows.
    }

    protected fun drainMain() {
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        shadowOf(Looper.getMainLooper()).idle()
    }
    protected suspend fun resetOgnTrafficTogglePreferences() = Unit
    protected fun ensureAdsbOverlayDisabled(viewModel: MapScreenViewModel) {
        drainMain()
        if (viewModel.adsbOverlayEnabled.value) {
            viewModel.setMapVisible(true)
            viewModel.onToggleAdsbTraffic()
            awaitCondition { viewModel.adsbOverlayEnabled.value.not() }
            viewModel.setMapVisible(false)
            drainMain()
        }
    }
    protected fun ensureAdsbOverlayEnabled(viewModel: MapScreenViewModel) {
        viewModel.setMapVisible(true)
        drainMain()
        if (viewModel.adsbOverlayEnabled.value.not()) {
            viewModel.onToggleAdsbTraffic()
        }
        awaitCondition(maxIterations = 5_000) { viewModel.adsbOverlayEnabled.value }
        drainMain()
    }
    protected fun awaitCondition(maxIterations: Int = 500, condition: () -> Boolean) {
        repeat(maxIterations) {
            drainMain()
            if (condition()) return
            Thread.sleep(1)
        }
        throw AssertionError("Timed out waiting for condition")
    }
    protected fun newTestPreferencesDataStore(prefix: String): DataStore<Preferences> {
        val uniqueId = testPrefsCounter.incrementAndGet()
        val backingFile = File(context.cacheDir, "${prefix}_${uniqueId}.preferences_pb")
        if (backingFile.exists()) backingFile.delete()
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { backingFile }
        )
    }
    protected fun createViewModel(
        waypointLoader: WaypointLoader = SuccessfulWaypointLoader(emptyList()),
        ognRepositoryOverride: OgnTrafficRepository? = null,
        ognThermalRepositoryOverride: OgnThermalRepository? = null,
        adsbRepositoryOverride: AdsbTrafficRepository? = null,
        ognPreferencesRepositoryOverride: OgnTrafficPreferencesRepository? = null,
        adsbPreferencesRepositoryOverride: AdsbTrafficPreferencesRepository? = null
    ): MapScreenViewModel {
        val localTaskManager = com.trust3.xcpro.tasks.TaskManagerCoordinator(
            taskEnginePersistenceService = null,
            racingTaskEngine = null,
            aatTaskEngine = null,
            racingTaskManager = RacingTaskManager(),
            aatTaskManager = AATTaskManager(),
            coordinatorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        )
        val localTaskFeatureFlags = TaskFeatureFlags()
        val localTaskNavigationController = TaskNavigationController(
            taskManager = localTaskManager,
            stateStore = RacingNavigationStateStore(),
            engine = RacingNavigationEngine(),
            featureFlags = localTaskFeatureFlags
        )
        val stillAirSinkProvider = testStillAirSinkProvider()
        val taskFlightSurfaceUseCase = TaskFlightSurfaceUseCase(
            taskManager = localTaskManager,
            taskNavigationController = localTaskNavigationController
        )
        val localNavigationRouteRepository =
            NavigationRouteRepository(taskManager = localTaskManager, taskNavigationController = localTaskNavigationController)
        val mapAirspaceUseCase = Mockito.mock(AirspaceUseCase::class.java)
        val mapWaypointFilesUseCase = Mockito.mock(WaypointFilesUseCase::class.java)
        val liveSourceStatePort = object : LiveSourceStatePort {
            override val state = MutableStateFlow(
                ResolvedLiveSourceState(status = LiveSourceStatus.PhoneReady)
            )

            override fun refreshAndGetState(): ResolvedLiveSourceState = state.value
        }
        val mapSensorsUseCase = MapSensorsUseCase(
            varioRuntimeControlPort = object : VarioRuntimeControlPort { override fun ensureRunningIfPermitted(): Boolean = true; override fun requestStop() = Unit },
            liveSourceStatePort = liveSourceStatePort,
            flightStateSource = flightStateSource,
            sensorFusionRepository = sensorFusionRepository
        )
        val mapPhoneHealthUseCase = MapPhoneHealthUseCase(
            unifiedSensorManager = unifiedSensorManager,
            liveSourceStatePort = liveSourceStatePort
        )
        val mapUiControllersUseCase = MapUiControllersUseCase(
            flightDataManagerFactory = flightDataManagerFactory,
            orientationManagerFactory = orientationManagerFactory,
            ballastControllerFactory = ballastControllerFactory
        )
        val waypointNavigationRepository = WaypointNavigationRepository(
            flightDataRepository = flightDataRepository, navigationRouteRepository = localNavigationRouteRepository
        )
        val mapReplayUseCase = MapReplayUseCase(
            taskManager = localTaskManager,
            taskNavigationController = localTaskNavigationController,
            glideComputationRepository = GlideComputationRepository(
                flightDataRepository = flightDataRepository,
                windSensorFusionRepository = windRepository,
                taskManager = localTaskManager,
                navigationRouteRepository = localNavigationRouteRepository,
                glideTargetProjector = GlideTargetProjector(),
                finalGlideUseCase = FinalGlideUseCase(
                    sinkProvider = stillAirSinkProvider
                )
            ),
            waypointNavigationRepository = waypointNavigationRepository,
            pilotCurrentLdRepository = createPilotCurrentLdRepositoryForTest(
                flightDataRepository = flightDataRepository,
                windStateFlow = windStateFlow,
                flightStateFlow = flightStateFlow,
                waypointNavigationRepository = waypointNavigationRepository,
                sinkProvider = stillAirSinkProvider
            ),
            taskPerformanceRepository = TaskPerformanceRepository(
                flightDataRepository = flightDataRepository,
                taskManager = localTaskManager,
                navigationRouteRepository = localNavigationRouteRepository,
                taskNavigationController = localTaskNavigationController,
                distanceProjector = TaskPerformanceDistanceProjector()
            ),
            controller = replayController,
            racingReplayLogBuilder = RacingReplayLogBuilder()
        )
        val mapTasksUseCase = MapTasksUseCase(localTaskManager)
        mapFeatureFlags.loadSavedTasksOnInit = false
        val thermallingPreferencesRepository =
            ThermallingModePreferencesRepository(newTestPreferencesDataStore("thermalling_mode"))
        val thermallingModeUseCase = ThermallingModeRuntimeUseCase(
            preferencesRepository = thermallingPreferencesRepository,
            coordinator = ThermallingModeCoordinator(testClock)
        )
        val ognTrafficRepository = ognRepositoryOverride ?: FakeOgnTrafficRepository()
        val ognThermalRepository = ognThermalRepositoryOverride ?: FakeOgnThermalRepository()
        val ognGliderTrailRepository = FakeOgnGliderTrailRepository()
        val ognTrafficPreferencesRepository = ognPreferencesRepositoryOverride
            ?: OgnTrafficPreferencesRepository(
                newTestPreferencesDataStore("ogn_traffic"),
                OgnSciaStartupResetCoordinator.AlreadyReset
            )
        val ognTrailSelectionPreferencesRepository = OgnTrailSelectionPreferencesRepository(
            newTestPreferencesDataStore("ogn_trail_selection"),
            OgnSciaStartupResetCoordinator.AlreadyReset
        )
        val ognTrafficFacade = OgnTrafficUseCase(
            repository = ognTrafficRepository,
            preferencesRepository = ognTrafficPreferencesRepository,
            thermalRepository = ognThermalRepository,
            gliderTrailRepository = ognGliderTrailRepository,
            trailSelectionRepository = ognTrailSelectionPreferencesRepository,
            clock = testClock
        )
        val adsbTrafficRepository = adsbRepositoryOverride ?: object : AdsbTrafficRepository {
            override val targets = MutableStateFlow<List<AdsbTrafficUiModel>>(emptyList())
            override val snapshot = MutableStateFlow(
                AdsbTrafficSnapshot(
                    targets = emptyList(),
                    connectionState = adsbConnectionStateDisabled(),
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
            override fun updateOwnshipMotion(trackDeg: Double?, speedMps: Double?) = Unit
            override fun clearOwnshipOrigin() = Unit
            override fun updateOwnshipAltitudeMeters(altitudeMeters: Double?) = Unit
            override fun updateOwnshipCirclingContext(
                isCircling: Boolean,
                circlingFeatureEnabled: Boolean
            ) = Unit
            override fun updateDisplayFilters(
                maxDistanceKm: Int,
                verticalAboveMeters: Double,
                verticalBelowMeters: Double
            ) = Unit
            override fun reconnectNow() = Unit
            override fun start() = setEnabled(true)
            override fun stop() = setEnabled(false)
        }
        val adsbTrafficPreferencesRepository = adsbPreferencesRepositoryOverride
            ?: AdsbTrafficPreferencesRepository(newTestPreferencesDataStore("adsb_traffic"))
        val metadataRepository = object : AircraftMetadataRepository {
            override val metadataRevision = MutableStateFlow(0L)
            override val lookupProgressRevision = MutableStateFlow(0L)
            override suspend fun getMetadataFor(icao24s: List<String>): Map<String, AircraftMetadata> = emptyMap()
        }
        val metadataSyncRepository = object : AircraftMetadataSyncRepository {
            override val syncState = MutableStateFlow<MetadataSyncState>(metadataSyncStateIdle())
            override suspend fun onScheduled() { syncState.value = metadataSyncStateScheduled() }
            override suspend fun onPausedByUser() { syncState.value = metadataSyncStatePausedByUser(lastSuccessWallMs = null) }
            override suspend fun runSyncNow(): MetadataSyncRunResult = metadataSyncRunResultSkipped()
        }
        val metadataSyncScheduler = object : AircraftMetadataSyncScheduler {
            override suspend fun onOverlayPreferenceChanged(enabled: Boolean) = Unit
            override suspend fun bootstrapForOverlayPreference(overlayEnabled: Boolean) = Unit
        }
        val adsbTrafficFacade = AdsbTrafficUseCase(
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
            mapStyleRepository = mapStyleRepository,
            unitsRepository = unitsRepository,
            orientationSettingsRepository = orientationSettingsRepository,
            gliderConfigRepository = gliderRepository,
            variometerLayoutUseCase = variometerLayoutUseCase,
            trailSettingsUseCase = trailSettingsUseCase,
            qnhRepository = qnhRepository,
            waypointLoader = waypointLoader,
            mapAirspaceUseCase = mapAirspaceUseCase,
            mapWaypointFilesUseCase = mapWaypointFilesUseCase,
            sensorsUseCase = mapSensorsUseCase,
            mapPhoneHealthUseCase = mapPhoneHealthUseCase,
            flightDataRepository = flightDataRepository,
            mapUiControllersUseCase = mapUiControllersUseCase,
            windSensorFusionRepository = windRepository,
            mapReplayUseCase = mapReplayUseCase,
            mapTasksUseCase = mapTasksUseCase,
            taskFlightSurfaceUseCase = taskFlightSurfaceUseCase,
            featureFlags = mapFeatureFlags,
            cardPreferences = cardPreferences,
            calibrateQnhUseCase = calibrateQnhUseCase,
            levoVarioPreferencesRepository = levoVarioPreferencesRepository,
            hawkVarioUseCase = hawkVarioUseCase,
            ognTrafficFacade = ognTrafficFacade,
            adsbTrafficFacade = adsbTrafficFacade,
            adsbMetadataEnrichmentUseCase = adsbMetadataEnrichmentUseCase,
            thermallingModeUseCase = thermallingModeUseCase,
            weGlidePromptBridge = MapScreenWeGlidePromptBridge(promptCoordinator = null, enqueueUseCase = null, notificationController = null)
        )
    }
}

