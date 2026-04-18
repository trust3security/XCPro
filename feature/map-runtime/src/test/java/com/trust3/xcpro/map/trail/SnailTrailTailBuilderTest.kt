package com.trust3.xcpro.map.trail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class SnailTrailTailBuilderTest {

    @Test
    fun build_returnsNull_whenSegmentTooShort() {
        val builder = SnailTrailTailBuilder(RecordingClipper(result = TrailGeoPoint(0.0, 0.0)))
        val input = baseInput(
            lastPoint = renderPoint(0.0, 0.0, vario = -1.0),
            currentLocation = TrailGeoPoint(0.0, 0.0),
            metersPerPixel = 0.0
        )

        val segment = builder.build(input)

        assertNull(segment)
    }

    @Test
    fun build_returnsNull_whenClipperReturnsNull() {
        val builder = SnailTrailTailBuilder(RecordingClipper(result = null))
        val input = baseInput(
            lastPoint = renderPoint(0.0, 0.0, vario = -1.0),
            currentLocation = TrailGeoPoint(0.0, 0.01),
            metersPerPixel = 1.0
        )

        val segment = builder.build(input)

        assertNull(segment)
    }

    @Test
    fun build_usesScaledWidth_whenEnabled() {
        val clipper = RecordingClipper(result = TrailGeoPoint(0.0, 0.01))
        val builder = SnailTrailTailBuilder(clipper)
        val input = baseInput(
            lastPoint = renderPoint(0.0, 0.0, vario = -2.0),
            currentLocation = TrailGeoPoint(0.0, 0.01),
            metersPerPixel = 1.0
        )

        val segment = builder.build(input)

        assertNotNull(segment)
        assertEquals(5f, segment?.width ?: -1f, 0f)
    }

    @Test
    fun build_appliesTailOffsetBeforeClipping() {
        val clipper = RecordingClipper(result = TrailGeoPoint(0.0, 0.0))
        val builder = SnailTrailTailBuilder(clipper)
        val currentLocation = TrailGeoPoint(0.0, 0.01)
        val metersPerPixel = 2.0
        val iconSizePx = 100f

        val input = baseInput(
            lastPoint = renderPoint(0.0, 0.0, vario = -1.0),
            currentLocation = currentLocation,
            metersPerPixel = metersPerPixel,
            iconSizePx = iconSizePx
        )

        builder.build(input)

        val tailOffsetMeters = iconSizePx * SnailTrailTailBuilder.TAIL_OFFSET_FRACTION * metersPerPixel
        val expected = TrailGeo.destinationPoint(
            currentLocation.latitude,
            currentLocation.longitude,
            270.0,
            tailOffsetMeters
        )
        val recorded = clipper.lastEnd
        assertNotNull(recorded)
        assertEquals(expected.first, recorded?.latitude ?: 0.0, 1e-6)
        assertEquals(expected.second, recorded?.longitude ?: 0.0, 1e-6)
    }

    private fun baseInput(
        lastPoint: RenderPoint,
        currentLocation: TrailGeoPoint,
        metersPerPixel: Double,
        iconSizePx: Float = 100f
    ): SnailTrailTailBuilder.Input {
        return SnailTrailTailBuilder.Input(
            lastPoint = lastPoint,
            settings = TrailSettings(type = TrailType.VARIO_1),
            currentLocation = currentLocation,
            currentTimeMillis = 1_000L,
            styleCache = styleCache(),
            metersPerPixel = metersPerPixel,
            iconSizePx = iconSizePx
        )
    }

    private fun styleCache(): SnailTrailStyleCache {
        val widths = FloatArray(SnailTrailPalette.NUM_COLORS) { index ->
            if (index == 0) 5f else 2f
        }
        return SnailTrailStyleCache(
            type = TrailType.VARIO_1,
            valueMin = -2.0,
            valueMax = 2.0,
            useScaledLines = true,
            scaledWidths = widths,
            minWidth = 1f
        )
    }

    private fun renderPoint(lat: Double, lon: Double, vario: Double): RenderPoint = RenderPoint(
        latitude = lat,
        longitude = lon,
        altitudeMeters = 1000.0,
        varioMs = vario,
        timestampMillis = 500L
    )

    private class RecordingClipper(
        private val result: TrailGeoPoint?
    ) : TailClipper {
        var lastEnd: TrailGeoPoint? = null

        override fun clipToClearance(start: RenderPoint, end: TrailGeoPoint, clearancePx: Float): TrailGeoPoint? {
            lastEnd = end
            return result
        }
    }
}
