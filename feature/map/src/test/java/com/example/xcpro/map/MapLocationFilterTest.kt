package com.example.xcpro.map

import android.graphics.PointF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

class MapLocationFilterTest {

    private class FakeProjector(private val points: Iterator<Pair<Float, Float>>) : ScreenProjector {
        private var call = 0
        override fun toScreenPoint(map: MapLibreMap, latLng: LatLng): PointF? {
            if (!points.hasNext()) return null
            val (x, y) = points.next()
            val p = PointF().apply {
                this.x = x
                this.y = y
            }
            call++
            return p
        }
    }

    @Test
    fun rejects_moves_below_threshold() {
        val projector = FakeProjector(listOf(0f to 0f, 0.3f to 0f).iterator())
        val filter = MapLocationFilter(
            MapLocationFilter.Config(thresholdPx = 0.5f, historySize = 4),
            projector
        )
        val map = org.mockito.kotlin.mock<MapLibreMap>()

        assertTrue(filter.accept(LatLng(), map)) // seed
        assertFalse(filter.accept(LatLng(), map)) // below 0.5px
        val stats = filter.stats()
        assertEquals(1, stats.accepted)
        assertEquals(1, stats.rejected)
    }

    @Test
    fun accepts_moves_above_threshold() {
        val projector = FakeProjector(listOf(0f to 0f, 1.0f to 0f).iterator())
        val filter = MapLocationFilter(
            MapLocationFilter.Config(thresholdPx = 0.5f, historySize = 4),
            projector
        )
        val map = org.mockito.kotlin.mock<MapLibreMap>()

        assertTrue(filter.accept(LatLng(), map)) // seed
        val second = filter.accept(LatLng(), map)
        assertTrue(second) // >0.5px
        val stats = filter.stats()
        assertEquals(2, stats.accepted)
        assertEquals(0, stats.rejected)
    }

    @Test
    fun projection_failure_allows_update() {
        val projector = object : ScreenProjector {
            override fun toScreenPoint(map: MapLibreMap, latLng: LatLng): PointF? = null
        }
        val filter = MapLocationFilter(
            MapLocationFilter.Config(thresholdPx = 0.5f, historySize = 4),
            projector
        )
        val map = org.mockito.kotlin.mock<MapLibreMap>()

        assertTrue(filter.accept(LatLng(), map))
    }
}
