package com.example.xcpro.map

import android.util.Log
import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.convertToRealTimeFlightData
import com.example.xcpro.glide.GlideSolution
import com.example.xcpro.hawk.HawkVarioUiState
import com.example.xcpro.map.trail.TrailLength
import com.example.xcpro.map.trail.domain.TrailProcessor
import com.example.xcpro.map.trail.domain.TrailUpdateInput
import com.example.xcpro.map.trail.domain.TrailUpdateResult
import com.example.xcpro.map.trail.TrailSettings
import com.example.xcpro.replay.IgcReplayController
import com.example.xcpro.replay.ReplayEvent
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.sensors.domain.FlyingState
import com.example.xcpro.sensors.domain.LiveWindValidityPolicy
import com.example.xcpro.weather.wind.model.WindState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Binds long-lived map observers to their data sources and pushes results into state flows.
 */
internal class MapScreenObservers(
    private val scope: CoroutineScope,
    private val flightDataFlow: StateFlow<CompleteFlightData?>,
    private val windStateFlow: StateFlow<WindState>,
    private val flightStateFlow: StateFlow<FlyingState>,
    private val hawkVarioUiStateFlow: StateFlow<HawkVarioUiState>,
    private val flightDataManager: FlightDataManager,
    private val mapStateStore: MapStateReader,
    private val trailSettingsFlow: StateFlow<TrailSettings>,
    private val liveDataReady: MutableStateFlow<Boolean>,
    private val containerReady: MutableStateFlow<Boolean>,
    private val uiEffects: MutableSharedFlow<MapUiEffect>,
    private val igcReplayController: IgcReplayController,
    private val glideSolutionFlow: Flow<GlideSolution>,
    private val trailProcessor: TrailProcessor,
    private val trailUpdates: MutableStateFlow<TrailUpdateResult?>
) {

    private var flightStartTimestampMillis: Long? = null
    private var trailEnabledBySettings: Boolean = true

    fun start() {
        observeFlightDataRepository()
        observeSafeContainerSize()
        observeReplayEvents()
        if (BuildConfig.DEBUG) {
            observeReplaySessionDebug()
        }
    }

    private fun observeFlightDataRepository() {
        combine(
            flightDataFlow,
            windStateFlow,
            flightStateFlow,
            hawkVarioUiStateFlow,
            igcReplayController.session.mapReplaySelectionActive()
        ) { data, wind, flightState, hawkState, isReplay ->
            Quintuple(data, wind, flightState, hawkState, isReplay)
        }.combine(glideSolutionFlow) { tuple, glideSolution ->
            Sextuple(
                tuple.first,
                tuple.second,
                tuple.third,
                tuple.fourth,
                tuple.fifth,
                glideSolution
            )
        }.combine(trailSettingsFlow.map { it.length != TrailLength.OFF }) { tuple, trailEnabled ->
            Septuple(
                tuple.first,
                tuple.second,
                tuple.third,
                tuple.fourth,
                tuple.fifth,
                tuple.sixth,
                trailEnabled
            )
        }
            .onEach { (data, wind, flightState, hawkState, isReplay, glideSolution, trailEnabled) ->
                if (data != null) {
                    if (!liveDataReady.value) {
                        liveDataReady.value = true
                    }
                    val sampleClockMillis = data.gps?.timestamp ?: data.timestamp
                    val startMillis = flightStartTimestampMillis ?: sampleClockMillis
                    if (flightStartTimestampMillis == null) {
                        flightStartTimestampMillis = startMillis
                    }
                    val elapsedMillis = (sampleClockMillis - startMillis).coerceAtLeast(0L)
                    val elapsedMinutes = elapsedMillis / 60_000L
                    val hours = elapsedMinutes / 60L
                    val minutes = elapsedMinutes % 60L
                    val formattedFlightTime = "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"

                    val hawkUiState = if (isReplay) HawkVarioUiState() else hawkState
                    val liveData = convertToRealTimeFlightData(
                        completeData = data,
                        windState = wind,
                        isFlying = flightState.isFlying,
                        glideSolution = glideSolution,
                        hawkVarioUiState = hawkUiState,
                        flightTime = formattedFlightTime,
                        lastUpdateTimeMillis = sampleClockMillis
                    ).applyWindState(wind)
                    flightDataManager.updateLiveFlightData(liveData)

                    if (!trailEnabled) {
                        if (trailEnabledBySettings) {
                            trailProcessor.resetAll()
                            trailEnabledBySettings = false
                        }
                        if (trailUpdates.value != null) {
                            trailUpdates.value = null
                        }
                    } else {
                        trailEnabledBySettings = true
                        val trailResult = trailProcessor.update(
                            TrailUpdateInput(
                                data = data,
                                windState = wind,
                                isFlying = flightState.isFlying,
                                isReplay = isReplay
                            )
                        )
                        if (trailResult != null) {
                            trailUpdates.value = trailResult
                        }
                    }
                } else {
                    flightStartTimestampMillis = null
                    flightDataManager.updateLiveFlightData(null)
                    trailUpdates.value = null
                }
            }
            .launchIn(scope)
    }

    private fun observeSafeContainerSize() {
        mapStateStore.safeContainerSize
            .onEach { size ->
                if (!containerReady.value && size.widthPx > 0 && size.heightPx > 0) {
                    containerReady.value = true
                }
            }
            .launchIn(scope)
    }

    private fun observeReplayEvents() {
        igcReplayController.events
            .onEach { event ->
                when (event) {
                    is ReplayEvent.Completed ->
                        uiEffects.emit(MapUiEffect.ShowToast("Replay finished (${event.samples} samples)"))
                    is ReplayEvent.Failed -> {
                        if (event.throwable is CancellationException) {
                            uiEffects.emit(MapUiEffect.ShowToast("Replay stopped."))
                            return@onEach
                        }
                        Log.e("MapScreenViewModel", "Replay failed", event.throwable)
                        uiEffects.emit(
                            MapUiEffect.ShowToast("Replay failed: ${event.throwable.message ?: "Unknown error"}")
                        )
                    }
                    ReplayEvent.Cancelled -> {
                        uiEffects.emit(MapUiEffect.ShowToast("Replay stopped."))
                    }
                }
            }
            .launchIn(scope)
    }

    private fun observeReplaySessionDebug() {
        igcReplayController.session
            .onEach { session ->
                Log.i(
                    "MapScreenViewModel",
                    "REPLAY_SESSION_UI status=${session.status} selection=${session.selection} " +
                        "elapsed=${session.elapsedMillis} dur=${session.durationMillis}"
                )
            }
            .launchIn(scope)
    }

    private fun RealTimeFlightData.applyWindState(windState: WindState): RealTimeFlightData {
        val windConfidence = windState.confidence.coerceIn(0.0, 1.0)
        val hasWind = LiveWindValidityPolicy.isLiveWindUsable(
            windState = windState,
            airspeedSourceLabel = airspeedSource
        )
        if (!hasWind) {
            return copy(
                windConfidence = windConfidence,
                windValid = false
            )
        }

        val vector = windState.vector ?: return copy(windConfidence = windConfidence, windValid = false)
        val directionFrom = ((vector.directionFromDeg % 360.0) + 360.0) % 360.0
        val windAgeSeconds = if (windState.lastUpdatedMillis > 0L) {
            val ageMs = (timestamp - windState.lastUpdatedMillis).coerceAtLeast(0L)
            ageMs / 1000L
        } else {
            -1L
        }

        return copy(
            windSpeed = vector.speed.toFloat(),
            windDirection = directionFrom.toFloat(),
            windQuality = windState.quality,
            windConfidence = windConfidence,
            windValid = true,
            windSource = windState.source.name,
            windHeadwind = windState.headwind,
            windCrosswind = windState.crosswind,
            windAgeSeconds = windAgeSeconds
        )
    }
}

private data class Quintuple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)

private data class Sextuple<A, B, C, D, E, F>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
    val sixth: F
)

private data class Septuple<A, B, C, D, E, F, G>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
    val sixth: F,
    val seventh: G
)
