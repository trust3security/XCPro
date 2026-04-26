package com.trust3.xcpro.glider

import com.trust3.xcpro.common.glider.GliderConfig
import com.trust3.xcpro.common.glider.GliderModel
import com.trust3.xcpro.common.glider.PolarPoint
import com.trust3.xcpro.common.glider.SpeedLimits
import com.trust3.xcpro.common.units.UnitsConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GliderSpeedBoundsResolverSiContractTest {

    @Test
    fun resolveIasBoundsMs_usesPolarRangeAndClampsWithSpeedLimitBoundary() {
        val model = GliderModel(
            id = "test",
            name = "test",
            classLabel = "open",
            points = listOf(
                PolarPoint.fromKmh(70.0, 0.6),
                PolarPoint.fromKmh(190.0, 2.2)
            ),
            speedLimits = SpeedLimits(vneKmh = 150)
        )

        val bounds = GliderSpeedBoundsResolver.resolveIasBoundsMs(model, GliderConfig())

        assertNotNull(bounds)
        assertEquals(UnitsConverter.kmhToMs(70.0), bounds?.minMs ?: Double.NaN, 1e-6)
        assertEquals(UnitsConverter.kmhToMs(150.0), bounds?.maxMs ?: Double.NaN, 1e-6)
    }

    @Test
    fun resolveIasBoundsMs_ignoresTowAndWinchLimitsForPolarScanRange() {
        val model = GliderModel(
            id = "test",
            name = "test",
            classLabel = "open",
            points = listOf(
                PolarPoint.fromKmh(80.0, 0.6),
                PolarPoint.fromKmh(240.0, 2.2)
            ),
            speedLimits = SpeedLimits(
                vneKmh = 280,
                vraKmh = 260,
                vaKmh = 250,
                vwKmh = 90,
                vtKmh = 70
            )
        )

        val bounds = GliderSpeedBoundsResolver.resolveIasBoundsMs(model, GliderConfig())

        assertNotNull(bounds)
        assertEquals(UnitsConverter.kmhToMs(80.0), bounds?.minMs ?: Double.NaN, 1e-6)
        assertEquals(UnitsConverter.kmhToMs(240.0), bounds?.maxMs ?: Double.NaN, 1e-6)
    }
}
