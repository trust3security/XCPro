package com.trust3.xcpro.glider

import com.trust3.xcpro.common.glider.GliderConfig
import com.trust3.xcpro.common.glider.GliderModel
import com.trust3.xcpro.common.glider.PolarPoint
import com.trust3.xcpro.common.units.UnitsConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlidePolarMetricsResolverTest {

    @Test
    fun ldAtSpeed_uses_active_polar_curve() {
        val model = GliderModel(
            id = "test",
            name = "test",
            classLabel = "club",
            points = listOf(
                PolarPoint.fromKmh(100.0, 1.0),
                PolarPoint.fromKmh(120.0, 2.0)
            )
        )

        val ld = GlidePolarMetricsResolver.ldAtSpeed(
            indicatedAirspeedMs = UnitsConverter.kmhToMs(110.0),
            model = model,
            config = GliderConfig()
        )

        assertEquals(UnitsConverter.kmhToMs(110.0) / 1.5, ld ?: 0.0, 1e-6)
    }

    @Test
    fun deriveBestLd_scans_active_polar_bounds() {
        val model = GliderModel(
            id = "best",
            name = "best",
            classLabel = "club",
            points = listOf(
                PolarPoint.fromKmh(100.0, 1.0),
                PolarPoint.fromKmh(120.0, 2.0)
            )
        )

        val derived = GlidePolarMetricsResolver.deriveBestLd(
            model = model,
            config = GliderConfig()
        )

        assertTrue((derived.bestLd ?: 0.0) > 27.0)
        assertEquals(UnitsConverter.kmhToMs(100.0), derived.bestLdSpeedMs ?: 0.0, 0.6)
    }

    @Test
    fun deriveBestLd_returns_null_without_usable_polar() {
        val model = GliderModel(
            id = "empty",
            name = "empty",
            classLabel = "club"
        )

        val derived = GlidePolarMetricsResolver.deriveBestLd(
            model = model,
            config = GliderConfig()
        )

        assertNull(derived.bestLd)
        assertNull(derived.bestLdSpeedMs)
    }
}
