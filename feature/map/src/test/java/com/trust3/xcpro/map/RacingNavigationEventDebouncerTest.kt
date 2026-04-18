package com.trust3.xcpro.map

import com.trust3.xcpro.tasks.racing.models.RacingWaypointRole
import com.trust3.xcpro.tasks.racing.navigation.RacingNavigationEvent
import com.trust3.xcpro.tasks.racing.navigation.RacingNavigationEventType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RacingNavigationEventDebouncerTest {

    @Test
    fun emitsFirstEvent() {
        val debouncer = RacingNavigationEventDebouncer(debounceMillis = 2000L)
        val event = eventAt(1000L, RacingNavigationEventType.START, 0, 1)

        assertTrue(debouncer.shouldEmit(event))
    }

    @Test
    fun suppressesDuplicateWithinWindow() {
        val debouncer = RacingNavigationEventDebouncer(debounceMillis = 2000L)
        val first = eventAt(1000L, RacingNavigationEventType.TURNPOINT, 1, 2)
        val second = eventAt(2500L, RacingNavigationEventType.TURNPOINT, 1, 2)

        assertTrue(debouncer.shouldEmit(first))
        assertFalse(debouncer.shouldEmit(second))
    }

    @Test
    fun allowsDuplicateAfterWindow() {
        val debouncer = RacingNavigationEventDebouncer(debounceMillis = 2000L)
        val first = eventAt(1000L, RacingNavigationEventType.TURNPOINT, 1, 2)
        val second = eventAt(3500L, RacingNavigationEventType.TURNPOINT, 1, 2)

        assertTrue(debouncer.shouldEmit(first))
        assertTrue(debouncer.shouldEmit(second))
    }

    @Test
    fun allowsDifferentKeyWithinWindow() {
        val debouncer = RacingNavigationEventDebouncer(debounceMillis = 2000L)
        val first = eventAt(1000L, RacingNavigationEventType.TURNPOINT, 1, 2)
        val second = eventAt(1500L, RacingNavigationEventType.TURNPOINT, 2, 3)

        assertTrue(debouncer.shouldEmit(first))
        assertTrue(debouncer.shouldEmit(second))
    }

    private fun eventAt(
        timestampMillis: Long,
        type: RacingNavigationEventType,
        fromIndex: Int,
        toIndex: Int
    ): RacingNavigationEvent = RacingNavigationEvent(
        type = type,
        fromLegIndex = fromIndex,
        toLegIndex = toIndex,
        waypointRole = RacingWaypointRole.TURNPOINT,
        timestampMillis = timestampMillis
    )
}
