package com.trust3.xcpro.map

import com.trust3.xcpro.common.orientation.MapOrientationMode
import com.trust3.xcpro.map.config.MapFeatureFlags
import org.maplibre.android.geometry.LatLng

class MapTrackingCameraController(
    private val mapSizeProvider: MapViewSizeProvider,
    private val mapStateReader: MapStateReader,
    private val stateActions: MapStateActions,
    private val preferenceReader: MapCameraPreferenceReader,
    private val paddingProvider: () -> IntArray,
    private val positionController: MapPositionController,
    private val cameraPolicy: MapCameraPolicy,
    private val followCameraMotionPolicy: MapFollowCameraMotionPolicy,
    private val cameraUpdateGate: MapCameraUpdateGate,
    private val biasResetter: MapShiftBiasResetter,
    private val cameraControllerProvider: () -> MapCameraController?,
    private val featureFlags: MapFeatureFlags,
    private val initialZoomLevel: Double,
    private val minUpdateIntervalMs: Long,
    private val bearingEpsDeg: Double,
    private val maxCameraBearingStepDeg: Double = 5.0,
    private val minCameraBearingChangeDeg: Double = 2.0,
    private val enableCameraBearingSmoothing: Boolean = true
) {
    data class FrameInput(
        val location: LatLng,
        val trackDeg: Double,
        val cameraTargetBearing: Double,
        val speedMs: Double,
        val orientationMode: MapOrientationMode,
        val timeBase: DisplayClock.TimeBase?,
        val nowMs: Long
    )

    data class FrameResult(
        val cameraBearing: Double,
        val initialCenteredZoom: Double? = null
    )

    private var lastCameraUpdateMs: Long = 0L

    private var hasInitiallyCentered: Boolean
        get() = mapStateReader.hasInitiallyCentered.value
        set(value) {
            stateActions.setHasInitiallyCentered(value)
        }

    fun onTimeBaseChanged(location: LatLng) {
        biasResetter.reset()
        cameraUpdateGate.resetTo(location)
        lastCameraUpdateMs = 0L
    }

    fun updateCamera(input: FrameInput): FrameResult? {
        val cameraController = cameraControllerProvider() ?: return null
        val initialCenteredZoom = ensureInitialCentering(cameraController, input.location)
        val currentCameraBearing = cameraController.cameraPosition.bearing
        val smoothedCameraBearing = if (shouldSmoothCameraBearing(input.timeBase)) {
            resolveCameraBearingUpdate(
                currentBearing = currentCameraBearing,
                requestedBearing = input.cameraTargetBearing,
                orientationMode = input.orientationMode,
                maxBearingStepDeg = maxCameraBearingStepDeg,
                minBearingChangeDeg = minCameraBearingChangeDeg
            ) ?: currentCameraBearing
        } else {
            input.cameraTargetBearing
        }

        val shouldTrackCamera = mapStateReader.isTrackingLocation.value && !mapStateReader.showReturnButton.value
        if (shouldTrackCamera) {
            val padding = cameraPolicy.computeSmoothedPadding(
                rawPadding = paddingProvider(),
                biasInput = buildBiasInput(
                    trackDeg = input.trackDeg,
                    mapBearing = input.cameraTargetBearing,
                    speedMs = input.speedMs,
                    orientationMode = input.orientationMode
                )
            )
            // Keep glider vertical-position preference responsive even when camera movement
            // is gated (for example stationary ownship after returning from settings).
            positionController.applyPadding(cameraController, padding)
            val shouldUpdate = cameraPolicy.shouldUpdateCamera(
                input = MapCameraPolicy.CameraUpdateInput(
                    timeBase = input.timeBase,
                    useRenderFrameSync = featureFlags.useRenderFrameSync,
                    cameraBearing = cameraController.cameraPosition.bearing,
                    targetBearing = input.cameraTargetBearing,
                    nowMs = input.nowMs,
                    lastCameraUpdateMs = lastCameraUpdateMs,
                    minUpdateIntervalMs = minUpdateIntervalMs,
                    bearingEpsDeg = bearingEpsDeg
                )
            ) {
                cameraUpdateGate.accept(input.location)
            }
            if (shouldUpdate) {
                positionController.updateCamera(
                    camera = cameraController,
                    location = input.location,
                    cameraBearing = smoothedCameraBearing.takeIf { it.isFinite() },
                    padding = padding,
                    motion = followCameraMotionPolicy.resolveContinuousFollowMotion(
                        timeBase = input.timeBase
                    )
                )
                cameraUpdateGate.resetTo(input.location)
                lastCameraUpdateMs = input.nowMs
            }
        } else {
            biasResetter.reset()
        }

        return FrameResult(
            cameraBearing = cameraController.cameraPosition.bearing,
            initialCenteredZoom = initialCenteredZoom
        )
    }

    private fun shouldSmoothCameraBearing(timeBase: DisplayClock.TimeBase?): Boolean {
        if (!enableCameraBearingSmoothing) {
            return false
        }
        return timeBase != DisplayClock.TimeBase.REPLAY
    }

    private fun ensureInitialCentering(
        cameraController: MapCameraController,
        location: LatLng
    ): Double? {
        if (hasInitiallyCentered) return null

        // Initial centering sets a sane zoom once, then preserves user zoom.
        val currentPosition = cameraController.cameraPosition
        val zoomToUse = if (currentPosition.zoom < 5.0) {
            initialZoomLevel
        } else {
            currentPosition.zoom
        }
        val initialCameraPosition = MapCameraPositionSnapshot(
            target = location,
            zoom = zoomToUse,
            bearing = 0.0,
            tilt = 0.0
        )
        val padding = paddingProvider()
        cameraController.moveCamera(initialCameraPosition)
        cameraController.setPadding(padding[0], padding[1], padding[2], padding[3])
        hasInitiallyCentered = true
        return zoomToUse
    }

    private fun buildBiasInput(
        trackDeg: Double,
        mapBearing: Double,
        speedMs: Double,
        orientationMode: MapOrientationMode
    ): MapCameraPolicy.BiasInput {
        val mapSize = mapSizeProvider.size()
        return MapCameraPolicy.BiasInput(
            trackDeg = trackDeg,
            targetBearingDeg = null,
            mapBearing = mapBearing,
            speedMs = speedMs,
            orientationMode = orientationMode,
            flightMode = mapStateReader.currentMode.value,
            biasMode = preferenceReader.getMapShiftBiasMode(),
            biasStrength = preferenceReader.getMapShiftBiasStrength(),
            minSpeedMs = featureFlags.mapShiftBiasMinSpeedMs,
            historySize = featureFlags.mapShiftBiasHistorySize,
            maxOffsetFraction = featureFlags.mapShiftBiasMaxOffsetFraction,
            holdOnInvalid = featureFlags.mapShiftBiasHoldOnInvalid,
            screenWidthPx = mapSize.widthPx,
            screenHeightPx = mapSize.heightPx,
            gliderScreenPercent = preferenceReader.getGliderScreenPercent()
        )
    }
}
