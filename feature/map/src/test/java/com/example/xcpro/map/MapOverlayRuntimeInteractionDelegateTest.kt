package com.example.xcpro.map

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapOverlayRuntimeInteractionDelegateTest {

    @Test
    fun setMapInteractionActive_reActivatesBeforeGrace_keepsInteractionActive() = runTest {
        val fixture = InteractionFixture(this)

        fixture.delegate.setMapInteractionActive(true)
        fixture.delegate.setMapInteractionActive(false)
        fixture.delegate.setMapInteractionActive(true)

        runCurrent()
        advanceTimeBy(600)
        runCurrent()

        assertEquals(1, fixture.probe.forecastCalls)
        assertEquals(1, fixture.probe.ognCalls)
        assertEquals(0, fixture.probe.flushCalls)
        assertTrue(fixture.probe.forecastActive)
        assertTrue(fixture.probe.ognActive)
        assertTrue(fixture.delegate.isMapInteractionActive)
    }

    @Test
    fun setMapInteractionActive_falseAppliesAfterGracePeriod() = runTest {
        val fixture = InteractionFixture(this)

        fixture.delegate.setMapInteractionActive(true)
        fixture.delegate.setMapInteractionActive(false)
        runCurrent()

        assertEquals(1, fixture.probe.forecastCalls)
        assertEquals(1, fixture.probe.ognCalls)
        assertTrue(fixture.probe.forecastActive)
        assertTrue(fixture.probe.ognActive)

        advanceTimeBy(500)
        runCurrent()

        assertEquals(2, fixture.probe.forecastCalls)
        assertEquals(2, fixture.probe.ognCalls)
        assertEquals(1, fixture.probe.flushCalls)
        assertFalse(fixture.probe.forecastActive)
        assertFalse(fixture.probe.ognActive)

        fixture.delegate.setMapInteractionActive(true)
        runCurrent()
        assertEquals(3, fixture.probe.forecastCalls)
        assertEquals(3, fixture.probe.ognCalls)
        assertTrue(fixture.probe.forecastActive)
        assertTrue(fixture.probe.ognActive)
        assertEquals(1, fixture.probe.flushCalls)
    }

    @Test
    fun onMapDetachedForcesInteractionInactive() = runTest {
        val fixture = InteractionFixture(this)

        fixture.delegate.setMapInteractionActive(true)
        fixture.delegate.setMapInteractionActive(false)
        fixture.delegate.onMapDetached()
        runCurrent()

        assertEquals(2, fixture.probe.forecastCalls)
        assertEquals(2, fixture.probe.ognCalls)
        assertEquals(1, fixture.probe.flushCalls)
        assertTrue(!fixture.probe.forecastActive)
        assertTrue(!fixture.probe.ognActive)
        assertTrue(!fixture.delegate.isMapInteractionActive)
    }

    @Test
    fun setMapInteractionActive_falseWhileInactive_noRedundantCallbacks() = runTest {
        val fixture = InteractionFixture(this)

        fixture.delegate.setMapInteractionActive(false)
        runCurrent()

        assertEquals(0, fixture.probe.forecastCalls)
        assertEquals(0, fixture.probe.ognCalls)
        assertFalse(fixture.probe.forecastActive)
        assertFalse(fixture.probe.ognActive)
    }

    private class InteractionFixture(scope: kotlinx.coroutines.test.TestScope) {
        val probe = InteractionProbe()
        val delegate = MapOverlayRuntimeInteractionDelegate(
            coroutineScope = scope,
            applyMapInteractionState = { active ->
                probe.forecastActive = active
                probe.ognActive = active
                probe.forecastCalls += 1
                probe.ognCalls += 1
                if (!active) {
                    probe.flushCalls += 1
                }
            }
        )
    }

    private class InteractionProbe {
        var forecastActive = false
        var ognActive = false
        var forecastCalls = 0
        var ognCalls = 0
        var flushCalls = 0
    }
}
