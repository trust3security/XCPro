package com.example.xcpro.map

import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficScreenDeclutterEngineTest {

    @Test
    fun layout_sameInput_returnsDeterministicOffsets() {
        val engine = TrafficScreenDeclutterEngine()
        val targets = listOf(
            target(key = "a", x = 100f, y = 100f),
            target(key = "b", x = 101f, y = 100f),
            target(key = "c", x = 100.5f, y = 101f)
        )

        val first = engine.layout(targets = targets, strengthMultiplier = 1f)
        val second = engine.layout(targets = targets, strengthMultiplier = 1f)

        assertEquals(first.offsetsByKey, second.offsetsByKey)
    }

    @Test
    fun layout_isolatedTarget_keepsZeroOffset() {
        val engine = TrafficScreenDeclutterEngine()

        val result = engine.layout(
            targets = listOf(target(key = "solo", x = 100f, y = 100f)),
            strengthMultiplier = 1f
        )

        assertEquals(TrafficDisplayOffset.Zero, result.offsetFor("solo"))
    }

    @Test
    fun layout_collisionGroups_assignOffsetsForTwoThreeAndFourAircraft() {
        val engine = TrafficScreenDeclutterEngine()

        val two = engine.layout(
            targets = listOf(
                target(key = "a", x = 100f, y = 100f),
                target(key = "b", x = 100f, y = 100f)
            ),
            strengthMultiplier = 1f
        )
        val three = engine.layout(
            targets = listOf(
                target(key = "a", x = 100f, y = 100f),
                target(key = "b", x = 100f, y = 100f),
                target(key = "c", x = 100f, y = 100f)
            ),
            strengthMultiplier = 1f
        )
        val four = engine.layout(
            targets = listOf(
                target(key = "a", x = 100f, y = 100f),
                target(key = "b", x = 100f, y = 100f),
                target(key = "c", x = 100f, y = 100f),
                target(key = "d", x = 100f, y = 100f)
            ),
            strengthMultiplier = 1f
        )

        assertEquals(TrafficDisplayOffset.Zero, two.offsetFor("a"))
        assertTrue(two.offsetFor("b") != TrafficDisplayOffset.Zero)
        assertEquals(TrafficDisplayOffset.Zero, three.offsetFor("a"))
        assertTrue(three.offsetFor("b") != TrafficDisplayOffset.Zero)
        assertTrue(three.offsetFor("c") != TrafficDisplayOffset.Zero)
        assertEquals(TrafficDisplayOffset.Zero, four.offsetFor("a"))
        assertTrue(four.offsetFor("b") != TrafficDisplayOffset.Zero)
        assertTrue(four.offsetFor("c") != TrafficDisplayOffset.Zero)
        assertTrue(four.offsetFor("d") != TrafficDisplayOffset.Zero)
    }

    @Test
    fun layout_identicalPriorities_usesStableKeyTieBreak() {
        val engine = TrafficScreenDeclutterEngine()
        val forward = listOf(
            target(key = "a", x = 100f, y = 100f),
            target(key = "b", x = 100f, y = 100f),
            target(key = "c", x = 100f, y = 100f)
        )
        val reversed = forward.reversed()

        val first = engine.layout(targets = forward, strengthMultiplier = 1f)
        val second = engine.layout(targets = reversed, strengthMultiplier = 1f)

        assertEquals(TrafficDisplayOffset.Zero, first.offsetFor("a"))
        assertEquals(TrafficDisplayOffset.Zero, second.offsetFor("a"))
        assertEquals(first.offsetsByKey, second.offsetsByKey)
        assertNotEquals(first.offsetFor("b"), first.offsetFor("c"))
    }

    @Test
    fun layout_pinnedTarget_staysAtOrigin() {
        val engine = TrafficScreenDeclutterEngine()

        val result = engine.layout(
            targets = listOf(
                target(key = "selected", x = 100f, y = 100f, priorityRank = 9, pinAtOrigin = true),
                target(key = "a", x = 100f, y = 100f, priorityRank = 0),
                target(key = "b", x = 100f, y = 100f, priorityRank = 1)
            ),
            strengthMultiplier = 1f
        )

        assertEquals(TrafficDisplayOffset.Zero, result.offsetFor("selected"))
        assertTrue(result.offsetFor("a") != TrafficDisplayOffset.Zero)
        assertTrue(result.offsetFor("b") != TrafficDisplayOffset.Zero)
    }

    @Test
    fun layout_strengthMultiplier_reducesOffsetMagnitude() {
        val engine = TrafficScreenDeclutterEngine()
        val targets = listOf(
            target(key = "a", x = 100f, y = 100f),
            target(key = "b", x = 100f, y = 100f)
        )

        val full = engine.layout(targets = targets, strengthMultiplier = 1f)
        val reduced = engine.layout(targets = targets, strengthMultiplier = 0.4f)

        val fullMagnitude = magnitude(full.offsetFor("b"))
        val reducedMagnitude = magnitude(reduced.offsetFor("b"))
        assertTrue(reducedMagnitude < fullMagnitude)
    }

    @Test
    fun layout_sameMembership_reusesOffsets() {
        val engine = TrafficScreenDeclutterEngine()
        val targets = listOf(
            target(key = "a", x = 100f, y = 100f),
            target(key = "b", x = 100f, y = 100f),
            target(key = "c", x = 100f, y = 100f)
        )

        val first = engine.layout(targets = targets, strengthMultiplier = 1f)
        val second = engine.layout(targets = targets, strengthMultiplier = 1f)

        assertEquals(first.offsetsByKey, second.offsetsByKey)
    }

    @Test
    fun resolveTrafficDeclutterStrengthMultiplier_fadesBetweenZoomBands() {
        assertEquals(
            1f,
            resolveTrafficDeclutterStrengthMultiplier(
                zoomLevel = 8.0f,
                fullStrengthAtOrBelowZoom = 8.25f,
                zeroStrengthAtOrAboveZoom = 10.5f
            )
        )
        assertEquals(
            0f,
            resolveTrafficDeclutterStrengthMultiplier(
                zoomLevel = 10.6f,
                fullStrengthAtOrBelowZoom = 8.25f,
                zeroStrengthAtOrAboveZoom = 10.5f
            )
        )
        assertTrue(
            resolveTrafficDeclutterStrengthMultiplier(
                zoomLevel = 9.5f,
                fullStrengthAtOrBelowZoom = 8.25f,
                zeroStrengthAtOrAboveZoom = 10.5f
            ) in 0f..1f
        )
    }

    private fun target(
        key: String,
        x: Float,
        y: Float,
        priorityRank: Int = 0,
        pinAtOrigin: Boolean = false
    ) = ProjectedTrafficTarget(
        key = key,
        screenX = x,
        screenY = y,
        collisionWidthPx = 24f,
        collisionHeightPx = 24f,
        priorityRank = priorityRank,
        pinAtOrigin = pinAtOrigin
    )

    private fun magnitude(offset: TrafficDisplayOffset): Float = hypot(offset.dxPx, offset.dyPx)
}
