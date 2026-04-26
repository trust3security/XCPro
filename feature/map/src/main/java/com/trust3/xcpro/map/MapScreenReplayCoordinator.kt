package com.trust3.xcpro.map

import com.trust3.xcpro.map.config.MapReplayFeatureFlagPort
import com.trust3.xcpro.replay.SessionState
import com.trust3.xcpro.replay.SessionStatus
import com.trust3.xcpro.sensors.CompleteFlightData
import com.trust3.xcpro.tasks.TaskManagerCoordinator
import com.trust3.xcpro.tasks.TaskNavigationController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach

internal class MapScreenReplayCoordinator(
    private val taskManager: TaskManagerCoordinator,
    private val taskNavigationController: TaskNavigationController,
    private val flightDataFlow: StateFlow<CompleteFlightData?>,
    private val featureFlags: MapReplayFeatureFlagPort,
    private val mapStateActions: MapStateActions,
    private val uiEffects: MutableSharedFlow<MapUiEffect>,
    private val replaySessionState: StateFlow<SessionState>,
    private val scope: CoroutineScope
) {

    private val racingEventDebouncer = RacingNavigationEventDebouncer()
    private val racingFixFlow = flightDataFlow
        .mapNotNull { data -> data?.let(RacingNavigationFixAdapter::toFix) }

    fun start() {
        taskNavigationController.bind(racingFixFlow, scope)
        observeReplayDisplayPoseMode()
        observeRacingNavigationEvents()
    }

    private fun observeReplayDisplayPoseMode() {
        replaySessionState
            .onEach { session ->
                val useRawReplay = featureFlags.useRawReplayPose &&
                    session.selection != null &&
                    session.status != SessionStatus.IDLE
                val mode = if (useRawReplay) {
                    DisplayPoseMode.RAW_REPLAY
                } else {
                    DisplayPoseMode.SMOOTHED
                }
                mapStateActions.setDisplayPoseMode(mode)
            }
            .launchIn(scope)
    }

    private fun observeRacingNavigationEvents() {
        taskNavigationController.racingEvents
            .onEach { event ->
                if (!racingEventDebouncer.shouldEmit(event)) return@onEach
                uiEffects.emit(
                    MapUiEffect.ShowToast(
                        buildRacingEventMessage(taskManager.currentSnapshot(), event)
                    )
                )
            }
            .launchIn(scope)
    }
}
