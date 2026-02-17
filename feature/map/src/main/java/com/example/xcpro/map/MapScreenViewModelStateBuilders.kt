package com.example.xcpro.map

import com.example.xcpro.MapOrientationManager
import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.map.model.GpsStatusUiModel
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.replay.SessionState
import com.example.xcpro.replay.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal fun createWindArrowState(
    scope: CoroutineScope,
    flightDataManager: FlightDataManager,
    orientationManager: MapOrientationManager
): StateFlow<WindArrowUiState> =
    combine(
        flightDataManager.windIndicatorStateFlow,
        orientationManager.orientationFlow
    ) { wind, orientation ->
        val baseDirection = wind.directionFromDeg ?: 0f
        val relativeDirection = normalizeAngleDeg(baseDirection - orientation.bearing.toFloat())
        WindArrowUiState(
            directionScreenDeg = relativeDirection,
            isValid = wind.isValid
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = WindArrowUiState()
    )

internal fun createMergedAdsbTargetsState(
    scope: CoroutineScope,
    rawAdsbTargets: StateFlow<List<AdsbTrafficUiModel>>,
    enrichedAdsbTargets: StateFlow<List<AdsbTrafficUiModel>>
): StateFlow<List<AdsbTrafficUiModel>> =
    combine(rawAdsbTargets, enrichedAdsbTargets) { rawTargets, enrichedTargets ->
        if (rawTargets.isEmpty()) {
            return@combine emptyList()
        }
        if (enrichedTargets.isEmpty()) {
            return@combine rawTargets
        }

        val metadataById = HashMap<String, AdsbTrafficUiModel>(enrichedTargets.size)
        for (target in enrichedTargets) {
            metadataById[target.id.raw] = target
        }
        rawTargets.map { target ->
            val enriched = metadataById[target.id.raw] ?: return@map target
            if (
                target.metadataTypecode == enriched.metadataTypecode &&
                target.metadataIcaoAircraftType == enriched.metadataIcaoAircraftType
            ) {
                target
            } else {
                target.copy(
                    metadataTypecode = enriched.metadataTypecode,
                    metadataIcaoAircraftType = enriched.metadataIcaoAircraftType
                )
            }
        }
    }.stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = emptyList())

internal data class MapReplaySensorGateStates(
    val suppressLiveGps: StateFlow<Boolean>,
    val allowSensorStart: StateFlow<Boolean>
)

internal fun createGpsStatusUiState(
    scope: CoroutineScope,
    mapSensorsUseCase: MapSensorsUseCase
): StateFlow<GpsStatusUiModel> =
    mapSensorsUseCase.gpsStatusFlow
        .map { it.toUiModel() }
        .eagerState(scope = scope, initial = GpsStatusUiModel.Searching)

internal fun createReplaySensorGateStates(
    scope: CoroutineScope,
    replaySessionState: StateFlow<SessionState>
): MapReplaySensorGateStates {
    val suppressLiveGps = replaySessionState
        .map { it.selection != null }
        .eagerState(scope = scope, initial = replaySessionState.value.selection != null)
    val allowSensorStart = replaySessionState
        .map { it.selection == null || it.status == SessionStatus.IDLE }
        .eagerState(
            scope = scope,
            initial = replaySessionState.value.selection == null ||
                replaySessionState.value.status == SessionStatus.IDLE
        )
    return MapReplaySensorGateStates(
        suppressLiveGps = suppressLiveGps,
        allowSensorStart = allowSensorStart
    )
}

internal fun createMapLocationState(
    scope: CoroutineScope,
    flightDataUseCase: FlightDataUseCase
): StateFlow<MapLocationUiModel?> =
    flightDataUseCase.flightData
        .map { it?.gps?.toUiModel() }
        .stateIn(scope, SharingStarted.Eagerly, null)

internal fun createCardHydrationReadyState(
    scope: CoroutineScope,
    containerReady: StateFlow<Boolean>,
    liveDataReady: StateFlow<Boolean>
): StateFlow<Boolean> =
    combine(containerReady, liveDataReady) { container, data -> container && data }
        .eagerState(scope = scope, initial = false)

internal fun FlightMode.toCardFlightModeSelection(): com.example.dfcards.FlightModeSelection =
    when (this) {
        FlightMode.CRUISE -> com.example.dfcards.FlightModeSelection.CRUISE
        FlightMode.THERMAL -> com.example.dfcards.FlightModeSelection.THERMAL
        FlightMode.FINAL_GLIDE -> com.example.dfcards.FlightModeSelection.FINAL_GLIDE
    }

internal fun <T> Flow<T>.eagerState(scope: CoroutineScope, initial: T): StateFlow<T> =
    stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = initial)

private fun normalizeAngleDeg(angle: Float): Float = ((angle % 360f) + 360f) % 360f
