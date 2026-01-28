package com.example.xcpro.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.geometry.LatLng

class MapUserInteractionControllerTest {

    private val noCameraProvider = object : MapCameraControllerProvider {
        override fun controllerOrNull(): MapCameraController? = null
    }
    private val padding = intArrayOf(1, 2, 3, 4)

    @Test
    fun showReturnButton_updates_state_and_timestamp() {
        val store = MapStateStore(initialStyleName = "Topo")
        val actions = MapStateActionsDelegate(store)
        val controller = MapUserInteractionController(
            mapStateReader = store,
            stateActions = actions,
            paddingProvider = { intArrayOf(0, 0, 0, 0) },
            cameraControllerProvider = noCameraProvider,
            logTag = "test",
            nowWallMs = { 1234L }
        )

        controller.showReturnButton()

        assertTrue(store.showReturnButton.value)
        assertEquals(1234L, store.lastUserPanTime.value)
    }

    @Test
    fun handleUserInteraction_saves_zoom_and_bearing_before_first_pan() {
        val store = MapStateStore(initialStyleName = "Topo")
        val actions = MapStateActionsDelegate(store)
        actions.saveLocation(MapStateStore.MapPoint(1.0, 2.0), 11.0, 22.0)
        val controller = MapUserInteractionController(
            mapStateReader = store,
            stateActions = actions,
            paddingProvider = { intArrayOf(0, 0, 0, 0) },
            cameraControllerProvider = noCameraProvider,
            logTag = "test",
            nowWallMs = { 2000L }
        )

        controller.handleUserInteraction(
            currentLocation = null,
            currentZoom = 33.0,
            currentBearing = 44.0
        )

        val savedZoom = requireNotNull(store.savedZoom.value)
        val savedBearing = requireNotNull(store.savedBearing.value)
        assertEquals(33.0, savedZoom, 0.0)
        assertEquals(44.0, savedBearing, 0.0)
        assertTrue(store.showReturnButton.value)
        assertEquals(2000L, store.lastUserPanTime.value)
    }

    @Test
    fun handleUserInteraction_does_not_overwrite_when_return_visible() {
        val store = MapStateStore(initialStyleName = "Topo")
        val actions = MapStateActionsDelegate(store)
        actions.saveLocation(MapStateStore.MapPoint(1.0, 2.0), 11.0, 22.0)
        actions.setShowReturnButton(true)
        val controller = MapUserInteractionController(
            mapStateReader = store,
            stateActions = actions,
            paddingProvider = { intArrayOf(0, 0, 0, 0) },
            cameraControllerProvider = noCameraProvider,
            logTag = "test",
            nowWallMs = { 3000L }
        )

        controller.handleUserInteraction(
            currentLocation = null,
            currentZoom = 33.0,
            currentBearing = 44.0
        )

        val savedZoom = requireNotNull(store.savedZoom.value)
        val savedBearing = requireNotNull(store.savedBearing.value)
        assertEquals(11.0, savedZoom, 0.0)
        assertEquals(22.0, savedBearing, 0.0)
        assertTrue(store.showReturnButton.value)
        assertEquals(3000L, store.lastUserPanTime.value)
    }

    @Test
    fun returnToSavedLocation_pausesTracking_untilAnimationFinishes() {
        val store = MapStateStore(initialStyleName = "Topo")
        val actions = MapStateActionsDelegate(store)
        actions.saveLocation(MapStateStore.MapPoint(10.0, 20.0), 11.0, 22.0)
        actions.setShowReturnButton(true)
        actions.setShowRecenterButton(true)
        val fakeCamera = FakeCameraController(
            initial = MapCameraPositionSnapshot(
                target = LatLng(0.0, 0.0),
                zoom = 7.0,
                bearing = 5.0,
                tilt = 3.0
            )
        )
        val controller = MapUserInteractionController(
            mapStateReader = store,
            stateActions = actions,
            paddingProvider = { padding.copyOf() },
            cameraControllerProvider = FakeCameraProvider(fakeCamera),
            logTag = "test"
        )

        val returned = controller.returnToSavedLocation()

        assertTrue(returned)
        assertFalse(store.isTrackingLocation.value)
        assertFalse(store.showReturnButton.value)
        assertFalse(store.showRecenterButton.value)
        assertEquals(1000, fakeCamera.lastDurationMs)
        val target = requireNotNull(fakeCamera.cameraPosition.target)
        assertEquals(10.0, target.latitude, 0.0)
        assertEquals(20.0, target.longitude, 0.0)
        assertEquals(11.0, fakeCamera.cameraPosition.zoom, 0.0)
        assertEquals(22.0, fakeCamera.cameraPosition.bearing, 0.0)
        assertTrue(fakeCamera.lastPadding?.contentEquals(padding) == true)

        fakeCamera.lastCallback?.onFinish()

        assertTrue(store.isTrackingLocation.value)
    }

    @Test
    fun returnToSavedLocation_restoresTracking_onCancel() {
        val store = MapStateStore(initialStyleName = "Topo")
        val actions = MapStateActionsDelegate(store)
        actions.saveLocation(MapStateStore.MapPoint(10.0, 20.0), 11.0, 22.0)
        val fakeCamera = FakeCameraController(
            initial = MapCameraPositionSnapshot(
                target = LatLng(0.0, 0.0),
                zoom = 7.0,
                bearing = 5.0,
                tilt = 3.0
            )
        )
        val controller = MapUserInteractionController(
            mapStateReader = store,
            stateActions = actions,
            paddingProvider = { padding.copyOf() },
            cameraControllerProvider = FakeCameraProvider(fakeCamera),
            logTag = "test"
        )

        controller.returnToSavedLocation()
        assertFalse(store.isTrackingLocation.value)

        fakeCamera.lastCallback?.onCancel()

        assertTrue(store.isTrackingLocation.value)
    }

    @Test
    fun recenterOnCurrentLocation_animates_and_hides_recenter() {
        val store = MapStateStore(initialStyleName = "Topo")
        val actions = MapStateActionsDelegate(store)
        actions.setCurrentUserLocation(MapStateStore.MapPoint(3.0, 4.0))
        actions.setShowRecenterButton(true)
        val fakeCamera = FakeCameraController(
            initial = MapCameraPositionSnapshot(
                target = LatLng(0.0, 0.0),
                zoom = 9.0,
                bearing = 33.0,
                tilt = 4.0
            )
        )
        val controller = MapUserInteractionController(
            mapStateReader = store,
            stateActions = actions,
            paddingProvider = { padding.copyOf() },
            cameraControllerProvider = FakeCameraProvider(fakeCamera),
            logTag = "test"
        )

        controller.recenterOnCurrentLocation()

        assertEquals(800, fakeCamera.lastDurationMs)
        val target = requireNotNull(fakeCamera.cameraPosition.target)
        assertEquals(3.0, target.latitude, 0.0)
        assertEquals(4.0, target.longitude, 0.0)
        assertEquals(9.0, fakeCamera.cameraPosition.zoom, 0.0)
        assertEquals(33.0, fakeCamera.cameraPosition.bearing, 0.0)
        assertEquals(4.0, fakeCamera.cameraPosition.tilt, 0.0)
        assertTrue(fakeCamera.lastPadding?.contentEquals(padding) == true)
        assertFalse(store.showRecenterButton.value)
    }

    private class FakeCameraProvider(
        private val cameraController: MapCameraController?
    ) : MapCameraControllerProvider {
        override fun controllerOrNull(): MapCameraController? = cameraController
    }

    private class FakeCameraController(
        initial: MapCameraPositionSnapshot
    ) : MapCameraController {
        private var position: MapCameraPositionSnapshot = initial
        var lastPadding: IntArray? = null
        var lastDurationMs: Int = -1
        var lastCallback: MapCameraController.CancelableCallback? = null

        override val cameraPosition: MapCameraPositionSnapshot
            get() = position

        override fun moveCamera(position: MapCameraPositionSnapshot) {
            this.position = position
        }

        override fun animateCamera(
            position: MapCameraPositionSnapshot,
            durationMs: Int,
            callback: MapCameraController.CancelableCallback?
        ) {
            this.position = position
            lastDurationMs = durationMs
            lastCallback = callback
        }

        override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
            lastPadding = intArrayOf(left, top, right, bottom)
        }

        override fun triggerRepaint() = Unit
    }
}
