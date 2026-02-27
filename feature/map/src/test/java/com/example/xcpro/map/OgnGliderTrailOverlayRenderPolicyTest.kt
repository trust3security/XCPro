package com.example.xcpro.map

import com.example.xcpro.ogn.OgnGliderTrailSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OgnGliderTrailOverlayRenderPolicyTest {

    @Test
    fun trimSegmentsForRender_keepsAllWhenWithinCap() {
        val segments = List(3, ::segment)

        val result = OgnGliderTrailOverlay.trimSegmentsForRender(
            segments = segments,
            maxSegments = 3
        )

        assertEquals(3, result.size)
        assertEquals("id-0", result.first().id)
        assertEquals("id-2", result.last().id)
    }

    @Test
    fun trimSegmentsForRender_keepsNewestWhenOverCap() {
        val segments = List(5, ::segment)

        val result = OgnGliderTrailOverlay.trimSegmentsForRender(
            segments = segments,
            maxSegments = 3
        )

        assertEquals(3, result.size)
        assertEquals(listOf("id-2", "id-3", "id-4"), result.map { it.id })
    }

    @Test
    fun trimSegmentsForRender_returnsEmptyWhenCapIsNonPositive() {
        val segments = List(5, ::segment)

        val result = OgnGliderTrailOverlay.trimSegmentsForRender(
            segments = segments,
            maxSegments = 0
        )

        assertTrue(result.isEmpty())
    }

    private fun segment(index: Int): OgnGliderTrailSegment = OgnGliderTrailSegment(
        id = "id-$index",
        sourceTargetId = "target",
        sourceLabel = "target",
        startLatitude = -35.0 + index,
        startLongitude = 149.0 + index,
        endLatitude = -34.5 + index,
        endLongitude = 149.5 + index,
        colorIndex = index,
        widthPx = 2.0f,
        timestampMonoMs = index.toLong()
    )
}
