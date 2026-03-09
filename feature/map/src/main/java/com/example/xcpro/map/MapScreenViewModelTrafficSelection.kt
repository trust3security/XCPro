package com.example.xcpro.map

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal data class MapTrafficSelectionState(
    val selectedAdsbId: MutableStateFlow<Icao24?>,
    val selectedAdsbTarget: StateFlow<AdsbSelectedTargetDetails?>,
    val selectedOgnId: MutableStateFlow<String?>,
    val selectedOgnTarget: StateFlow<OgnTrafficTarget?>,
    val selectedOgnThermalId: MutableStateFlow<String?>,
    val selectedOgnThermal: StateFlow<OgnThermalHotspot?>
)

internal fun createTrafficSelectionState(
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
