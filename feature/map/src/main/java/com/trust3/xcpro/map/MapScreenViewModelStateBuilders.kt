package com.trust3.xcpro.map

import com.trust3.xcpro.MapOrientationManager
import com.trust3.xcpro.common.flight.FlightMode
import com.trust3.xcpro.map.model.GpsStatusUiModel
import com.trust3.xcpro.map.model.MapLocationUiModel
import com.trust3.xcpro.replay.SessionState
import com.trust3.xcpro.replay.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.math.round

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
    flightData: StateFlow<com.trust3.xcpro.sensors.CompleteFlightData?>
): StateFlow<MapLocationUiModel?> =
    flightData
        .map { it?.gps?.toUiModel() }
        .stateIn(scope, SharingStarted.Eagerly, null)

internal fun createOwnshipAltitudeState(
    scope: CoroutineScope,
    flightData: StateFlow<com.trust3.xcpro.sensors.CompleteFlightData?>
): StateFlow<Double?> =
    flightData
        .map(::resolveOwnshipAltitudeMeters)
        .stateIn(scope, SharingStarted.Eagerly, null)

internal fun createOverlayOwnshipAltitudeState(
    scope: CoroutineScope,
    flightData: StateFlow<com.trust3.xcpro.sensors.CompleteFlightData?>
): StateFlow<Double?> =
    flightData
        .map(::resolveOwnshipAltitudeMeters)
        .map(::quantizeOverlayOwnshipAltitudeMeters)
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)

internal fun createOwnshipCirclingState(
    scope: CoroutineScope,
    flightData: StateFlow<com.trust3.xcpro.sensors.CompleteFlightData?>
): StateFlow<Boolean> =
    flightData
        .map { sample -> sample?.isCircling == true }
        .stateIn(scope, SharingStarted.Eagerly, false)

internal fun createCirclingFeatureEnabledState(
    scope: CoroutineScope,
    thermallingSettingsFlow: Flow<com.trust3.xcpro.thermalling.ThermallingModeSettings>
): StateFlow<Boolean> =
    thermallingSettingsFlow
        .map { settings -> settings.enabled }
        .eagerState(scope = scope, initial = false)

internal data class AdsbFilterStateFlows(
    val maxDistanceKm: StateFlow<Int>,
    val verticalAboveMeters: StateFlow<Double>,
    val verticalBelowMeters: StateFlow<Double>
)

internal fun createAdsbFilterStateFlows(
    scope: CoroutineScope,
    adsbTrafficFacade: AdsbTrafficFacade
): AdsbFilterStateFlows = AdsbFilterStateFlows(
    maxDistanceKm = adsbTrafficFacade.maxDistanceKm.eagerState(
        scope = scope,
        initial = ADSB_MAX_DISTANCE_DEFAULT_KM
    ),
    verticalAboveMeters = adsbTrafficFacade.verticalAboveMeters.eagerState(
        scope = scope,
        initial = ADSB_VERTICAL_FILTER_ABOVE_DEFAULT_METERS
    ),
    verticalBelowMeters = adsbTrafficFacade.verticalBelowMeters.eagerState(
        scope = scope,
        initial = ADSB_VERTICAL_FILTER_BELOW_DEFAULT_METERS
    )
)

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

private fun resolveOwnshipAltitudeMeters(sample: com.trust3.xcpro.sensors.CompleteFlightData?): Double? {
    val gpsAltitude = sample?.gps?.altitude?.value?.takeIf { it.isFinite() }
    return gpsAltitude ?: sample?.baroAltitude?.value?.takeIf { it.isFinite() }
}

private fun quantizeOverlayOwnshipAltitudeMeters(
    altitudeMeters: Double?,
    quantizeStepMeters: Double = OVERLAY_OWNSHIP_ALTITUDE_QUANTIZE_STEP_METERS
): Double? {
    val altitude = altitudeMeters?.takeIf { it.isFinite() } ?: return null
    if (!quantizeStepMeters.isFinite() || quantizeStepMeters <= 0.0) return altitude
    return round(altitude / quantizeStepMeters) * quantizeStepMeters
}

private const val OVERLAY_OWNSHIP_ALTITUDE_QUANTIZE_STEP_METERS = 2.0
