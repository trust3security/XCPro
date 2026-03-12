package com.example.xcpro.map

import com.example.xcpro.common.glider.GliderConfigRepository
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.units.UnitsRepository
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.qnh.QnhRepository
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.sensors.domain.FlyingState
import com.example.xcpro.weather.wind.data.WindSensorFusionRepository
import com.example.xcpro.weather.wind.model.WindState
import com.example.xcpro.common.waypoint.WaypointLoader
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.map.trail.domain.TrailUpdateResult
import com.example.xcpro.map.trail.TrailSettings
import com.example.xcpro.map.ballast.BallastController
import com.example.xcpro.map.ballast.BallastControllerFactory
import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.glide.FinalGlideUseCase
import com.example.xcpro.glide.GlideTargetRepository
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.TaskNavigationController
import com.example.xcpro.replay.IgcReplayController
import com.example.xcpro.replay.ReplayDisplayPose
import com.example.xcpro.map.replay.RacingReplayLogBuilder
import com.example.xcpro.thermalling.ThermallingModeAction
import com.example.xcpro.thermalling.ThermallingModeCoordinator
import com.example.xcpro.thermalling.ThermallingModeInput
import com.example.xcpro.thermalling.ThermallingModePreferencesRepository
import com.example.xcpro.thermalling.ThermallingModeSettings
import com.example.xcpro.thermalling.ThermallingModeState
import com.example.xcpro.vario.LevoVarioPreferencesRepository
import com.example.dfcards.CardPreferences
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.MapOrientationManagerFactory
import com.example.xcpro.MapOrientationSettingsRepository
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
    fun setActiveProfileId(profileId: String) {
        repository.setActiveProfileId(profileId)
    }

    fun initialStyle(): String = repository.initialStyle()

    fun readProfileStyle(profileId: String): String = repository.readProfileStyle(profileId)

    suspend fun saveStyle(style: String) {
        repository.saveStyle(style)
    }

    suspend fun writeProfileStyle(profileId: String, style: String) {
        repository.writeProfileStyle(profileId, style)
    }

    suspend fun clearProfile(profileId: String) {
        repository.clearProfile(profileId)
    }
}

class UnitsPreferencesUseCase @Inject constructor(
    private val repository: UnitsRepository
) {
    val unitsFlow: Flow<UnitsPreferences> = repository.unitsFlow

    fun setActiveProfileId(profileId: String) {
        repository.setActiveProfileId(profileId)
    }
}

class GliderConfigUseCase @Inject constructor(
    private val repository: GliderConfigRepository
) {
    val config = repository.config

    fun setActiveProfileId(profileId: String) {
        repository.setActiveProfileId(profileId)
    }

    fun clearProfile(profileId: String) {
        repository.clearProfile(profileId)
    }
}

class FlightDataUseCase @Inject constructor(
    private val repository: FlightDataRepository
) {
    val flightData: StateFlow<CompleteFlightData?> = repository.flightData
    val activeSource: StateFlow<FlightDataRepository.Source> = repository.activeSource
}

interface ThermallingModeRuntimeController {
    fun update(input: ThermallingModeInput): List<ThermallingModeAction>
    fun onUserZoomChanged(currentZoom: Float, settings: ThermallingModeSettings)
    fun state(): ThermallingModeState
    fun reset()
}

class ThermallingModeRuntimeUseCase @Inject constructor(
    private val preferencesRepository: ThermallingModePreferencesRepository,
    private val coordinator: ThermallingModeCoordinator
) : ThermallingModeRuntimeController {
    val settingsFlow: Flow<ThermallingModeSettings> = preferencesRepository.settingsFlow

    override fun update(input: ThermallingModeInput): List<ThermallingModeAction> =
        coordinator.update(input)

    override fun onUserZoomChanged(currentZoom: Float, settings: ThermallingModeSettings) {
        coordinator.onUserZoomChanged(currentZoom, settings)
    }

    override fun state(): ThermallingModeState = coordinator.state()

    override fun reset() {
        coordinator.reset()
    }
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

    suspend fun setActiveProfileId(profileId: String) {
        repository.setActiveProfileId(profileId)
    }

    suspend fun setManualQnh(hpa: Double) {
        repository.setManualQnh(hpa)
    }
}

class MapWaypointsUseCase @Inject constructor(
    private val waypointLoader: WaypointLoader
) {
    suspend fun loadWaypoints(): List<WaypointData> = waypointLoader.load()
}

class MapVarioPreferencesUseCase @Inject constructor(
    private val repository: LevoVarioPreferencesRepository
) {
    val showWindSpeedOnVario: Flow<Boolean> = repository.config.map { it.showWindSpeedOnVario }
    val showHawkCard: Flow<Boolean> = repository.config.map { it.showHawkCard }
}

class MapOrientationSettingsUseCase @Inject constructor(
    private val repository: MapOrientationSettingsRepository
) {
    fun setActiveProfileId(profileId: String) {
        repository.setActiveProfileId(profileId)
    }
}

class MapReplayUseCase @Inject constructor(
    private val taskManager: TaskManagerCoordinator,
    private val taskNavigationController: TaskNavigationController,
    private val glideTargetRepository: GlideTargetRepository,
    private val finalGlideUseCase: FinalGlideUseCase,
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
        trailSettingsFlow: StateFlow<TrailSettings>,
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
        trailSettingsFlow = trailSettingsFlow,
        liveDataReady = liveDataReady,
        containerReady = containerReady,
        uiEffects = uiEffects,
        igcReplayController = controller,
        glideTargetFlow = glideTargetRepository.finishTarget,
        finalGlideUseCase = finalGlideUseCase,
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
