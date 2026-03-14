package com.example.xcpro.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.maplibre.android.geometry.LatLng
import com.example.xcpro.common.orientation.MapOrientationMode
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MapPositionControllerTest {

    @Test
    fun clampBearingStep_limits_to_max_step_and_wraps() {
        val controller = MapPositionController(
            locationOverlayPort = mock(),
            maxBearingStepDegProvider = { 5.0 }
        )

        // Seed bearing near wrap boundary.
        controller.clampBearingStep(350.0) // moves to 355
        controller.clampBearingStep(350.0) // settles at 350

        val clamped = controller.clampBearingStep(10.0)

        // Crossing 350 -> 10 should step forward only 5 degrees (355).
        assertEquals(355.0, clamped, 1e-6)
    }

    @Test
    fun averagedOffset_returns_mean_of_history() {
        val controller = MapPositionController(locationOverlayPort = mock(), offsetHistorySize = 3)

        controller.rememberOffset(MapPositionController.Offset(1f, 2f))
        controller.rememberOffset(MapPositionController.Offset(3f, -1f))
        controller.rememberOffset(MapPositionController.Offset(-2f, 4f))

        val avg = controller.averagedOffset()

        assertEquals(0.6666667f, avg.x, 1e-5f)
        assertEquals(1.6666666f, avg.y, 1e-5f)
    }

    @Test
    fun updateOverlay_doesNotForceVisibilityOnEveryFrame() {
        val overlayPort = RecordingLocationOverlayPort()
        val controller = MapPositionController(locationOverlayPort = overlayPort)

        controller.updateOverlay(
            location = LatLng(-35.0, 149.0),
            trackBearing = 90.0,
            headingDeg = 92.0,
            headingValid = true,
            bearingAccuracyDeg = 2.0,
            speedAccuracyMs = 0.3,
            mapBearing = 15.0,
            orientationMode = MapOrientationMode.NORTH_UP,
            speedMs = 20.0,
            nowMs = 1_000L
        )

        assertEquals(1, overlayPort.updateCalls)
        assertTrue(overlayPort.visibleCalls.isEmpty())
    }

    private class RecordingLocationOverlayPort : MapLocationOverlayPort {
        var updateCalls: Int = 0
        val visibleCalls = mutableListOf<Boolean>()

        override fun updateBlueLocation(
            location: LatLng,
            trackBearing: Double,
            iconHeading: Double,
            mapBearing: Double,
            orientationMode: MapOrientationMode
        ) {
            updateCalls += 1
        }

        override fun setBlueLocationVisible(visible: Boolean) {
            visibleCalls += visible
        }
    }
}
