package com.trust3.xcpro.map

import com.trust3.xcpro.common.orientation.MapOrientationMode
import com.trust3.xcpro.map.config.MapFeatureFlags
import com.trust3.xcpro.map.domain.MapShiftBiasCalculator
import com.trust3.xcpro.map.domain.MapShiftBiasMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.geometry.LatLng

class MapTrackingCameraControllerTest {

    @Test
    fun updateCamera_initialCentering_setsZoomPadding_andMarksCentered() {
        val featureFlags = MapFeatureFlags()
        val snapshot = snapshotFlags(featureFlags)
        try {
            featureFlags.useRenderFrameSync = false
            featureFlags.useRuntimeReplayHeading = false

            val store = MapStateStore(initialStyleName = "Topo")
            val actions = MapStateActionsDelegate(store)
            val cameraController = FakeCameraController(
                initial = MapCameraPositionSnapshot(
                    target = LatLng(0.0, 0.0),
                    zoom = 3.0,
                    bearing = 0.0,
                    tilt = 0.0
                )
            )
            val deps = buildDeps(store, actions, cameraController, featureFlags)

            val result = deps.controller.updateCamera(
                MapTrackingCameraController.FrameInput(
                    location = LatLng(1.0, 2.0),
                    trackDeg = 45.0,
                    cameraTargetBearing = 0.0,
                    speedMs = 20.0,
                    orientationMode = MapOrientationMode.NORTH_UP,
                    timeBase = DisplayClock.TimeBase.MONOTONIC,
                    nowMs = 1_000L
                )
            )

            assertTrue(store.hasInitiallyCentered.value)
            assertEquals(10.0, result?.initialCenteredZoom ?: -1.0, 0.0)
            assertEquals(1, cameraController.moveCount)
            assertEquals(10.0, cameraController.cameraPosition.zoom, 0.0)
            assertTrue(cameraController.lastPadding?.contentEquals(PADDING) == true)
        } finally {
            restoreFlags(featureFlags, snapshot)
        }
    }

    @Test
    fun onTimeBaseChanged_resetsGate_and_allows_update() {
        val featureFlags = MapFeatureFlags()
        val snapshot = snapshotFlags(featureFlags)
        try {
            featureFlags.useRenderFrameSync = false
            featureFlags.useRuntimeReplayHeading = false

            val store = MapStateStore(initialStyleName = "Topo")
            val actions = MapStateActionsDelegate(store)
            actions.setHasInitiallyCentered(true)
            val cameraController = FakeCameraController(
                initial = MapCameraPositionSnapshot(
                    target = LatLng(0.0, 0.0),
                    zoom = 12.0,
                    bearing = 0.0,
                    tilt = 0.0
                )
            )
            val deps = buildDeps(store, actions, cameraController, featureFlags)
            deps.gate.acceptEnabled = false

            deps.controller.updateCamera(
                MapTrackingCameraController.FrameInput(
                    location = LatLng(5.0, 6.0),
                    trackDeg = 90.0,
                    cameraTargetBearing = 0.0,
                    speedMs = 25.0,
                    orientationMode = MapOrientationMode.NORTH_UP,
                    timeBase = DisplayClock.TimeBase.MONOTONIC,
                    nowMs = 1_000L
                )
            )
            assertEquals(0, cameraController.animateCount)

            val fixLocation = LatLng(5.0, 6.0)
            deps.controller.onTimeBaseChanged(fixLocation)
            assertEquals(1, deps.biasResetter.resetCalls)
            assertEquals(1, deps.gate.resetCalls)
            assertEquals(fixLocation, deps.gate.lastResetLocation)

            deps.controller.updateCamera(
                MapTrackingCameraController.FrameInput(
                    location = fixLocation,
                    trackDeg = 90.0,
                    cameraTargetBearing = 15.0,
                    speedMs = 25.0,
                    orientationMode = MapOrientationMode.TRACK_UP,
                    timeBase = DisplayClock.TimeBase.MONOTONIC,
                    nowMs = 1_100L
                )
            )

            assertEquals(1, cameraController.moveCount)
            assertEquals(0, cameraController.animateCount)
            assertEquals(5.0, cameraController.cameraPosition.bearing, 0.0)
        } finally {
            restoreFlags(featureFlags, snapshot)
        }
    }

    @Test
    fun updateCamera_appliesPaddingChange_evenWhenMovementIsGated() {
        val featureFlags = MapFeatureFlags()
        val snapshot = snapshotFlags(featureFlags)
        try {
            featureFlags.useRenderFrameSync = false
            featureFlags.useRuntimeReplayHeading = false

            val store = MapStateStore(initialStyleName = "Topo")
            val actions = MapStateActionsDelegate(store)
            actions.setHasInitiallyCentered(true)
            val cameraController = FakeCameraController(
                initial = MapCameraPositionSnapshot(
                    target = LatLng(0.0, 0.0),
                    zoom = 12.0,
                    bearing = 0.0,
                    tilt = 0.0
                )
            )
            var dynamicPadding = intArrayOf(0, 20, 0, 40)
            val deps = buildDeps(
                store = store,
                actions = actions,
                cameraController = cameraController,
                featureFlags = featureFlags,
                paddingProvider = { dynamicPadding.copyOf() }
            )
            deps.gate.acceptEnabled = false

            deps.controller.updateCamera(
                MapTrackingCameraController.FrameInput(
                    location = LatLng(5.0, 6.0),
                    trackDeg = 90.0,
                    cameraTargetBearing = 0.0,
                    speedMs = 25.0,
                    orientationMode = MapOrientationMode.NORTH_UP,
                    timeBase = DisplayClock.TimeBase.MONOTONIC,
                    nowMs = 1_000L
                )
            )

            val firstPadding = cameraController.lastPadding?.copyOf()
            assertTrue(firstPadding?.contentEquals(intArrayOf(0, 20, 0, 40)) == true)
            assertEquals(0, cameraController.animateCount)

            dynamicPadding = intArrayOf(0, 80, 0, 10)
            deps.controller.updateCamera(
                MapTrackingCameraController.FrameInput(
                    location = LatLng(5.0, 6.0),
                    trackDeg = 90.0,
                    cameraTargetBearing = 0.0,
                    speedMs = 25.0,
                    orientationMode = MapOrientationMode.NORTH_UP,
                    timeBase = DisplayClock.TimeBase.MONOTONIC,
                    nowMs = 1_010L
                )
            )

            val secondPadding = cameraController.lastPadding
            assertTrue(secondPadding != null)
            assertFalse(secondPadding!!.contentEquals(firstPadding))
            assertEquals(0, cameraController.animateCount)
        } finally {
            restoreFlags(featureFlags, snapshot)
        }
    }

    @Test
    fun updateCamera_usesDirectMove_forReplayFollowWhenFrameSyncIsEnabled() {
        val featureFlags = MapFeatureFlags()
        val snapshot = snapshotFlags(featureFlags)
        try {
            featureFlags.useRenderFrameSync = true
            featureFlags.useRuntimeReplayHeading = true

            val store = MapStateStore(initialStyleName = "Topo")
            val actions = MapStateActionsDelegate(store)
            actions.setHasInitiallyCentered(true)
            val cameraController = FakeCameraController(
                initial = MapCameraPositionSnapshot(
                    target = LatLng(0.0, 0.0),
                    zoom = 12.0,
                    bearing = 0.0,
                    tilt = 0.0
                )
            )
            val deps = buildDeps(store, actions, cameraController, featureFlags)

            deps.controller.updateCamera(
                MapTrackingCameraController.FrameInput(
                    location = LatLng(7.0, 8.0),
                    trackDeg = 120.0,
                    cameraTargetBearing = 30.0,
                    speedMs = 25.0,
                    orientationMode = MapOrientationMode.NORTH_UP,
                    timeBase = DisplayClock.TimeBase.REPLAY,
                    nowMs = 1_000L
                )
            )

            assertEquals(1, cameraController.moveCount)
            assertEquals(0, cameraController.animateCount)
            assertEquals(30.0, cameraController.cameraPosition.bearing, 0.0)
        } finally {
            restoreFlags(featureFlags, snapshot)
        }
    }

    private data class ControllerDeps(
        val controller: MapTrackingCameraController,
        val gate: ResettableGate,
        val biasResetter: CountingBiasResetter
    )

    private fun buildDeps(
        store: MapStateStore,
        actions: MapStateActions,
        cameraController: FakeCameraController,
        featureFlags: MapFeatureFlags,
        paddingProvider: () -> IntArray = { PADDING.copyOf() }
    ): ControllerDeps {
        val biasCalculator = MapShiftBiasCalculator()
        val positionController = MapPositionController(locationOverlayPort = NoOpLocationOverlayPort)
        val cameraPolicy = MapCameraPolicy(
            offsetAverager = object : MapCameraPolicy.OffsetAverager {
                override fun remember(topPx: Float, bottomPx: Float) {
                    positionController.rememberOffset(MapPositionController.Offset(topPx, bottomPx))
                }

                override fun averaged(): MapCameraPolicy.AveragedOffset {
                    val averaged = positionController.averagedOffset()
                    return MapCameraPolicy.AveragedOffset(averaged.x, averaged.y)
                }
            },
            biasCalculator = biasCalculator
        )
        val gate = ResettableGate()
        val biasResetter = CountingBiasResetter(biasCalculator)
        val mapSizeProvider = FixedMapSizeProvider()
        val controller = MapTrackingCameraController(
            mapSizeProvider = mapSizeProvider,
            mapStateReader = store,
            stateActions = actions,
            preferenceReader = FakePreferenceReader(),
            paddingProvider = paddingProvider,
            positionController = positionController,
            cameraPolicy = cameraPolicy,
            followCameraMotionPolicy = MapFollowCameraMotionPolicy(),
            cameraUpdateGate = gate,
            biasResetter = biasResetter,
            cameraControllerProvider = { cameraController },
            featureFlags = featureFlags,
            initialZoomLevel = 10.0,
            minUpdateIntervalMs = 80L,
            bearingEpsDeg = 2.0
        )
        return ControllerDeps(controller, gate, biasResetter)
    }

    private data class FlagSnapshot(
        val useRenderFrameSync: Boolean,
        val useRuntimeReplayHeading: Boolean
    )

    private fun snapshotFlags(featureFlags: MapFeatureFlags): FlagSnapshot = FlagSnapshot(
        useRenderFrameSync = featureFlags.useRenderFrameSync,
        useRuntimeReplayHeading = featureFlags.useRuntimeReplayHeading
    )

    private fun restoreFlags(featureFlags: MapFeatureFlags, snapshot: FlagSnapshot) {
        featureFlags.useRenderFrameSync = snapshot.useRenderFrameSync
        featureFlags.useRuntimeReplayHeading = snapshot.useRuntimeReplayHeading
    }

    private class FakePreferenceReader : MapCameraPreferenceReader {
        override fun getMapShiftBiasMode(): MapShiftBiasMode = MapShiftBiasMode.NONE

        override fun getMapShiftBiasStrength(): Double = 0.0

        override fun getGliderScreenPercent(): Int = 35
    }

    private class CountingBiasResetter(
        private val calculator: MapShiftBiasCalculator
    ) : MapShiftBiasResetter {
        var resetCalls: Int = 0

        override fun reset() {
            resetCalls += 1
            calculator.reset()
        }
    }

    private class ResettableGate : MapCameraUpdateGate {
        var acceptEnabled: Boolean = false
        var resetCalls: Int = 0
        var lastResetLocation: LatLng? = null

        override fun accept(location: LatLng): Boolean = acceptEnabled

        override fun resetTo(location: LatLng) {
            resetCalls += 1
            lastResetLocation = location
            acceptEnabled = true
        }
    }

    private class FixedMapSizeProvider : MapViewSizeProvider {
        override fun size(): MapViewSize = MapViewSize(widthPx = 0, heightPx = 0)
    }

    private object NoOpLocationOverlayPort : MapLocationOverlayPort {
        override fun updateBlueLocation(
            location: LatLng,
            trackBearing: Double,
            iconHeading: Double,
            mapBearing: Double,
            orientationMode: MapOrientationMode
        ) = Unit

        override fun setBlueLocationVisible(visible: Boolean) = Unit
    }

    private class FakeCameraController(
        initial: MapCameraPositionSnapshot
    ) : MapCameraController {
        private var position: MapCameraPositionSnapshot = initial
        var moveCount: Int = 0
        var animateCount: Int = 0
        var lastPadding: IntArray? = null

        override val cameraPosition: MapCameraPositionSnapshot
            get() = position

        override fun moveCamera(position: MapCameraPositionSnapshot) {
            moveCount += 1
            this.position = position
        }

        override fun animateCamera(
            position: MapCameraPositionSnapshot,
            durationMs: Int,
            callback: MapCameraController.CancelableCallback?
        ) {
            animateCount += 1
            this.position = position
            callback?.onFinish()
        }

        override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
            lastPadding = intArrayOf(left, top, right, bottom)
        }

        override fun triggerRepaint() = Unit
    }

    private companion object {
        val PADDING: IntArray = intArrayOf(0, 20, 0, 40)
    }
}
