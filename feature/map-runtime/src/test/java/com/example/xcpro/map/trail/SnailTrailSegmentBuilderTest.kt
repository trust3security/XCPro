package com.example.xcpro.map.trail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnailTrailSegmentBuilderTest {

    @Test
    fun build_altitudeCreatesLineSegments() {
        val builder = SnailTrailSegmentBuilder(AlwaysInsideBounds())
        val settings = TrailSettings(type = TrailType.ALTITUDE)
        val style = styleCache(type = settings.type)

        val plan = builder.build(
            points = listOf(renderPoint(1.0), renderPoint(2.0)),
            settings = settings,
            styleCache = style,
            skipBoundsCheck = false,
            includeLogs = true
        )

        assertEquals(1, plan.lineSegments.size)
        assertEquals(0, plan.dotSegments.size)
        assertEquals(1, plan.logEntries.size)
        assertEquals("line", plan.logEntries.first().kind)
    }

    @Test
    fun build_varioDots_negativeVarioCreatesDot() {
        val builder = SnailTrailSegmentBuilder(AlwaysInsideBounds())
        val settings = TrailSettings(type = TrailType.VARIO_1_DOTS)
        val style = styleCache(type = settings.type)

        val plan = builder.build(
            points = listOf(renderPoint(1.0, vario = 0.1), renderPoint(2.0, vario = -0.3)),
            settings = settings,
            styleCache = style,
            skipBoundsCheck = false,
            includeLogs = true
        )

        assertEquals(0, plan.lineSegments.size)
        assertEquals(1, plan.dotSegments.size)
        assertEquals("dot", plan.logEntries.first().kind)
    }

    @Test
    fun build_varioDotsAndLines_positiveVarioCreatesDotAndLine() {
        val builder = SnailTrailSegmentBuilder(AlwaysInsideBounds())
        val settings = TrailSettings(type = TrailType.VARIO_DOTS_AND_LINES)
        val style = styleCache(type = settings.type)

        val plan = builder.build(
            points = listOf(renderPoint(1.0, vario = 0.1), renderPoint(2.0, vario = 1.5)),
            settings = settings,
            styleCache = style,
            skipBoundsCheck = false,
            includeLogs = true
        )

        assertEquals(1, plan.lineSegments.size)
        assertEquals(1, plan.dotSegments.size)
        assertEquals("dot+line", plan.logEntries.first().kind)
        assertTrue(plan.logEntries.first().width != null)
        assertTrue(plan.logEntries.first().radius != null)
    }

    @Test
    fun build_skipBoundsCheck_buildsSegmentsEvenWhenOutside() {
        val builder = SnailTrailSegmentBuilder(AlwaysOutsideBounds())
        val settings = TrailSettings(type = TrailType.VARIO_1)
        val style = styleCache(type = settings.type)

        val plan = builder.build(
            points = listOf(renderPoint(1.0), renderPoint(2.0)),
            settings = settings,
            styleCache = style,
            skipBoundsCheck = true,
            includeLogs = true
        )

        assertEquals(1, plan.lineSegments.size)
    }

    private fun styleCache(type: TrailType): SnailTrailStyleCache {
        val widths = FloatArray(SnailTrailPalette.NUM_COLORS) { 2f }
        return SnailTrailStyleCache(
            type = type,
            valueMin = -5.0,
            valueMax = 5.0,
            useScaledLines = true,
            scaledWidths = widths,
            minWidth = 1f
        )
    }

    private fun renderPoint(value: Double, vario: Double = 0.5): RenderPoint = RenderPoint(
        latitude = value,
        longitude = value,
        altitudeMeters = 1000.0,
        varioMs = vario,
        timestampMillis = (value * 1000).toLong()
    )

    private class AlwaysInsideBounds : SnailTrailSegmentBuilder.BoundsChecker {
        override fun isInside(point: RenderPoint): Boolean = true
    }

    private class AlwaysOutsideBounds : SnailTrailSegmentBuilder.BoundsChecker {
        override fun isInside(point: RenderPoint): Boolean = false
    }
}
