package com.trust3.xcpro.map

import com.trust3.xcpro.core.common.logging.AppLogger
import com.trust3.xcpro.replay.SessionState
import com.trust3.xcpro.tasks.racing.navigation.RacingNavigationEvent

/**
 * Debug-only logger for racing replay validation.
 * Collects navigation events during replay and logs a summary on completion.
 */
class RacingReplayEventLogger(
    private val tag: String = DEFAULT_TAG
) {
    private val events = mutableListOf<RacingNavigationEvent>()

    fun record(event: RacingNavigationEvent) {
        events += event
    }

    fun flush(session: SessionState) {
        if (events.isEmpty()) {
            AppLogger.i(tag, "Racing replay: no navigation events captured")
            return
        }
        val start = session.startTimestampMillis
        events.forEachIndexed { index, event ->
            val offset = event.timestampMillis - start
            AppLogger.i(
                tag,
                "Event ${index + 1}: ${event.type} " +
                    "from=${event.fromLegIndex} to=${event.toLegIndex} " +
                    "t=${offset}ms"
            )
        }
    }

    fun reset() {
        events.clear()
    }

    companion object {
        private const val DEFAULT_TAG = "RACING_REPLAY"
    }
}
