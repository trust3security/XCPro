package com.trust3.xcpro.map

import com.trust3.xcpro.common.orientation.MapOrientationMode
import com.trust3.xcpro.map.config.MapFeatureFlags
import com.trust3.xcpro.map.domain.MapShiftBiasCalculator
import com.trust3.xcpro.map.domain.MapShiftBiasMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.maplibre.android.geometry.LatLng

class MapTrackingCameraControllerCadenceTest {

    @Test
    fun updateCamera_closeScaleNorthUp_usesFastPositionCadence() {
        val fixture = createFixture(
            mapViewSize = MapViewSize(widthPx = 1_000, heightPx = 1_000),
            distancePerPixelMetersProvider = { 4.0 }
        )
        fixture.gate.acceptEnabled = true

        fixture.controller.updateCamera(frame(nowMs = 1_000L, orientationMode = MapOrientationMode.NORTH_UP))
        fixture.controller.updateCamera(
            frame(
                nowMs = 1_030L,
                location = LatLng(0.0001, 0.0),
                orientationMode = MapOrientationMode.NORTH_UP
            )
        )

        assertEquals(2, fixture.cameraController.moveCount)
    }

    @Test
    fun updateCamera_farScaleLiveTracking_keepsNormalPositionCadence() {
        val fixture = createFixture(
            mapViewSize = MapViewSize(widthPx = 1_000, heightPx = 1_000),
            distancePerPixelMetersProvider = { 8.0 }
        )
        fixture.gate.acceptEnabled = true

        fixture.controller.updateCamera(frame(nowMs = 1_000L, orientationMode = MapOrientationMode.NORTH_UP))
        fixture.controller.updateCamera(
            frame(
                nowMs = 1_030L,
                location = LatLng(0.0001, 0.0),
                orientationMode = MapOrientationMode.NORTH_UP
            )
        )

        assertEquals(1, fixture.cameraController.moveCount)
    }

    @Test
    fun updateCamera_closeScaleTrackUp_usesSmallBearingDeadband() {
        val fixture = createFixture(
            mapViewSize = MapViewSize(widthPx = 1_000, heightPx = 1_000),
            distancePerPixelMetersProvider = { 4.0 }
        )
        fixture.gate.acceptEnabled = true

        fixture.controller.updateCamera(frame(nowMs = 1_000L, orientationMode = MapOrientationMode.TRACK_UP))
        fixture.gate.acceptEnabled = false
        fixture.controller.updateCamera(
            frame(
                nowMs = 1_010L,
                cameraTargetBearing = 1.0,
                orientationMode = MapOrientationMode.TRACK_UP
            )
        )

        assertEquals(2, fixture.cameraController.moveCount)
        assertEquals(1.0, fixture.cameraController.cameraPosition.bearing, 0.0)
    }

    private fun createFixture(
        mapViewSize: MapViewSize,
        distancePerPixelMetersProvider: (Double) -> Double?
    ): Fixture {
        val store = MapStateStore(initialStyleName = "Topo")
        val actions = MapStateActionsDelegate(store)
        actions.setHasInitiallyCentered(true)
        val cameraController = FakeCameraController(
            initial = MapCameraPositionSnapshot(
                target = LatLng(0.0, 0.0),
                zoom = 14.0,
                bearing = 0.0,
                tilt = 0.0
            )
        )
        val positionController = MapPositionController(locationOverlayPort = NoOpLocationOverlayPort)
        val biasCalculator = MapShiftBiasCalculator()
        val gate = ResettableGate()
        val controller = MapTrackingCameraController(
            mapSizeProvider = FixedMapSizeProvider(mapViewSize),
            mapStateReader = store,
            stateActions = actions,
            preferenceReader = FakePreferenceReader(),
            paddingProvider = { PADDING.copyOf() },
            positionController = positionController,
            cameraPolicy = MapCameraPolicy(
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
            ),
            followCameraMotionPolicy = MapFollowCameraMotionPolicy(),
            cameraUpdateGate = gate,
            biasResetter = MapShiftBiasResetterAdapter(biasCalculator),
            cameraControllerProvider = { cameraController },
            distancePerPixelMetersProvider = distancePerPixelMetersProvider,
            followCameraCadencePolicy = MapFollowCameraCadencePolicy(),
            featureFlags = MapFeatureFlags(),
            initialZoomLevel = 10.0
        )
        return Fixture(
            controller = controller,
            gate = gate,
            cameraController = cameraController
        )
    }

    private fun frame(
        nowMs: Long,
        location: LatLng = LatLng(5.0, 6.0),
        cameraTargetBearing: Double = 0.0,
        orientationMode: MapOrientationMode
    ): MapTrackingCameraController.FrameInput {
        return MapTrackingCameraController.FrameInput(
            location = location,
            trackDeg = cameraTargetBearing,
            cameraTargetBearing = cameraTargetBearing,
            speedMs = 25.0,
            orientationMode = orientationMode,
            timeBase = DisplayClock.TimeBase.MONOTONIC,
            nowMs = nowMs
        )
    }

    private data class Fixture(
        val controller: MapTrackingCameraController,
        val gate: ResettableGate,
        val cameraController: FakeCameraController
    )

    private class ResettableGate : MapCameraUpdateGate {
        var acceptEnabled: Boolean = false

        override fun accept(location: LatLng): Boolean = acceptEnabled

        override fun resetTo(location: LatLng) {
            acceptEnabled = true
        }
    }

    private class FixedMapSizeProvider(
        private val size: MapViewSize
    ) : MapViewSizeProvider {
        override fun size(): MapViewSize = size
    }

    private class FakePreferenceReader : MapCameraPreferenceReader {
        override fun getMapShiftBiasMode(): MapShiftBiasMode = MapShiftBiasMode.NONE
        override fun getMapShiftBiasStrength(): Double = 0.0
        override fun getGliderScreenPercent(): Int = 35
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
            this.position = position
            callback?.onFinish()
        }

        override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) = Unit

        override fun triggerRepaint() = Unit
    }

    private companion object {
        val PADDING: IntArray = intArrayOf(0, 20, 0, 40)
    }
}
