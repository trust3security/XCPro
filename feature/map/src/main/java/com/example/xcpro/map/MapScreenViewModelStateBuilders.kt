package com.example.xcpro.map

import com.example.xcpro.MapOrientationManager
import com.example.xcpro.adsb.ADSB_MAX_DISTANCE_DEFAULT_KM
import com.example.xcpro.adsb.ADSB_VERTICAL_FILTER_ABOVE_DEFAULT_METERS
import com.example.xcpro.adsb.ADSB_VERTICAL_FILTER_BELOW_DEFAULT_METERS
import com.example.xcpro.adsb.AdsbSelectedTargetDetails
import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.adsb.Icao24
import com.example.xcpro.adsb.metadata.domain.AdsbMetadataEnrichmentUseCase
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.map.model.GpsStatusUiModel
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.ogn.OgnTrafficTarget
import com.example.xcpro.ogn.OgnThermalHotspot
import com.example.xcpro.ogn.normalizeOgnAircraftKey
import com.example.xcpro.ogn.selectionSetContainsOgnKey
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

internal fun createSelectedAdsbTargetState(
    scope: CoroutineScope,
    adsbMetadataEnrichmentUseCase: AdsbMetadataEnrichmentUseCase,
    selectedAdsbId: StateFlow<Icao24?>,
    rawAdsbTargets: StateFlow<List<AdsbTrafficUiModel>>
): StateFlow<AdsbSelectedTargetDetails?> = adsbMetadataEnrichmentUseCase
    .selectedTargetDetails(selectedIcao24 = selectedAdsbId, adsbTargets = rawAdsbTargets)
    .eagerState(scope = scope, initial = null)

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

internal fun createOwnshipAltitudeState(
    scope: CoroutineScope,
    flightDataUseCase: FlightDataUseCase
): StateFlow<Double?> =
    flightDataUseCase.flightData
        .map { sample ->
            val gpsAltitude = sample?.gps?.altitude?.value?.takeIf { it.isFinite() }
            gpsAltitude ?: sample?.baroAltitude?.value?.takeIf { it.isFinite() }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

internal fun createOwnshipCirclingState(
    scope: CoroutineScope,
    flightDataUseCase: FlightDataUseCase
): StateFlow<Boolean> =
    flightDataUseCase.flightData
        .map { sample -> sample?.isCircling == true }
        .stateIn(scope, SharingStarted.Eagerly, false)

internal fun createCirclingFeatureEnabledState(
    scope: CoroutineScope,
    thermallingModeUseCase: ThermallingModeRuntimeUseCase
): StateFlow<Boolean> =
    thermallingModeUseCase.settingsFlow
        .map { settings -> settings.enabled }
        .eagerState(scope = scope, initial = false)

internal data class AdsbFilterStateFlows(
    val maxDistanceKm: StateFlow<Int>,
    val verticalAboveMeters: StateFlow<Double>,
    val verticalBelowMeters: StateFlow<Double>
)

internal fun createAdsbFilterStateFlows(
    scope: CoroutineScope,
    adsbTrafficUseCase: AdsbTrafficUseCase
): AdsbFilterStateFlows = AdsbFilterStateFlows(
    maxDistanceKm = adsbTrafficUseCase.maxDistanceKm.eagerState(
        scope = scope,
        initial = ADSB_MAX_DISTANCE_DEFAULT_KM
    ),
    verticalAboveMeters = adsbTrafficUseCase.verticalAboveMeters.eagerState(
        scope = scope,
        initial = ADSB_VERTICAL_FILTER_ABOVE_DEFAULT_METERS
    ),
    verticalBelowMeters = adsbTrafficUseCase.verticalBelowMeters.eagerState(
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

internal fun createSelectedOgnTargetState(
    scope: CoroutineScope,
    selectedOgnId: StateFlow<String?>,
    ognTargets: StateFlow<List<OgnTrafficTarget>>
): StateFlow<OgnTrafficTarget?> =
    combine(selectedOgnId, ognTargets) { selectedId, targets ->
        selectedId?.let { key ->
            val normalizedKey = normalizeOgnAircraftKey(key)
            targets.firstOrNull { target ->
                selectionSetContainsOgnKey(
                    selectedKeys = setOf(normalizedKey),
                    candidateKey = target.canonicalKey
                ) || normalizeOgnAircraftKey(target.id) == normalizedKey
            }
        }
    }.stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = null)

internal fun createSelectedOgnThermalState(
    scope: CoroutineScope,
    selectedThermalId: StateFlow<String?>,
    thermalHotspots: StateFlow<List<OgnThermalHotspot>>
): StateFlow<OgnThermalHotspot?> =
    combine(selectedThermalId, thermalHotspots) { selectedId, hotspots ->
        selectedId?.let { id -> hotspots.firstOrNull { it.id == id } }
    }.stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = null)

internal fun FlightMode.toCardFlightModeSelection(): com.example.dfcards.FlightModeSelection =
    when (this) {
        FlightMode.CRUISE -> com.example.dfcards.FlightModeSelection.CRUISE
        FlightMode.THERMAL -> com.example.dfcards.FlightModeSelection.THERMAL
        FlightMode.FINAL_GLIDE -> com.example.dfcards.FlightModeSelection.FINAL_GLIDE
    }

internal fun <T> Flow<T>.eagerState(scope: CoroutineScope, initial: T): StateFlow<T> =
    stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = initial)

private fun normalizeAngleDeg(angle: Float): Float = ((angle % 360f) + 360f) % 360f
