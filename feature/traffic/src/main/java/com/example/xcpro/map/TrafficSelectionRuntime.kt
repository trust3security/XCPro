package com.example.xcpro.map

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MapTrafficSelectionState(
    val selectedAdsbId: MutableStateFlow<Icao24?>,
    val selectedAdsbTarget: StateFlow<AdsbSelectedTargetDetails?>,
    val selectedOgnId: MutableStateFlow<String?>,
    val selectedOgnTarget: StateFlow<OgnTrafficTarget?>,
    val selectedOgnThermalId: MutableStateFlow<String?>,
    val selectedOgnThermal: StateFlow<OgnThermalHotspot?>
)

fun createTrafficSelectionState(
    scope: CoroutineScope,
    adsbMetadataEnrichmentUseCase: AdsbMetadataEnrichmentUseCase,
    rawAdsbTargets: StateFlow<List<AdsbTrafficUiModel>>,
    ognTargets: StateFlow<List<OgnTrafficTarget>>,
    thermalHotspots: StateFlow<List<OgnThermalHotspot>>
): MapTrafficSelectionState {
    val selectedAdsbId = MutableStateFlow<Icao24?>(null)
    val selectedOgnId = MutableStateFlow<String?>(null)
    val selectedOgnThermalId = MutableStateFlow<String?>(null)
    return MapTrafficSelectionState(
        selectedAdsbId = selectedAdsbId,
        selectedAdsbTarget = createSelectedAdsbTargetState(
            scope = scope,
            adsbMetadataEnrichmentUseCase = adsbMetadataEnrichmentUseCase,
            selectedAdsbId = selectedAdsbId,
            rawAdsbTargets = rawAdsbTargets
        ),
        selectedOgnId = selectedOgnId,
        selectedOgnTarget = createSelectedOgnTargetState(
            scope = scope,
            selectedOgnId = selectedOgnId,
            ognTargets = ognTargets
        ),
        selectedOgnThermalId = selectedOgnThermalId,
        selectedOgnThermal = createSelectedOgnThermalState(
            scope = scope,
            selectedThermalId = selectedOgnThermalId,
            thermalHotspots = thermalHotspots
        )
    )
}

fun createSelectedAdsbTargetState(
    scope: CoroutineScope,
    adsbMetadataEnrichmentUseCase: AdsbMetadataEnrichmentUseCase,
    selectedAdsbId: StateFlow<Icao24?>,
    rawAdsbTargets: StateFlow<List<AdsbTrafficUiModel>>
): StateFlow<AdsbSelectedTargetDetails?> = adsbMetadataEnrichmentUseCase
    .selectedTargetDetails(selectedIcao24 = selectedAdsbId, adsbTargets = rawAdsbTargets)
    .eagerState(scope = scope, initial = null)

fun createSelectedOgnTargetState(
    scope: CoroutineScope,
    selectedOgnId: StateFlow<String?>,
    ognTargets: StateFlow<List<OgnTrafficTarget>>
): StateFlow<OgnTrafficTarget?> =
    combine(selectedOgnId, ognTargets) { selectedId, targets ->
        selectedId?.let { key ->
            val normalizedKey = normalizeOgnAircraftKey(key)
            val selectedLookup = buildOgnSelectionLookup(setOf(normalizedKey))
            targets.firstOrNull { target ->
                selectionLookupContainsOgnKey(
                    lookup = selectedLookup,
                    candidateKey = target.canonicalKey
                ) || normalizeOgnAircraftKey(target.id) == normalizedKey
            }
        }
    }.stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = null)

fun createSelectedOgnThermalState(
    scope: CoroutineScope,
    selectedThermalId: StateFlow<String?>,
    thermalHotspots: StateFlow<List<OgnThermalHotspot>>
): StateFlow<OgnThermalHotspot?> =
    combine(selectedThermalId, thermalHotspots) { selectedId, hotspots ->
        selectedId?.let { id -> hotspots.firstOrNull { it.id == id } }
    }.stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = null)

private fun <T> Flow<T>.eagerState(scope: CoroutineScope, initial: T): StateFlow<T> =
    stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = initial)
