package com.example.xcpro.map

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class MapPositionControllerTest {

    @Test
    fun clampBearingStep_limits_to_max_step_and_wraps() {
        val controller = MapPositionController(
            mapState = mock(),
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
        val controller = MapPositionController(mapState = mock(), offsetHistorySize = 3)

        controller.rememberOffset(MapPositionController.Offset(1f, 2f))
        controller.rememberOffset(MapPositionController.Offset(3f, -1f))
        controller.rememberOffset(MapPositionController.Offset(-2f, 4f))

        val avg = controller.averagedOffset()

        assertEquals(0.6666667f, avg.x, 1e-5f)
        assertEquals(1.6666666f, avg.y, 1e-5f)
    }
}
