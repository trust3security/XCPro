package com.example.xcpro.map

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OgnTrafficOverlayConfigTest {

    @Test
    fun relativeGliderTints_matchBrightRequestedPalette() {
        assertEquals(Color.parseColor("#09CF00"), RELATIVE_GLIDER_ABOVE_TINT)
        assertEquals(Color.parseColor("#0009CF"), RELATIVE_GLIDER_BELOW_TINT)
        assertEquals(Color.parseColor("#101010"), RELATIVE_GLIDER_NEAR_TINT)
    }
}
