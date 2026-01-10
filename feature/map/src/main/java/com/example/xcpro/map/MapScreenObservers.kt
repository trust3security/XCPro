package com.example.xcpro.map

import android.util.Log
import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.convertToRealTimeFlightData
import com.example.xcpro.weather.wind.data.WindSensorFusionRepository
import com.example.xcpro.weather.wind.model.WindState
import com.example.xcpro.replay.IgcReplayController
import com.example.xcpro.replay.ReplayEvent
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.sensors.FlightStateSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.combine

/**
 * Binds long-lived map observers to their data sources and pushes results into state flows.
 */
internal class MapScreenObservers(
    private val scope: CoroutineScope,
    private val flightDataRepository: FlightDataRepository,
    private val windRepository: WindSensorFusionRepository,
    private val flightStateSource: FlightStateSource,
    private val flightDataManager: FlightDataManager,
    private val mapStateStore: MapStateReader,
    private val liveDataReady: MutableStateFlow<Boolean>,
    private val containerReady: MutableStateFlow<Boolean>,
    private val uiEffects: MutableSharedFlow<MapUiEffect>,
    private val igcReplayController: IgcReplayController
) {

    private var flightStartTimestampMillis: Long? = null

    fun start() {
        observeFlightDataRepository()
        observeSafeContainerSize()
        observeReplayEvents()
        observeReplaySessionDebug()
    }

    private fun observeFlightDataRepository() {
        combine(
            flightDataRepository.flightData,
            windRepository.windState,
            flightStateSource.flightState
        ) { data, wind, flightState -> Triple(data, wind, flightState) }
            .onEach { (data, wind, flightState) ->
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

                    val liveData = convertToRealTimeFlightData(
                        completeData = data,
                        windState = wind,
                        isFlying = flightState.isFlying
                    )
                        .copy(flightTime = formattedFlightTime)
                        .applyWindState(wind)
                    flightDataManager.updateLiveFlightData(liveData)
                } else {
                    flightStartTimestampMillis = null
                    flightDataManager.updateLiveFlightData(null)
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
                            uiEffects.emit(MapUiEffect.ShowToast("Replay failed: job was cancelled"))
                            return@onEach
                        }
                        Log.e("MapScreenViewModel", "Replay failed", event.throwable)
                        uiEffects.emit(
                            MapUiEffect.ShowToast("Replay failed: ${event.throwable.message ?: "Unknown error"}")
                        )
                    }
                    ReplayEvent.Cancelled -> {
                        uiEffects.emit(MapUiEffect.ShowToast("Replay failed: job was cancelled"))
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
        if (!windState.isAvailable) {
            return this
        }

        val vector = windState.vector ?: return this
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
            windSource = windState.source.name,
            windHeadwind = windState.headwind,
            windCrosswind = windState.crosswind,
            windAgeSeconds = windAgeSeconds
        )
    }
}
