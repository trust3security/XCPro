package com.example.xcpro.map

import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.adsb.Icao24
import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.ogn.buildOgnSelectionLookup
import com.example.xcpro.ogn.normalizeOgnAircraftKey
import com.example.xcpro.ogn.OgnTrafficTarget
import com.example.xcpro.ogn.OgnThermalHotspot
import com.example.xcpro.ogn.selectionLookupContainsOgnKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class MapScreenTrafficCoordinator(
    private val scope: CoroutineScope,
    private val allowSensorStart: StateFlow<Boolean>,
    private val isMapVisible: MutableStateFlow<Boolean>,
    private val ognOverlayEnabled: StateFlow<Boolean>,
    private val adsbOverlayEnabled: StateFlow<Boolean>,
    private val mapState: MapStateReader,
    private val mapLocation: StateFlow<MapLocationUiModel?>,
    private val isFlying: StateFlow<Boolean>,
    private val ownshipAltitudeMeters: StateFlow<Double?>,
    private val ownshipIsCircling: StateFlow<Boolean>,
    private val circlingFeatureEnabled: StateFlow<Boolean>,
    private val adsbMaxDistanceKm: StateFlow<Int>,
    private val adsbVerticalAboveMeters: StateFlow<Double>,
    private val adsbVerticalBelowMeters: StateFlow<Double>,
    private val rawOgnTargets: StateFlow<List<OgnTrafficTarget>>,
    private val selectedOgnId: MutableStateFlow<String?>,
    private val showSciaEnabled: StateFlow<Boolean>,
    private val showThermalsEnabled: StateFlow<Boolean>,
    private val thermalHotspots: StateFlow<List<OgnThermalHotspot>>,
    private val selectedThermalId: MutableStateFlow<String?>,
    private val rawAdsbTargets: StateFlow<List<AdsbTrafficUiModel>>,
    private val selectedAdsbId: MutableStateFlow<Icao24?>,
    private val ognTrafficUseCase: OgnTrafficUseCase,
    private val adsbTrafficUseCase: AdsbTrafficUseCase,
    private val emitUiEffect: suspend (MapUiEffect) -> Unit
) {
    private val mutationGateMutex = Mutex()
    private val inFlightMutationKeys = HashSet<String>()

    fun bind() {
        combine(
            allowSensorStart,
            isMapVisible,
            ognOverlayEnabled
        ) { sensorAllowed, mapVisible, overlayEnabled ->
            sensorAllowed && mapVisible && overlayEnabled
        }
            .distinctUntilChanged()
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
            .distinctUntilChanged()
            .onEach { shouldStream ->
                if (shouldStream) {
                    seedAdsbPositionFromCurrentPosition()
                }
                adsbTrafficUseCase.setStreamingEnabled(shouldStream)
            }
            .launchIn(scope)

        mapLocation
            .map { location ->
                location?.let { it.latitude to it.longitude }
            }
            .distinctUntilChanged()
            .onEach { gpsPosition ->
                if (gpsPosition == null) {
                    return@onEach
                }
                val (latitude, longitude) = gpsPosition
                ognTrafficUseCase.updateCenter(
                    latitude = latitude,
                    longitude = longitude
                )
                if (!adsbTrafficUseCase.isStreamingEnabled.value) {
                    return@onEach
                }
                adsbTrafficUseCase.updateCenter(
                    latitude = latitude,
                    longitude = longitude
                )
            }
            .launchIn(scope)

        mapLocation
            .onEach { location ->
                if (location == null) {
                    if (adsbTrafficUseCase.isStreamingEnabled.value) {
                        adsbTrafficUseCase.clearOwnshipOrigin()
                    }
                    return@onEach
                }
                if (!adsbTrafficUseCase.isStreamingEnabled.value) {
                    return@onEach
                }
                // Keep ownship reference fresh even when lat/lon are unchanged while stationary.
                adsbTrafficUseCase.updateOwnshipOrigin(
                    latitude = location.latitude,
                    longitude = location.longitude
                )
            }
            .launchIn(scope)

        combine(
            mapState.currentZoom,
            mapLocation,
            isFlying
        ) { zoomLevel, location, flying ->
            OgnAutoRadiusInput(
                zoomLevel = zoomLevel,
                groundSpeedMs = location?.speedMs ?: 0.0,
                isFlying = flying
            )
        }
            .distinctUntilChanged()
            .onEach { input ->
                ognTrafficUseCase.updateAutoReceiveRadiusContext(
                    zoomLevel = input.zoomLevel,
                    groundSpeedMs = input.groundSpeedMs,
                    isFlying = input.isFlying
                )
            }
            .launchIn(scope)

        ownshipAltitudeMeters
            .onEach { altitudeMeters ->
                adsbTrafficUseCase.updateOwnshipAltitudeMeters(altitudeMeters)
            }
            .launchIn(scope)

        combine(ownshipIsCircling, circlingFeatureEnabled) { isCircling, featureEnabled ->
            isCircling to featureEnabled
        }
            .distinctUntilChanged()
            .onEach { (isCircling, featureEnabled) ->
                adsbTrafficUseCase.updateOwnshipCirclingContext(
                    isCircling = isCircling,
                    circlingFeatureEnabled = featureEnabled
                )
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
                val normalizedSelectedId = normalizeOgnAircraftKey(selectedId)
                val selectedLookup = buildOgnSelectionLookup(setOf(normalizedSelectedId))
                if (targets.none { target ->
                        selectionLookupContainsOgnKey(
                            lookup = selectedLookup,
                            candidateKey = target.canonicalKey
                        ) || normalizeOgnAircraftKey(target.id) == normalizedSelectedId
                    }
                ) {
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

        combine(showSciaEnabled, ognOverlayEnabled) { sciaEnabled, overlayEnabled ->
            sciaEnabled to overlayEnabled
        }
            .distinctUntilChanged()
            .onEach { (sciaEnabled, overlayEnabled) ->
                if (sciaEnabled && !overlayEnabled) {
                    runPreferenceMutation(
                        actionLabel = "auto-enable OGN traffic for Scia",
                        userMessage = OGN_SETTINGS_FAILURE_MESSAGE,
                        coalesceKey = MUTATION_KEY_SCIA_OVERLAY_SYNC
                    ) {
                        ognTrafficUseCase.setOverlayEnabled(true)
                    }
                }
            }
            .launchIn(scope)
    }

    fun setMapVisible(isVisible: Boolean) {
        if (isMapVisible.value == isVisible) return
        isMapVisible.value = isVisible
    }

    fun onToggleOgnTraffic() {
        if (showSciaEnabled.value) {
            return
        }
        launchPreferenceMutation(
            actionLabel = "toggle OGN traffic",
            userMessage = OGN_SETTINGS_FAILURE_MESSAGE,
            coalesceKey = MUTATION_KEY_TOGGLE_OGN
        ) {
            val next = !ognOverlayEnabled.value
            ognTrafficUseCase.setOverlayEnabled(next)
            if (!next) {
                selectedOgnId.value = null
                selectedThermalId.value = null
            }
        }
    }

    fun onToggleOgnThermals() {
        launchPreferenceMutation(
            actionLabel = "toggle OGN thermals",
            userMessage = OGN_SETTINGS_FAILURE_MESSAGE,
            coalesceKey = MUTATION_KEY_TOGGLE_THERMALS
        ) {
            val next = !showThermalsEnabled.value
            if (next && !ognOverlayEnabled.value) {
                ognTrafficUseCase.setOverlayEnabled(true)
            }
            ognTrafficUseCase.setShowThermalsEnabled(next)
            if (!next) {
                selectedThermalId.value = null
            }
        }
    }

    fun onToggleOgnScia() {
        launchPreferenceMutation(
            actionLabel = "toggle Show Scia",
            userMessage = OGN_SETTINGS_FAILURE_MESSAGE,
            coalesceKey = MUTATION_KEY_TOGGLE_SCIA
        ) {
            val next = !showSciaEnabled.value
            if (next && !ognOverlayEnabled.value) {
                ognTrafficUseCase.setOverlayAndShowSciaEnabled(
                    overlayEnabled = true,
                    showSciaEnabled = true
                )
            } else {
                ognTrafficUseCase.setShowSciaEnabled(next)
            }
        }
    }

    fun onToggleAdsbTraffic() {
        launchPreferenceMutation(
            actionLabel = "toggle ADS-B traffic",
            userMessage = ADSB_SETTINGS_FAILURE_MESSAGE,
            coalesceKey = MUTATION_KEY_TOGGLE_ADSB
        ) {
            val next = !adsbOverlayEnabled.value
            if (next) {
                seedAdsbPositionFromCurrentPosition()
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

    private fun seedAdsbPositionFromCurrentPosition() {
        val gps = mapLocation.value
        if (gps != null) {
            adsbTrafficUseCase.updateCenter(
                latitude = gps.latitude,
                longitude = gps.longitude
            )
            adsbTrafficUseCase.updateOwnshipOrigin(
                latitude = gps.latitude,
                longitude = gps.longitude
            )
            return
        }

        adsbTrafficUseCase.clearOwnshipOrigin()
        val cameraTarget = mapState.lastCameraSnapshot.value?.target
        if (cameraTarget != null) {
            adsbTrafficUseCase.updateCenter(
                latitude = cameraTarget.latitude,
                longitude = cameraTarget.longitude
            )
        }
    }

    private fun launchPreferenceMutation(
        actionLabel: String,
        userMessage: String,
        coalesceKey: String? = null,
        mutation: suspend () -> Unit
    ) {
        scope.launch {
            runPreferenceMutation(
                actionLabel = actionLabel,
                userMessage = userMessage,
                coalesceKey = coalesceKey,
                mutation = mutation
            )
        }
    }

    private suspend fun runPreferenceMutation(
        actionLabel: String,
        userMessage: String,
        coalesceKey: String? = null,
        mutation: suspend () -> Unit
    ) {
        if (!tryAcquireMutationKey(coalesceKey)) {
            return
        }
        // AI-NOTE: Preference writes are user actions; surface failure as UI effect instead of crashing the scope.
        try {
            val failure = runCatching { mutation() }.exceptionOrNull() ?: return
            AppLogger.e(TAG, "Failed to $actionLabel: ${failure.message}", failure)
            emitUiEffect(MapUiEffect.ShowToast(userMessage))
        } finally {
            releaseMutationKey(coalesceKey)
        }
    }

    private suspend fun tryAcquireMutationKey(coalesceKey: String?): Boolean {
        if (coalesceKey == null) return true
        return mutationGateMutex.withLock {
            if (inFlightMutationKeys.contains(coalesceKey)) {
                false
            } else {
                inFlightMutationKeys.add(coalesceKey)
                true
            }
        }
    }

    private suspend fun releaseMutationKey(coalesceKey: String?) {
        if (coalesceKey == null) return
        mutationGateMutex.withLock {
            inFlightMutationKeys.remove(coalesceKey)
        }
    }

    private data class OgnAutoRadiusInput(
        val zoomLevel: Float,
        val groundSpeedMs: Double,
        val isFlying: Boolean
    )

    private companion object {
        private const val TAG = "MapScreenTrafficCoordinator"
        private const val OGN_SETTINGS_FAILURE_MESSAGE = "Unable to update OGN settings."
        private const val ADSB_SETTINGS_FAILURE_MESSAGE = "Unable to update ADS-B settings."
        private const val MUTATION_KEY_TOGGLE_OGN = "toggle_ogn"
        private const val MUTATION_KEY_TOGGLE_SCIA = "toggle_scia"
        private const val MUTATION_KEY_TOGGLE_THERMALS = "toggle_thermals"
        private const val MUTATION_KEY_TOGGLE_ADSB = "toggle_adsb"
        private const val MUTATION_KEY_SCIA_OVERLAY_SYNC = "scia_overlay_sync"
    }
}
