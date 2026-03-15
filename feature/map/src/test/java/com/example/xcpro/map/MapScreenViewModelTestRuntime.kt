package com.example.xcpro.map

import android.content.Context
import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.calculations.ConfidenceLevel
import com.example.xcpro.MapOrientationManagerFactory
import com.example.xcpro.MapOrientationHeadingPolicy
import com.example.xcpro.MapOrientationSensorInputSource
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
import com.example.xcpro.glider.SpeedBoundsMs
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.glider.GliderRepository
import com.example.xcpro.glide.FinalGlideUseCase
import com.example.xcpro.glide.GlideTargetRepository
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
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
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
import com.example.xcpro.map.AdsbProximityTier
import com.example.xcpro.hawk.HawkVarioUiState
import com.example.xcpro.hawk.HawkVarioUseCase
import com.example.xcpro.map.AdsbConnectionState
import com.example.xcpro.map.ADSB_ICON_SIZE_DEFAULT_PX
import com.example.xcpro.map.ADSB_ICON_SIZE_MAX_PX
import com.example.xcpro.map.ADSB_MAX_DISTANCE_DEFAULT_KM
import com.example.xcpro.map.ADSB_VERTICAL_FILTER_ABOVE_DEFAULT_METERS
import com.example.xcpro.map.ADSB_VERTICAL_FILTER_BELOW_DEFAULT_METERS
import com.example.xcpro.map.AdsbTrafficPreferencesRepository
import com.example.xcpro.map.AdsbTrafficRepository
import com.example.xcpro.map.AdsbTrafficSnapshot
import com.example.xcpro.map.AdsbTrafficUiModel
import com.example.xcpro.map.Icao24
import com.example.xcpro.map.AdsbMetadataEnrichmentUseCase
import com.example.xcpro.map.AircraftMetadata
import com.example.xcpro.map.AircraftMetadataRepository
import com.example.xcpro.map.AircraftMetadataSyncRepository
import com.example.xcpro.map.AircraftMetadataSyncScheduler
import com.example.xcpro.map.MetadataSyncRunResult
import com.example.xcpro.map.MetadataSyncState
import com.example.xcpro.map.OgnConnectionState
import com.example.xcpro.map.OgnDisplayUpdateMode
import com.example.xcpro.map.OGN_ICON_SIZE_DEFAULT_PX
import com.example.xcpro.map.OGN_ICON_SIZE_MAX_PX
import com.example.xcpro.map.OgnTrafficPreferencesRepository
import com.example.xcpro.map.OgnTrafficRepository
import com.example.xcpro.map.OgnTrafficSnapshot
import com.example.xcpro.map.OgnTrafficTarget
import com.example.xcpro.map.OgnThermalHotspot
import com.example.xcpro.map.OgnThermalHotspotState
import com.example.xcpro.map.OgnThermalRepository
import com.example.xcpro.map.OgnGliderTrailRepository
import com.example.xcpro.map.OgnGliderTrailSegment
import com.example.xcpro.map.OgnTrailSelectionPreferencesRepository
import com.example.xcpro.thermalling.ThermallingModeCoordinator
import com.example.xcpro.thermalling.ThermallingModePreferencesRepository
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
    protected val flightDataManagerFactory = FlightDataManagerFactory(context, cardPreferences)
    protected val configurationRepository = ConfigurationRepository(context)
    protected val mapStyleRepository = MapStyleRepository(configurationRepository)
    protected val mapStyleUseCase = MapStyleUseCase(mapStyleRepository)
    protected val unitsRepository = UnitsRepository(context)
    protected val unitsUseCase = UnitsPreferencesUseCase(unitsRepository)
    protected val qnhRepository = FakeQnhRepository()
    protected val qnhUseCase = QnhUseCase(qnhRepository)
    protected val calibrateQnhUseCase = Mockito.mock(CalibrateQnhUseCase::class.java)
    protected val trailSettingsUseCase = MapTrailSettingsUseCase(MapTrailPreferences(context))
    protected val variometerLayoutUseCase =
        VariometerLayoutUseCase(VariometerWidgetRepository(context))
    protected val varioServiceManager = Mockito.mock(VarioServiceManager::class.java)
    protected val unifiedSensorManager = Mockito.mock(UnifiedSensorManager::class.java)
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
    protected val flightDataUseCase = FlightDataUseCase(flightDataRepository)
    protected val windRepository = Mockito.mock(WindSensorFusionRepository::class.java)
    protected lateinit var windStateUseCase: WindStateUseCase
    protected val windStateFlow = MutableStateFlow(com.example.xcpro.weather.wind.model.WindState())
    protected val replayController = Mockito.mock(IgcReplayController::class.java)
    protected val replaySessionFlow = MutableStateFlow(SessionState())
    protected val replayEventsFlow = MutableSharedFlow<ReplayEvent>()
    protected val gliderRepository = Mockito.mock(GliderRepository::class.java)
    protected lateinit var gliderConfigUseCase: GliderConfigUseCase
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

    @Before
    fun resetTrafficPreferences() {
        // Individual tests explicitly drive ADS-B/OGN state via ensure* helpers.
        // Avoid per-test DataStore writes here because they can stall Robolectric workers on Windows.
    }


    protected class SuccessfulWaypointLoader(
        private val waypoints: List<WaypointData>
    ) : WaypointLoader {
        override suspend fun load(): List<WaypointData> = waypoints
    }

    protected class FailingWaypointLoader(
        private val throwable: Throwable
    ) : WaypointLoader {
        override suspend fun load(): List<WaypointData> = throw throwable
    }


    protected fun drainMain() {
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        shadowOf(Looper.getMainLooper()).idle()
    }

    protected suspend fun resetOgnTrafficTogglePreferences() {
        // No-op: tests now use isolated per-view-model DataStores.
    }

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

    protected fun awaitCondition(
        maxIterations: Int = 500,
        condition: () -> Boolean
    ) {
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
        if (backingFile.exists()) {
            backingFile.delete()
        }
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
        val localTaskManager = com.example.xcpro.tasks.TaskManagerCoordinator(
            taskEnginePersistenceService = null,
            racingTaskEngine = null,
            aatTaskEngine = null,
            racingTaskManager = RacingTaskManager(),
            aatTaskManager = AATTaskManager()
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
        val orientationSettingsUseCase = MapOrientationSettingsUseCase(
            orientationSettingsRepository
        )
        val mapUiControllersUseCase = MapUiControllersUseCase(
            flightDataManagerFactory = flightDataManagerFactory,
            orientationManagerFactory = orientationManagerFactory,
            ballastControllerFactory = ballastControllerFactory
        )
        val mapReplayUseCase = MapReplayUseCase(
            taskManager = localTaskManager,
            taskNavigationController = localTaskNavigationController,
            glideTargetRepository = GlideTargetRepository(
                taskManager = localTaskManager,
                taskNavigationController = localTaskNavigationController
            ),
            finalGlideUseCase = FinalGlideUseCase(
                sinkProvider = object : StillAirSinkProvider {
                    override fun sinkAtSpeed(airspeedMs: Double): Double {
                        val centered = airspeedMs - 17.0
                        return 0.55 + (centered * centered * 0.01)
                    }

                    override fun iasBoundsMs(): SpeedBoundsMs =
                        SpeedBoundsMs(minMs = 12.0, maxMs = 25.0)
                }
            ),
            controller = replayController,
            racingReplayLogBuilder = RacingReplayLogBuilder()
        )
        val mapTasksUseCase = MapTasksUseCase(localTaskManager)
        mapFeatureFlags.loadSavedTasksOnInit = false
        val mapFeatureFlagsUseCase = MapFeatureFlagsUseCase(mapFeatureFlags)
        val mapCardPreferencesUseCase = MapCardPreferencesUseCase(cardPreferences)
        val mapVarioPreferencesUseCase = MapVarioPreferencesUseCase(levoVarioPreferencesRepository)
        val thermallingPreferencesRepository = ThermallingModePreferencesRepository(
            newTestPreferencesDataStore("thermalling_mode")
        )
        val thermallingModeUseCase = ThermallingModeRuntimeUseCase(
            preferencesRepository = thermallingPreferencesRepository,
            coordinator = ThermallingModeCoordinator(testClock)
        )
        val ognTrafficRepository = ognRepositoryOverride ?: FakeOgnTrafficRepository()
        val ognThermalRepository = ognThermalRepositoryOverride ?: FakeOgnThermalRepository()
        val ognGliderTrailRepository = FakeOgnGliderTrailRepository()
        val ognTrafficPreferencesRepository = ognPreferencesRepositoryOverride
            ?: OgnTrafficPreferencesRepository(newTestPreferencesDataStore("ogn_traffic"))
        val ognTrailSelectionPreferencesRepository = OgnTrailSelectionPreferencesRepository(
            newTestPreferencesDataStore("ogn_trail_selection")
        )
        val ognTrafficFacade = OgnTrafficUseCase(
            repository = ognTrafficRepository,
            preferencesRepository = ognTrafficPreferencesRepository,
            thermalRepository = ognThermalRepository,
            gliderTrailRepository = ognGliderTrailRepository,
            trailSelectionRepository = ognTrailSelectionPreferencesRepository
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

            override fun start() {
                setEnabled(true)
            }

            override fun stop() {
                setEnabled(false)
            }
        }
        val adsbTrafficPreferencesRepository = adsbPreferencesRepositoryOverride
            ?: AdsbTrafficPreferencesRepository(newTestPreferencesDataStore("adsb_traffic"))
        val metadataRepository = object : AircraftMetadataRepository {
            override val metadataRevision = MutableStateFlow(0L)

            override suspend fun getMetadataFor(icao24s: List<String>): Map<String, AircraftMetadata> {
                return emptyMap()
            }
        }
        val metadataSyncRepository = object : AircraftMetadataSyncRepository {
            override val syncState = MutableStateFlow<MetadataSyncState>(metadataSyncStateIdle())

            override suspend fun onScheduled() {
                syncState.value = metadataSyncStateScheduled()
            }

            override suspend fun onPausedByUser() {
                syncState.value = metadataSyncStatePausedByUser(lastSuccessWallMs = null)
            }

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
        val profileSessionDependencies = MapScreenProfileSessionDependencies(
            mapStyleUseCase = mapStyleUseCase,
            unitsUseCase = unitsUseCase,
            orientationSettingsUseCase = orientationSettingsUseCase,
            gliderConfigUseCase = gliderConfigUseCase,
            variometerLayoutUseCase = variometerLayoutUseCase,
            trailSettingsUseCase = trailSettingsUseCase,
            qnhUseCase = qnhUseCase
        )

        return MapScreenViewModel(
            profileSessionDependencies = profileSessionDependencies,
            mapWaypointsUseCase = mapWaypointsUseCase,
            mapAirspaceUseCase = mapAirspaceUseCase,
            mapWaypointFilesUseCase = mapWaypointFilesUseCase,
            sensorsUseCase = mapSensorsUseCase,
            flightDataUseCase = flightDataUseCase,
            mapUiControllersUseCase = mapUiControllersUseCase,
            windStateUseCase = windStateUseCase,
            mapReplayUseCase = mapReplayUseCase,
            mapTasksUseCase = mapTasksUseCase,
            mapFeatureFlagsUseCase = mapFeatureFlagsUseCase,
            mapCardPreferencesUseCase = mapCardPreferencesUseCase,
            calibrateQnhUseCase = calibrateQnhUseCase,
            mapVarioPreferencesUseCase = mapVarioPreferencesUseCase,
            hawkVarioUseCase = hawkVarioUseCase,
            ognTrafficFacade = ognTrafficFacade,
            adsbTrafficFacade = adsbTrafficFacade,
            adsbMetadataEnrichmentUseCase = adsbMetadataEnrichmentUseCase,
            thermallingModeUseCase = thermallingModeUseCase,
            weGlidePromptBridge = MapScreenWeGlidePromptBridge(
                promptCoordinator = null,
                enqueueUseCase = null,
                notificationController = null
            )
        )
    }

}

