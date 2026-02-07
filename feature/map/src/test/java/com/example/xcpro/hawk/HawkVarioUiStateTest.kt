package com.example.xcpro.hawk

import org.junit.Assert.assertEquals
import org.junit.Test

class HawkVarioUiStateTest {

    @Test
    fun formatCenterValue_returnsPlaceholderWhenNull() {
        val state = HawkVarioUiState(varioSmoothedMps = null)

        assertEquals("--.- m/s", state.formatCenterValue())
    }

    @Test
    fun formatCenterValue_clampsSmallValuesToZero() {
        val state = HawkVarioUiState(varioSmoothedMps = 0.03f)

        assertEquals("+0.0 m/s", state.formatCenterValue())
    }

    @Test
    fun statusLine_reflectsAccelBaroAndConfidence() {
        val state = HawkVarioUiState(
            accelOk = true,
            baroOk = false,
            confidence = HawkConfidence.LEVEL4
        )

        assertEquals("ACCEL OK BARO DEG CONF 4", state.statusLine)
    }
}
