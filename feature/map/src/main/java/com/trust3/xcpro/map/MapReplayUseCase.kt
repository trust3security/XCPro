package com.trust3.xcpro.map

import com.trust3.xcpro.currentld.PilotCurrentLdRepository
import com.trust3.xcpro.glide.GlideComputationRepository
import com.trust3.xcpro.hawk.HawkVarioUiState
import com.trust3.xcpro.map.config.MapFeatureFlags
import com.trust3.xcpro.map.replay.RacingReplayLogBuilder
import com.trust3.xcpro.map.replay.SyntheticThermalReplayLogBuilder
import com.trust3.xcpro.map.replay.SyntheticThermalReplayMode
import com.trust3.xcpro.map.trail.TrailSettings
import com.trust3.xcpro.map.trail.domain.TrailUpdateResult
import com.trust3.xcpro.navigation.WaypointNavigationRepository
import com.trust3.xcpro.replay.IgcReplayController
import com.trust3.xcpro.replay.ReplayDisplayPose
import com.trust3.xcpro.replay.SessionState
import com.trust3.xcpro.sensors.CompleteFlightData
import com.trust3.xcpro.sensors.domain.FlyingState
import com.trust3.xcpro.taskperformance.TaskPerformanceRepository
import com.trust3.xcpro.tasks.TaskManagerCoordinator
import com.trust3.xcpro.tasks.TaskNavigationController
import com.trust3.xcpro.weather.wind.model.WindState
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MapReplayUseCase @Inject constructor(
    private val taskManager: TaskManagerCoordinator,
    private val taskNavigationController: TaskNavigationController,
    private val glideComputationRepository: GlideComputationRepository,
    private val waypointNavigationRepository: WaypointNavigationRepository,
    private val pilotCurrentLdRepository: PilotCurrentLdRepository,
    private val taskPerformanceRepository: TaskPerformanceRepository,
    private val controller: IgcReplayController,
    private val racingReplayLogBuilder: RacingReplayLogBuilder,
    private val syntheticThermalReplayLogBuilder: SyntheticThermalReplayLogBuilder
) {
    val replaySession: StateFlow<SessionState> = controller.session

    fun getInterpolatedReplayHeadingDeg(nowMs: Long): Double? =
        controller.getInterpolatedReplayHeadingDeg(nowMs)

    fun getInterpolatedReplayPose(nowMs: Long): ReplayDisplayPose? =
        controller.getInterpolatedReplayPose(nowMs)

    internal fun createFlightDataUiAdapter(
        scope: CoroutineScope,
        flightDataFlow: StateFlow<CompleteFlightData?>,
        windStateFlow: StateFlow<WindState>,
        flightStateFlow: StateFlow<FlyingState>,
        hawkVarioUiStateFlow: StateFlow<HawkVarioUiState>,
        flightDataManager: FlightDataManager,
        mapStateStore: MapStateReader,
        trailSettingsFlow: StateFlow<TrailSettings>,
        syntheticReplayMode: StateFlow<SyntheticThermalReplayMode>,
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
        syntheticReplayMode = syntheticReplayMode,
        liveDataReady = liveDataReady,
        containerReady = containerReady,
        uiEffects = uiEffects,
        igcReplayController = controller,
        glideSolutionFlow = glideComputationRepository.glide,
        waypointNavigationFlow = waypointNavigationRepository.waypointNavigation,
        pilotCurrentLdFlow = pilotCurrentLdRepository.pilotCurrentLd,
        taskPerformanceFlow = taskPerformanceRepository.taskPerformance,
        trailUpdates = trailUpdates
    )

    internal fun createReplayCoordinator(
        flightDataFlow: StateFlow<CompleteFlightData?>,
        featureFlags: MapFeatureFlags,
        mapStateStore: MapStateStore,
        mapStateActions: MapStateActions,
        syntheticReplayMode: MutableStateFlow<SyntheticThermalReplayMode>,
        uiEffects: MutableSharedFlow<MapUiEffect>,
        replaySessionState: StateFlow<SessionState>,
        scope: CoroutineScope
    ): MapScreenReplayCoordinator = MapScreenReplayCoordinator(
        taskManager = taskManager,
        taskNavigationController = taskNavigationController,
        flightDataFlow = flightDataFlow,
        igcReplayController = controller,
        racingReplayLogBuilder = racingReplayLogBuilder,
        syntheticThermalReplayLogBuilder = syntheticThermalReplayLogBuilder,
        featureFlags = featureFlags,
        mapStateStore = mapStateStore,
        mapStateActions = mapStateActions,
        syntheticReplayMode = syntheticReplayMode,
        uiEffects = uiEffects,
        replaySessionState = replaySessionState,
        scope = scope
    )
}
