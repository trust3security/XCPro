package com.example.xcpro.map

import com.example.xcpro.tasks.racing.navigation.RacingNavigationEvent
import com.example.xcpro.tasks.racing.navigation.RacingNavigationEventType

/**
 * Debounces racing navigation events for UI consumption only.
 * Uses navigation timestamps (GPS/replay time), never wall time.
 */
class RacingNavigationEventDebouncer(
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS
) {

    private var lastKey: EventKey? = null
    private var lastTimestampMillis: Long = Long.MIN_VALUE

    fun shouldEmit(event: RacingNavigationEvent): Boolean {
        val key = EventKey(event.type, event.fromLegIndex, event.toLegIndex)
        val delta = event.timestampMillis - lastTimestampMillis
        if (key == lastKey && delta in 0 until debounceMillis) {
            return false
        }
        lastKey = key
        lastTimestampMillis = event.timestampMillis
        return true
    }

    private data class EventKey(
        val type: RacingNavigationEventType,
        val fromIndex: Int,
        val toIndex: Int
    )

    companion object {
        private const val DEFAULT_DEBOUNCE_MILLIS = 2000L
    }
}
