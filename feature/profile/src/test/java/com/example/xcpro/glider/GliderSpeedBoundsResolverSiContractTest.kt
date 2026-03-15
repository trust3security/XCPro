package com.example.xcpro.glider

import com.example.xcpro.common.glider.GliderConfig
import com.example.xcpro.common.glider.GliderModel
import com.example.xcpro.common.glider.PolarPoint
import com.example.xcpro.common.glider.SpeedLimits
import com.example.xcpro.common.units.UnitsConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GliderSpeedBoundsResolverSiContractTest {

    @Test
    fun resolveIasBoundsMs_usesCanonicalSiConfigAndClampsWithSpeedLimitBoundary() {
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
        val config = GliderConfig(
            iasMinMs = UnitsConverter.kmhToMs(80.0),
            iasMaxMs = UnitsConverter.kmhToMs(180.0)
        )

        val bounds = GliderSpeedBoundsResolver.resolveIasBoundsMs(model, config)

        assertNotNull(bounds)
        assertEquals(UnitsConverter.kmhToMs(80.0), bounds?.minMs ?: Double.NaN, 1e-6)
        assertEquals(UnitsConverter.kmhToMs(150.0), bounds?.maxMs ?: Double.NaN, 1e-6)
    }
}
