package com.trust3.xcpro.map

import com.trust3.xcpro.core.common.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MapScreenTrafficCoordinator(
    private val scope: CoroutineScope,
    private val streamingGate: TrafficStreamingGatePort,
    private val ognOverlayEnabled: StateFlow<Boolean>,
    private val adsbOverlayEnabled: StateFlow<Boolean>,
    private val viewportPort: TrafficViewportPort,
    private val ownshipPort: TrafficOwnshipPort,
    private val adsbFilterPort: AdsbTrafficFilterPort,
    private val rawOgnTargets: StateFlow<List<OgnTrafficTarget>>,
    private val selectionPort: TrafficSelectionPort,
    private val ognTargetEnabled: StateFlow<Boolean>,
    private val ognTargetAircraftKey: StateFlow<String?>,
    private val ognSuppressedTargetIds: StateFlow<Set<String>>,
    private val showSciaEnabled: StateFlow<Boolean>,
    private val showThermalsEnabled: StateFlow<Boolean>,
    private val thermalHotspots: StateFlow<List<OgnThermalHotspot>>,
    private val rawAdsbTargets: StateFlow<List<AdsbTrafficUiModel>>,
    private val ognTrafficFacade: OgnTrafficFacade,
    private val adsbTrafficFacade: AdsbTrafficFacade,
    private val userMessagePort: TrafficUserMessagePort
) {
    private val mutationGateMutex = Mutex()
    private val inFlightMutationKeys = HashSet<String>()

    fun bind() {
        combine(
            streamingGate.allowSensorStart,
            streamingGate.isMapVisible,
            ognOverlayEnabled
        ) { sensorAllowed, mapVisible, overlayEnabled ->
            sensorAllowed && mapVisible && overlayEnabled
        }
            .distinctUntilChanged()
            .onEach { shouldStream ->
                ognTrafficFacade.setStreamingEnabled(shouldStream)
            }
            .launchIn(scope)

        combine(
            streamingGate.allowSensorStart,
            streamingGate.isMapVisible,
            adsbOverlayEnabled
        ) { sensorAllowed, mapVisible, overlayEnabled ->
            sensorAllowed && mapVisible && overlayEnabled
        }
            .distinctUntilChanged()
            .onEach { shouldStream ->
                if (shouldStream) {
                    seedAdsbPositionFromCurrentPosition()
                }
                adsbTrafficFacade.setStreamingEnabled(shouldStream)
            }
            .launchIn(scope)

        ownshipPort.location
            .map { location ->
                location?.let { it.latitude to it.longitude }
            }
            .distinctUntilChanged()
            .onEach { gpsPosition ->
                if (gpsPosition == null) {
                    return@onEach
                }
                val (latitude, longitude) = gpsPosition
                ognTrafficFacade.updateCenter(
                    latitude = latitude,
                    longitude = longitude
                )
                if (!adsbTrafficFacade.isStreamingEnabled.value) {
                    return@onEach
                }
                adsbTrafficFacade.updateCenter(
                    latitude = latitude,
                    longitude = longitude
                )
            }
            .launchIn(scope)

        ownshipPort.location
            .onEach { location ->
                if (location == null) {
                    if (adsbTrafficFacade.isStreamingEnabled.value) {
                        adsbTrafficFacade.clearOwnshipOrigin()
                        adsbTrafficFacade.updateOwnshipMotion(trackDeg = null, speedMps = null)
                    }
                    return@onEach
                }
                if (!adsbTrafficFacade.isStreamingEnabled.value) {
                    return@onEach
                }
                // Keep ownship reference fresh even when lat/lon are unchanged while stationary.
                adsbTrafficFacade.updateOwnshipOrigin(
                    latitude = location.latitude,
                    longitude = location.longitude
                )
                val ownshipMotion = toOwnshipMotion(location)
                adsbTrafficFacade.updateOwnshipMotion(
                    trackDeg = ownshipMotion.trackDeg,
                    speedMps = ownshipMotion.speedMps
                )
            }
            .launchIn(scope)

        combine(
            viewportPort.currentZoom,
            ownshipPort.location,
            ownshipPort.isFlying
        ) { zoomLevel, location, flying ->
            OgnAutoRadiusInput(
                zoomLevel = zoomLevel,
                groundSpeedMs = location?.speedMs ?: 0.0,
                isFlying = flying
            )
        }
            .distinctUntilChanged()
            .onEach { input ->
                ognTrafficFacade.updateAutoReceiveRadiusContext(
                    zoomLevel = input.zoomLevel,
                    groundSpeedMs = input.groundSpeedMs,
                    isFlying = input.isFlying
                )
            }
            .launchIn(scope)

        ownshipPort.altitudeMeters
            .onEach { altitudeMeters ->
                adsbTrafficFacade.updateOwnshipAltitudeMeters(altitudeMeters)
            }
            .launchIn(scope)

        combine(ownshipPort.isCircling, ownshipPort.circlingFeatureEnabled) { isCircling, featureEnabled ->
            isCircling to featureEnabled
        }
            .distinctUntilChanged()
            .onEach { (isCircling, featureEnabled) ->
                adsbTrafficFacade.updateOwnshipCirclingContext(
                    isCircling = isCircling,
                    circlingFeatureEnabled = featureEnabled
                )
            }
            .launchIn(scope)

        combine(
            adsbFilterPort.maxDistanceKm,
            adsbFilterPort.verticalAboveMeters,
            adsbFilterPort.verticalBelowMeters
        ) { maxDistanceKm, verticalAboveMeters, verticalBelowMeters ->
            Triple(maxDistanceKm, verticalAboveMeters, verticalBelowMeters)
        }
            .distinctUntilChanged()
            .onEach { (maxDistanceKm, verticalAboveMeters, verticalBelowMeters) ->
                adsbTrafficFacade.updateDisplayFilters(
                    maxDistanceKm = maxDistanceKm,
                    verticalAboveMeters = verticalAboveMeters,
                    verticalBelowMeters = verticalBelowMeters
                )
            }
            .launchIn(scope)

        rawAdsbTargets
            .onEach { targets ->
                val selectedId = selectionPort.selectedAdsbId.value ?: return@onEach
                if (targets.none { it.id == selectedId }) {
                    selectionPort.setSelectedAdsbId(null)
                }
            }
            .launchIn(scope)

        rawOgnTargets
            .onEach { targets ->
                val selectedId = selectionPort.selectedOgnId.value ?: return@onEach
                val normalizedSelectedId = normalizeOgnAircraftKey(selectedId)
                val selectedLookup = buildOgnSelectionLookup(setOf(normalizedSelectedId))
                if (targets.none { target ->
                        selectionLookupContainsOgnKey(
                            lookup = selectedLookup,
                            candidateKey = target.canonicalKey
                        ) || normalizeOgnAircraftKey(target.id) == normalizedSelectedId
                    }
                ) {
                    selectionPort.setSelectedOgnId(null)
                }
            }
            .launchIn(scope)

        combine(ognTargetEnabled, ognTargetAircraftKey, ognSuppressedTargetIds) { enabled, aircraftKey, suppressedIds ->
            Triple(enabled, aircraftKey, suppressedIds)
        }
            .distinctUntilChanged()
            .onEach { (enabled, aircraftKey, suppressedIds) ->
                if (!enabled || suppressedIds.isEmpty()) return@onEach
                val normalizedAircraftKey = normalizeOgnAircraftKeyOrNull(aircraftKey) ?: return@onEach
                val selectedLookup = buildOgnSelectionLookup(setOf(normalizedAircraftKey))
                val isSuppressed = suppressedIds.any { suppressedKey ->
                    selectionLookupContainsOgnKey(
                        lookup = selectedLookup,
                        candidateKey = suppressedKey
                    )
                }
                if (!isSuppressed) return@onEach
                runPreferenceMutation(
                    actionLabel = "clear OGN target due to ownship suppression",
                    userMessage = OGN_SETTINGS_FAILURE_MESSAGE,
                    coalesceKey = MUTATION_KEY_CLEAR_SUPPRESSED_TARGET
                ) {
                    ognTrafficFacade.clearTargetSelection()
                }
            }
            .launchIn(scope)

        thermalHotspots
            .onEach { hotspots ->
                val selectedId = selectionPort.selectedThermalId.value ?: return@onEach
                if (hotspots.none { it.id == selectedId }) {
                    selectionPort.setSelectedThermalId(null)
                }
            }
            .launchIn(scope)

        showThermalsEnabled
            .onEach { enabled ->
                if (!enabled) {
                    selectionPort.setSelectedThermalId(null)
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
                        ognTrafficFacade.setOverlayEnabled(true)
                    }
                }
            }
            .launchIn(scope)
    }

    fun setMapVisible(isVisible: Boolean) {
        if (streamingGate.isMapVisible.value == isVisible) return
        streamingGate.setMapVisible(isVisible)
        if (!isVisible) {
            selectionPort.setSelectedOgnId(null)
            selectionPort.setSelectedThermalId(null)
            selectionPort.setSelectedAdsbId(null)
        }
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
            ognTrafficFacade.setOverlayEnabled(next)
            if (!next) {
                selectionPort.setSelectedOgnId(null)
                selectionPort.setSelectedThermalId(null)
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
                ognTrafficFacade.setOverlayEnabled(true)
            }
            ognTrafficFacade.setShowThermalsEnabled(next)
            if (!next) {
                selectionPort.setSelectedThermalId(null)
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
                ognTrafficFacade.setOverlayAndShowSciaEnabled(
                    overlayEnabled = true,
                    showSciaEnabled = true
                )
            } else {
                ognTrafficFacade.setShowSciaEnabled(next)
            }
        }
    }

    fun onSetOgnTarget(aircraftKey: String, enabled: Boolean) {
        launchPreferenceMutation(
            actionLabel = "set OGN target",
            userMessage = OGN_SETTINGS_FAILURE_MESSAGE,
            coalesceKey = MUTATION_KEY_TARGET_SELECTION
        ) {
            if (!enabled) {
                ognTrafficFacade.clearTargetSelection()
                return@launchPreferenceMutation
            }
            val normalizedAircraftKey = normalizeOgnAircraftKeyOrNull(aircraftKey) ?: return@launchPreferenceMutation
            if (!ognOverlayEnabled.value) {
                ognTrafficFacade.setOverlayEnabled(true)
            }
            ognTrafficFacade.setTargetSelection(enabled = true, aircraftKey = normalizedAircraftKey)
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
            adsbTrafficFacade.setOverlayEnabled(next)
            if (!next) {
                adsbTrafficFacade.clearTargets()
                selectionPort.setSelectedAdsbId(null)
            }
        }
    }

    fun onAdsbTargetSelected(id: Icao24) {
        selectionPort.setSelectedOgnId(null)
        selectionPort.setSelectedThermalId(null)
        selectionPort.setSelectedAdsbId(id)
    }

    fun dismissSelectedAdsbTarget() {
        selectionPort.setSelectedAdsbId(null)
    }

    fun onOgnTargetSelected(id: String) {
        selectionPort.setSelectedAdsbId(null)
        selectionPort.setSelectedThermalId(null)
        selectionPort.setSelectedOgnId(id)
    }

    fun dismissSelectedOgnTarget() {
        selectionPort.setSelectedOgnId(null)
    }

    fun onOgnThermalSelected(id: String) {
        selectionPort.setSelectedOgnId(null)
        selectionPort.setSelectedAdsbId(null)
        selectionPort.setSelectedThermalId(id)
        selectionPort.setSelectedThermalDetailsVisible(true)
    }

    fun dismissSelectedOgnThermal() {
        selectionPort.setSelectedThermalDetailsVisible(false)
    }

    private fun seedAdsbPositionFromCurrentPosition() {
        val gps = ownshipPort.location.value
        if (gps != null) {
            adsbTrafficFacade.updateCenter(
                latitude = gps.latitude,
                longitude = gps.longitude
            )
            adsbTrafficFacade.updateOwnshipOrigin(
                latitude = gps.latitude,
                longitude = gps.longitude
            )
            val ownshipMotion = toOwnshipMotion(gps)
            adsbTrafficFacade.updateOwnshipMotion(
                trackDeg = ownshipMotion.trackDeg,
                speedMps = ownshipMotion.speedMps
            )
            return
        }

        adsbTrafficFacade.clearOwnshipOrigin()
        adsbTrafficFacade.updateOwnshipMotion(trackDeg = null, speedMps = null)
        val cameraTarget = viewportPort.lastCameraTarget()
        if (cameraTarget != null) {
            adsbTrafficFacade.updateCenter(
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
        try {
            val failure = runCatching { mutation() }.exceptionOrNull() ?: return
            AppLogger.e(TAG, "Failed to $actionLabel: ${failure.message}", failure)
            userMessagePort.showToast(userMessage)
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

    private data class OwnshipMotion(
        val trackDeg: Double?,
        val speedMps: Double?
    )

    private fun toOwnshipMotion(location: TrafficMapOwnshipLocation): OwnshipMotion {
        val normalizedSpeedMps = location.speedMs
            .takeIf { it.isFinite() && it >= 0.0 }
            ?.takeUnless {
                location.speedAccuracyMs?.let { accuracy -> accuracy.isFinite() && accuracy > MAX_ACCEPTABLE_SPEED_ACCURACY_MPS } == true
            }
        val speedForTrack = normalizedSpeedMps ?: return OwnshipMotion(trackDeg = null, speedMps = null)
        if (speedForTrack < MIN_VALID_TRACK_SPEED_MPS) {
            return OwnshipMotion(trackDeg = null, speedMps = speedForTrack)
        }

        val validTrack = location.bearingDeg
            .takeIf { it.isFinite() }
            ?.takeUnless {
                location.bearingAccuracyDeg?.let { accuracy ->
                    accuracy.isFinite() && accuracy > MAX_ACCEPTABLE_BEARING_ACCURACY_DEG
                } == true
            }
        return OwnshipMotion(trackDeg = validTrack, speedMps = speedForTrack)
    }

    private companion object {
        private const val TAG = "MapScreenTrafficCoordinator"
        private const val OGN_SETTINGS_FAILURE_MESSAGE = "Unable to update OGN settings."
        private const val ADSB_SETTINGS_FAILURE_MESSAGE = "Unable to update ADS-B settings."
        private const val MUTATION_KEY_TOGGLE_OGN = "toggle_ogn"
        private const val MUTATION_KEY_TOGGLE_SCIA = "toggle_scia"
        private const val MUTATION_KEY_TOGGLE_THERMALS = "toggle_thermals"
        private const val MUTATION_KEY_TOGGLE_ADSB = "toggle_adsb"
        private const val MUTATION_KEY_SCIA_OVERLAY_SYNC = "scia_overlay_sync"
        private const val MUTATION_KEY_TARGET_SELECTION = "ogn_target_selection"
        private const val MUTATION_KEY_CLEAR_SUPPRESSED_TARGET = "ogn_target_suppression_clear"
        private const val MIN_VALID_TRACK_SPEED_MPS = 2.0
        private const val MAX_ACCEPTABLE_BEARING_ACCURACY_DEG = 60.0
        private const val MAX_ACCEPTABLE_SPEED_ACCURACY_MPS = 12.0
    }
}
