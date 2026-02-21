package com.example.xcpro.map

import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.adsb.Icao24
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.ogn.OgnTrafficTarget
import com.example.xcpro.ogn.OgnThermalHotspot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

internal class MapScreenTrafficCoordinator(
    private val scope: CoroutineScope,
    private val allowSensorStart: StateFlow<Boolean>,
    private val isMapVisible: MutableStateFlow<Boolean>,
    private val ognOverlayEnabled: StateFlow<Boolean>,
    private val adsbOverlayEnabled: StateFlow<Boolean>,
    private val mapState: MapStateReader,
    private val mapLocation: StateFlow<MapLocationUiModel?>,
    private val ownshipAltitudeMeters: StateFlow<Double?>,
    private val adsbMaxDistanceKm: StateFlow<Int>,
    private val adsbVerticalAboveMeters: StateFlow<Double>,
    private val adsbVerticalBelowMeters: StateFlow<Double>,
    private val rawOgnTargets: StateFlow<List<OgnTrafficTarget>>,
    private val selectedOgnId: MutableStateFlow<String?>,
    private val showThermalsEnabled: StateFlow<Boolean>,
    private val showGliderTrailsEnabled: StateFlow<Boolean>,
    private val thermalHotspots: StateFlow<List<OgnThermalHotspot>>,
    private val selectedThermalId: MutableStateFlow<String?>,
    private val rawAdsbTargets: StateFlow<List<AdsbTrafficUiModel>>,
    private val selectedAdsbId: MutableStateFlow<Icao24?>,
    private val ognTrafficUseCase: OgnTrafficUseCase,
    private val adsbTrafficUseCase: AdsbTrafficUseCase
) {
    fun bind() {
        combine(
            allowSensorStart,
            isMapVisible,
            ognOverlayEnabled
        ) { sensorAllowed, mapVisible, overlayEnabled ->
            sensorAllowed && mapVisible && overlayEnabled
        }
            .onEach { shouldStream ->
                ognTrafficUseCase.setStreamingEnabled(shouldStream)
            }
            .launchIn(scope)

        combine(
            allowSensorStart,
            isMapVisible,
            adsbOverlayEnabled
        ) { sensorAllowed, mapVisible, overlayEnabled ->
            sensorAllowed && mapVisible && overlayEnabled
        }
            .onEach { shouldStream ->
                if (shouldStream) {
                    seedAdsbCenterFromCurrentPosition()
                }
                adsbTrafficUseCase.setStreamingEnabled(shouldStream)
            }
            .launchIn(scope)

        mapLocation
            .filterNotNull()
            .map { location ->
                location.latitude to location.longitude
            }
            .distinctUntilChanged()
            .onEach { (latitude, longitude) ->
                ognTrafficUseCase.updateCenter(
                    latitude = latitude,
                    longitude = longitude
                )
                adsbTrafficUseCase.updateCenter(
                    latitude = latitude,
                    longitude = longitude
                )
                adsbTrafficUseCase.updateOwnshipOrigin(
                    latitude = latitude,
                    longitude = longitude
                )
            }
            .launchIn(scope)

        ownshipAltitudeMeters
            .onEach { altitudeMeters ->
                adsbTrafficUseCase.updateOwnshipAltitudeMeters(altitudeMeters)
            }
            .launchIn(scope)

        combine(
            adsbMaxDistanceKm,
            adsbVerticalAboveMeters,
            adsbVerticalBelowMeters
        ) { maxDistanceKm, verticalAboveMeters, verticalBelowMeters ->
            Triple(maxDistanceKm, verticalAboveMeters, verticalBelowMeters)
        }
            .distinctUntilChanged()
            .onEach { (maxDistanceKm, verticalAboveMeters, verticalBelowMeters) ->
                adsbTrafficUseCase.updateDisplayFilters(
                    maxDistanceKm = maxDistanceKm,
                    verticalAboveMeters = verticalAboveMeters,
                    verticalBelowMeters = verticalBelowMeters
                )
            }
            .launchIn(scope)

        rawAdsbTargets
            .onEach { targets ->
                val selectedId = selectedAdsbId.value ?: return@onEach
                if (targets.none { it.id == selectedId }) {
                    selectedAdsbId.value = null
                }
            }
            .launchIn(scope)

        rawOgnTargets
            .onEach { targets ->
                val selectedId = selectedOgnId.value ?: return@onEach
                if (targets.none { it.id == selectedId }) {
                    selectedOgnId.value = null
                }
            }
            .launchIn(scope)

        thermalHotspots
            .onEach { hotspots ->
                val selectedId = selectedThermalId.value ?: return@onEach
                if (hotspots.none { it.id == selectedId }) {
                    selectedThermalId.value = null
                }
            }
            .launchIn(scope)

        showThermalsEnabled
            .onEach { enabled ->
                if (!enabled) {
                    selectedThermalId.value = null
                }
            }
            .launchIn(scope)
    }

    fun setMapVisible(isVisible: Boolean) {
        if (isMapVisible.value == isVisible) return
        isMapVisible.value = isVisible
    }

    fun onToggleOgnTraffic() {
        scope.launch {
            val next = !ognOverlayEnabled.value
            ognTrafficUseCase.setOverlayEnabled(next)
            if (!next) {
                selectedOgnId.value = null
                selectedThermalId.value = null
            }
        }
    }

    fun onToggleOgnThermals() {
        scope.launch {
            val next = !showThermalsEnabled.value
            ognTrafficUseCase.setShowThermalsEnabled(next)
            if (!next) {
                selectedThermalId.value = null
            }
        }
    }

    fun onToggleOgnGliderTrails() {
        scope.launch {
            val next = !showGliderTrailsEnabled.value
            ognTrafficUseCase.setShowGliderTrailsEnabled(next)
        }
    }

    fun onToggleAdsbTraffic() {
        scope.launch {
            val next = !adsbOverlayEnabled.value
            if (next) {
                seedAdsbCenterFromCurrentPosition()
            }
            adsbTrafficUseCase.setOverlayEnabled(next)
            if (!next) {
                adsbTrafficUseCase.clearTargets()
                selectedAdsbId.value = null
            }
        }
    }

    fun onAdsbTargetSelected(id: Icao24) {
        selectedOgnId.value = null
        selectedThermalId.value = null
        selectedAdsbId.value = id
    }

    fun dismissSelectedAdsbTarget() {
        selectedAdsbId.value = null
    }

    fun onOgnTargetSelected(id: String) {
        selectedAdsbId.value = null
        selectedThermalId.value = null
        selectedOgnId.value = id
    }

    fun dismissSelectedOgnTarget() {
        selectedOgnId.value = null
    }

    fun onOgnThermalSelected(id: String) {
        selectedOgnId.value = null
        selectedAdsbId.value = null
        selectedThermalId.value = id
    }

    fun dismissSelectedOgnThermal() {
        selectedThermalId.value = null
    }

    private fun seedAdsbCenterFromCurrentPosition() {
        val gps = mapLocation.value
        if (gps != null) {
            adsbTrafficUseCase.updateCenter(
                latitude = gps.latitude,
                longitude = gps.longitude
            )
            return
        }

        val cameraTarget = mapState.lastCameraSnapshot.value?.target
        if (cameraTarget != null) {
            adsbTrafficUseCase.updateCenter(
                latitude = cameraTarget.latitude,
                longitude = cameraTarget.longitude
            )
        }
    }
}
