package com.example.xcpro.map

import com.example.xcpro.common.glider.GliderConfigRepository
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.units.UnitsRepository
import com.example.xcpro.adsb.AdsbTrafficPreferencesRepository
import com.example.xcpro.adsb.AdsbTrafficRepository
import com.example.xcpro.adsb.AdsbTrafficSnapshot
import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.adsb.metadata.domain.AircraftMetadataSyncRepository
import com.example.xcpro.adsb.metadata.domain.AircraftMetadataSyncScheduler
import com.example.xcpro.adsb.metadata.domain.MetadataSyncState
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.sensors.SensorStatus
import com.example.xcpro.ogn.OgnTrafficRepository
import com.example.xcpro.ogn.OgnTrafficPreferencesRepository
import com.example.xcpro.ogn.OgnTrafficSnapshot
import com.example.xcpro.ogn.OgnTrafficTarget
import com.example.xcpro.ogn.OgnGliderTrailRepository
import com.example.xcpro.ogn.OgnGliderTrailSegment
import com.example.xcpro.ogn.OgnDisplayUpdateMode
import com.example.xcpro.ogn.OgnThermalHotspot
import com.example.xcpro.ogn.OgnThermalRepository
import com.example.xcpro.ogn.OgnTrailSelectionPreferencesRepository
import com.example.xcpro.ogn.buildOgnSelectionLookup
import com.example.xcpro.ogn.selectionLookupContainsOgnKey
import com.example.xcpro.qnh.QnhRepository
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.weather.wind.data.WindSensorFusionRepository
import com.example.xcpro.weather.wind.model.WindState
import com.example.xcpro.common.waypoint.WaypointLoader
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.map.trail.domain.TrailUpdateResult
import com.example.xcpro.map.ballast.BallastController
import com.example.xcpro.map.ballast.BallastControllerFactory
import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.TaskNavigationController
import com.example.xcpro.replay.IgcReplayController
import com.example.xcpro.replay.ReplayDisplayPose
import com.example.xcpro.map.replay.RacingReplayLogBuilder
import com.example.xcpro.gestures.TaskGestureCallbacks
import com.example.xcpro.gestures.TaskGestureHandler
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.vario.VarioServiceManager
import com.example.xcpro.vario.LevoVarioPreferencesRepository
import com.example.xcpro.sensors.GpsStatus
import com.example.xcpro.sensors.domain.FlyingState
import com.example.dfcards.CardPreferences
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.MapOrientationManagerFactory
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope

class MapStyleUseCase @Inject constructor(
    private val repository: MapStyleRepository
) {
    fun initialStyle(): String = repository.initialStyle()

    suspend fun saveStyle(style: String) {
        repository.saveStyle(style)
    }
}

class UnitsPreferencesUseCase @Inject constructor(
    private val repository: UnitsRepository
) {
    val unitsFlow: Flow<UnitsPreferences> = repository.unitsFlow
}

class GliderConfigUseCase @Inject constructor(
    private val repository: GliderConfigRepository
) {
    val config = repository.config
}

class FlightDataUseCase @Inject constructor(
    private val repository: FlightDataRepository
) {
    val flightData: StateFlow<CompleteFlightData?> = repository.flightData
    val activeSource: StateFlow<FlightDataRepository.Source> = repository.activeSource
}

class WindStateUseCase @Inject constructor(
    private val repository: WindSensorFusionRepository
) {
    val windState: StateFlow<WindState> = repository.windState
}

class QnhUseCase @Inject constructor(
    private val repository: QnhRepository
) {
    val calibrationState = repository.calibrationState

    suspend fun setManualQnh(hpa: Double) {
        repository.setManualQnh(hpa)
    }
}

class MapWaypointsUseCase @Inject constructor(
    private val waypointLoader: WaypointLoader
) {
    suspend fun loadWaypoints(): List<WaypointData> = waypointLoader.load()
}

class MapSensorsUseCase @Inject constructor(
    private val varioServiceManager: VarioServiceManager
) {
    val gpsStatusFlow: StateFlow<GpsStatus> = varioServiceManager.unifiedSensorManager.gpsStatusFlow
    val flightStateFlow: StateFlow<FlyingState> = varioServiceManager.flightStateSource.flightState

    fun setFlightMode(mode: com.example.xcpro.common.flight.FlightMode) {
        varioServiceManager.setFlightMode(mode)
    }

    suspend fun startSensors(): Boolean = varioServiceManager.start()

    fun stopSensors() {
        varioServiceManager.stop()
    }

    fun sensorStatus(): SensorStatus = varioServiceManager.unifiedSensorManager.getSensorStatus()

    fun isGpsEnabled(): Boolean = varioServiceManager.unifiedSensorManager.isGpsEnabled()
}

class MapVarioPreferencesUseCase @Inject constructor(
    private val repository: LevoVarioPreferencesRepository
) {
    val showWindSpeedOnVario: Flow<Boolean> = repository.config.map { it.showWindSpeedOnVario }
    val showHawkCard: Flow<Boolean> = repository.config.map { it.showHawkCard }
}

data class TaskRenderSnapshot(
    val task: Task,
    val taskType: TaskType,
    val aatEditWaypointIndex: Int?
)

class MapTasksUseCase @Inject constructor(
    private val taskManager: TaskManagerCoordinator
) {
    val taskTypeFlow: StateFlow<TaskType> = taskManager.taskTypeFlow

    suspend fun loadSavedTasks() {
        taskManager.loadSavedTasks()
    }

    fun createGestureHandler(callbacks: TaskGestureCallbacks): TaskGestureHandler {
        return taskManager.createGestureHandler(callbacks)
    }

    fun enterAATEditMode(waypointIndex: Int) {
        taskManager.enterAATEditMode(waypointIndex)
    }

    fun exitAATEditMode() {
        taskManager.exitAATEditMode()
    }

    fun updateAATTargetPoint(index: Int, lat: Double, lon: Double) {
        taskManager.updateAATTargetPoint(index, lat, lon)
    }

    fun clearTask() {
        taskManager.clearTask()
    }

    suspend fun saveTask(taskName: String): Boolean = taskManager.saveTask(taskName)

    fun currentTaskSnapshot(): Task = taskManager.currentTask

    fun currentWaypointCount(): Int = taskManager.currentTask.waypoints.size

    fun taskRenderSnapshot(): TaskRenderSnapshot = TaskRenderSnapshot(
        task = taskManager.currentTask,
        taskType = taskManager.taskType,
        aatEditWaypointIndex = taskManager.getAATEditWaypointIndex()
    )
}

class MapReplayUseCase @Inject constructor(
    private val taskManager: TaskManagerCoordinator,
    private val taskNavigationController: TaskNavigationController,
    private val controller: IgcReplayController,
    private val racingReplayLogBuilder: RacingReplayLogBuilder
) {
    val replaySession: StateFlow<com.example.xcpro.replay.SessionState> = controller.session

    fun getInterpolatedReplayHeadingDeg(nowMs: Long): Double? =
        controller.getInterpolatedReplayHeadingDeg(nowMs)

    fun getInterpolatedReplayPose(nowMs: Long): ReplayDisplayPose? =
        controller.getInterpolatedReplayPose(nowMs)

    internal fun createFlightDataUiAdapter(
        scope: CoroutineScope,
        flightDataFlow: StateFlow<CompleteFlightData?>,
        windStateFlow: StateFlow<WindState>,
        flightStateFlow: StateFlow<FlyingState>,
        hawkVarioUiStateFlow: StateFlow<com.example.xcpro.hawk.HawkVarioUiState>,
        flightDataManager: FlightDataManager,
        mapStateStore: MapStateReader,
        liveDataReady: MutableStateFlow<Boolean>,
        containerReady: MutableStateFlow<Boolean>,
        uiEffects: MutableSharedFlow<MapUiEffect>,
        trailUpdates: MutableStateFlow<TrailUpdateResult?>
    ): FlightDataUiAdapter = FlightDataUiAdapter(
        scope = scope,
        flightDataFlow = flightDataFlow,
        windStateFlow = windStateFlow,
        flightStateFlow = flightStateFlow,
        hawkVarioUiStateFlow = hawkVarioUiStateFlow,
        flightDataManager = flightDataManager,
        mapStateStore = mapStateStore,
        liveDataReady = liveDataReady,
        containerReady = containerReady,
        uiEffects = uiEffects,
        igcReplayController = controller,
        trailUpdates = trailUpdates
    )

    internal fun createReplayCoordinator(
        flightDataFlow: StateFlow<CompleteFlightData?>,
        featureFlags: MapFeatureFlags,
        mapStateStore: MapStateStore,
        mapStateActions: MapStateActions,
        uiEffects: MutableSharedFlow<MapUiEffect>,
        replaySessionState: StateFlow<com.example.xcpro.replay.SessionState>,
        scope: CoroutineScope
    ): MapScreenReplayCoordinator = MapScreenReplayCoordinator(
        taskManager = taskManager,
        taskNavigationController = taskNavigationController,
        flightDataFlow = flightDataFlow,
        igcReplayController = controller,
        racingReplayLogBuilder = racingReplayLogBuilder,
        featureFlags = featureFlags,
        mapStateStore = mapStateStore,
        mapStateActions = mapStateActions,
        uiEffects = uiEffects,
        replaySessionState = replaySessionState,
        scope = scope
    )
}

data class MapUiControllers(
    val flightDataManager: FlightDataManager,
    val orientationManager: MapOrientationManager,
    val ballastController: BallastController
)

class MapUiControllersUseCase @Inject constructor(
    private val flightDataManagerFactory: FlightDataManagerFactory,
    private val orientationManagerFactory: MapOrientationManagerFactory,
    private val ballastControllerFactory: BallastControllerFactory
) {
    fun create(scope: CoroutineScope): MapUiControllers =
        MapUiControllers(
            flightDataManager = flightDataManagerFactory.create(scope),
            orientationManager = orientationManagerFactory.create(scope),
            ballastController = ballastControllerFactory.create(scope)
        )
}

class MapCardPreferencesUseCase @Inject constructor(
    val cardPreferences: CardPreferences
)

class MapFeatureFlagsUseCase @Inject constructor(
    val featureFlags: MapFeatureFlags
)

class OgnTrafficUseCase @Inject constructor(
    private val repository: OgnTrafficRepository,
    private val preferencesRepository: OgnTrafficPreferencesRepository,
    private val thermalRepository: OgnThermalRepository,
    private val gliderTrailRepository: OgnGliderTrailRepository,
    private val trailSelectionRepository: OgnTrailSelectionPreferencesRepository
) {
    val targets: StateFlow<List<OgnTrafficTarget>> = repository.targets
    val snapshot: StateFlow<OgnTrafficSnapshot> = repository.snapshot
    val isStreamingEnabled: StateFlow<Boolean> = repository.isEnabled
    val overlayEnabled: Flow<Boolean> = preferencesRepository.enabledFlow
    val iconSizePx: Flow<Int> = preferencesRepository.iconSizePxFlow
    val displayUpdateMode: Flow<OgnDisplayUpdateMode> = preferencesRepository.displayUpdateModeFlow
    val showSciaEnabled: Flow<Boolean> = preferencesRepository.showSciaEnabledFlow
    val thermalHotspots: StateFlow<List<OgnThermalHotspot>> = thermalRepository.hotspots
    val showThermalsEnabled: Flow<Boolean> = preferencesRepository.showThermalsEnabledFlow
    val gliderTrailSegments: Flow<List<OgnGliderTrailSegment>> = combine(
        gliderTrailRepository.segments,
        trailSelectionRepository.selectedAircraftKeysFlow
    ) { segments, selectedKeys ->
        val lookup = buildOgnSelectionLookup(selectedKeys)
        if (lookup.normalizedSelectedKeys.isEmpty()) {
            emptyList()
        } else {
            segments.filter { segment ->
                selectionLookupContainsOgnKey(
                    lookup = lookup,
                    candidateKey = segment.sourceTargetId
                )
            }
        }
    }.distinctUntilChanged()

    fun setStreamingEnabled(enabled: Boolean) {
        repository.setEnabled(enabled)
    }

    fun updateCenter(latitude: Double, longitude: Double) {
        repository.updateCenter(latitude, longitude)
    }

    fun updateAutoReceiveRadiusContext(
        zoomLevel: Float,
        groundSpeedMs: Double,
        isFlying: Boolean
    ) {
        repository.updateAutoReceiveRadiusContext(
            zoomLevel = zoomLevel,
            groundSpeedMs = groundSpeedMs,
            isFlying = isFlying
        )
    }

    suspend fun setOverlayEnabled(enabled: Boolean) {
        preferencesRepository.setEnabled(enabled)
    }

    suspend fun setIconSizePx(iconSizePx: Int) {
        preferencesRepository.setIconSizePx(iconSizePx)
    }

    suspend fun setDisplayUpdateMode(mode: OgnDisplayUpdateMode) {
        preferencesRepository.setDisplayUpdateMode(mode)
    }

    suspend fun setShowSciaEnabled(enabled: Boolean) {
        preferencesRepository.setShowSciaEnabled(enabled)
    }

    suspend fun setOverlayAndShowSciaEnabled(
        overlayEnabled: Boolean,
        showSciaEnabled: Boolean
    ) {
        preferencesRepository.setOverlayAndSciaEnabled(
            overlayEnabled = overlayEnabled,
            showSciaEnabled = showSciaEnabled
        )
    }

    suspend fun setShowThermalsEnabled(enabled: Boolean) {
        preferencesRepository.setShowThermalsEnabled(enabled)
    }

    fun stop() {
        repository.stop()
    }
}

class AdsbTrafficUseCase @Inject constructor(
    private val repository: AdsbTrafficRepository,
    private val preferencesRepository: AdsbTrafficPreferencesRepository,
    private val metadataSyncRepository: AircraftMetadataSyncRepository,
    private val metadataSyncScheduler: AircraftMetadataSyncScheduler
) {
    val targets: StateFlow<List<AdsbTrafficUiModel>> = repository.targets
    val snapshot: StateFlow<AdsbTrafficSnapshot> = repository.snapshot
    val isStreamingEnabled: StateFlow<Boolean> = repository.isEnabled
    val overlayEnabled: Flow<Boolean> = preferencesRepository.enabledFlow
    val iconSizePx: Flow<Int> = preferencesRepository.iconSizePxFlow
    val maxDistanceKm: Flow<Int> = preferencesRepository.maxDistanceKmFlow
    val verticalAboveMeters: Flow<Double> = preferencesRepository.verticalAboveMetersFlow
    val verticalBelowMeters: Flow<Double> = preferencesRepository.verticalBelowMetersFlow
    val metadataSyncState: StateFlow<MetadataSyncState> = metadataSyncRepository.syncState

    fun setStreamingEnabled(enabled: Boolean) {
        repository.setEnabled(enabled)
    }

    fun clearTargets() {
        repository.clearTargets()
    }

    fun updateCenter(latitude: Double, longitude: Double) {
        repository.updateCenter(latitude, longitude)
    }

    fun updateOwnshipOrigin(latitude: Double, longitude: Double) {
        repository.updateOwnshipOrigin(latitude, longitude)
    }

    fun clearOwnshipOrigin() {
        repository.clearOwnshipOrigin()
    }

    fun updateOwnshipAltitudeMeters(altitudeMeters: Double?) {
        repository.updateOwnshipAltitudeMeters(altitudeMeters)
    }

    fun updateDisplayFilters(
        maxDistanceKm: Int,
        verticalAboveMeters: Double,
        verticalBelowMeters: Double
    ) {
        repository.updateDisplayFilters(
            maxDistanceKm = maxDistanceKm,
            verticalAboveMeters = verticalAboveMeters,
            verticalBelowMeters = verticalBelowMeters
        )
    }

    suspend fun setOverlayEnabled(enabled: Boolean) {
        preferencesRepository.setEnabled(enabled)
        metadataSyncScheduler.onOverlayPreferenceChanged(enabled)
    }

    suspend fun setIconSizePx(iconSizePx: Int) {
        preferencesRepository.setIconSizePx(iconSizePx)
    }

    suspend fun bootstrapMetadataSync() {
        val enabled = overlayEnabled.first()
        metadataSyncScheduler.bootstrapForOverlayPreference(enabled)
    }

    fun stop() {
        repository.stop()
    }
}
