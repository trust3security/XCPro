package com.example.xcpro.map

import com.example.xcpro.map.model.MapLocationUiModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

internal fun createTrafficStreamingGatePort(
    allowSensorStart: StateFlow<Boolean>,
    isMapVisible: MutableStateFlow<Boolean>
): TrafficStreamingGatePort = object : TrafficStreamingGatePort {
    override val allowSensorStart: StateFlow<Boolean> = allowSensorStart
    override val isMapVisible: StateFlow<Boolean> = isMapVisible

    override fun setMapVisible(isVisible: Boolean) {
        isMapVisible.value = isVisible
    }
}

internal fun createTrafficViewportPort(
    mapState: MapStateReader
): TrafficViewportPort = object : TrafficViewportPort {
    override val currentZoom: StateFlow<Float> = mapState.currentZoom

    override fun lastCameraTarget(): TrafficMapCoordinate? =
        mapState.lastCameraSnapshot.value?.target?.let { target ->
            TrafficMapCoordinate(
                latitude = target.latitude,
                longitude = target.longitude
            )
        }
}

internal fun createTrafficOwnshipPort(
    scope: CoroutineScope,
    mapLocation: StateFlow<MapLocationUiModel?>,
    isFlying: StateFlow<Boolean>,
    ownshipAltitudeMeters: StateFlow<Double?>,
    ownshipIsCircling: StateFlow<Boolean>,
    circlingFeatureEnabled: StateFlow<Boolean>
): TrafficOwnshipPort = object : TrafficOwnshipPort {
    override val location: StateFlow<TrafficMapOwnshipLocation?> =
        mapLocation
            .map { it?.toTrafficOwnshipLocation() }
            .eagerState(scope = scope, initial = mapLocation.value?.toTrafficOwnshipLocation())
    override val isFlying: StateFlow<Boolean> = isFlying
    override val altitudeMeters: StateFlow<Double?> = ownshipAltitudeMeters
    override val isCircling: StateFlow<Boolean> = ownshipIsCircling
    override val circlingFeatureEnabled: StateFlow<Boolean> = circlingFeatureEnabled
}

internal fun createAdsbTrafficFilterPort(
    filterStates: AdsbFilterStateFlows
): AdsbTrafficFilterPort = object : AdsbTrafficFilterPort {
    override val maxDistanceKm: StateFlow<Int> = filterStates.maxDistanceKm
    override val verticalAboveMeters: StateFlow<Double> = filterStates.verticalAboveMeters
    override val verticalBelowMeters: StateFlow<Double> = filterStates.verticalBelowMeters
}

internal fun createTrafficSelectionPort(
    selectedOgnId: MutableStateFlow<String?>,
    selectedThermalId: MutableStateFlow<String?>,
    selectedThermalDetailsVisible: MutableStateFlow<Boolean> = MutableStateFlow(false),
    selectedAdsbId: MutableStateFlow<Icao24?>
): TrafficSelectionPort = object : TrafficSelectionPort {
    override val selectedOgnId: StateFlow<String?> = selectedOgnId
    override val selectedThermalId: StateFlow<String?> = selectedThermalId
    override val selectedThermalDetailsVisible: StateFlow<Boolean> = selectedThermalDetailsVisible
    override val selectedAdsbId: StateFlow<Icao24?> = selectedAdsbId

    override fun setSelectedOgnId(id: String?) {
        selectedOgnId.value = id
    }

    override fun setSelectedThermalId(id: String?) {
        selectedThermalId.value = id
        if (id == null) {
            selectedThermalDetailsVisible.value = false
        }
    }

    override fun setSelectedThermalDetailsVisible(visible: Boolean) {
        selectedThermalDetailsVisible.value = visible && selectedThermalId.value != null
    }

    override fun setSelectedAdsbId(id: Icao24?) {
        selectedAdsbId.value = id
    }
}

internal fun createTrafficUserMessagePort(
    uiEffects: MutableSharedFlow<MapUiEffect>
): TrafficUserMessagePort = object : TrafficUserMessagePort {
    override suspend fun showToast(message: String) {
        uiEffects.emit(MapUiEffect.ShowToast(message))
    }
}

private fun MapLocationUiModel.toTrafficOwnshipLocation(): TrafficMapOwnshipLocation =
    TrafficMapOwnshipLocation(
        latitude = latitude,
        longitude = longitude,
        speedMs = speedMs,
        bearingDeg = bearingDeg,
        bearingAccuracyDeg = bearingAccuracyDeg,
        speedAccuracyMs = speedAccuracyMs,
        sampleTimeMillis = timeForCalculationsMillis
    )
