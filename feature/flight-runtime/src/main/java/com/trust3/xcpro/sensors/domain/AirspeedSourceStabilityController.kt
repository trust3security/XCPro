package com.trust3.xcpro.sensors.domain

internal enum class AirspeedSourceTransitionEvent {
    GPS_TO_WIND,
    WIND_TO_GPS,
    WIND_GRACE_HOLD,
    WIND_DWELL_BLOCK
}

internal class AirspeedSourceStabilityController(
    private val minDwellMs: Long = FlightMetricsConstants.WIND_SOURCE_MIN_DWELL_MS,
    private val transientGraceMs: Long = FlightMetricsConstants.WIND_TRANSIENT_GRACE_MS
) {
    private var activeSource: AirspeedSource = AirspeedSource.GPS_GROUND
    private var lastSourceSwitchMs: Long = 0L
    private var windDropoutStartMs: Long? = null
    private var lastDecisionTimeMs: Long = 0L
    private var lastWindEstimate: AirspeedEstimate? = null
    private var graceHoldEventRecordedForDropout: Boolean = false
    private var dwellBlockEventRecordedForEpisode: Boolean = false
    private val transitionEvents = ArrayDeque<AirspeedSourceTransitionEvent>()

    fun isWindSelected(): Boolean = activeSource == AirspeedSource.WIND_VECTOR

    fun drainTransitionEvents(): List<AirspeedSourceTransitionEvent> {
        if (transitionEvents.isEmpty()) return emptyList()
        val drained = transitionEvents.toList()
        transitionEvents.clear()
        return drained
    }

    fun select(
        currentTimeMillis: Long,
        windDecision: WindAirspeedDecision,
        windCandidate: AirspeedEstimate?,
        gpsCandidate: AirspeedEstimate?
    ): AirspeedEstimate? {
        val now = normalizedNow(currentTimeMillis)
        if (windCandidate?.source == AirspeedSource.WIND_VECTOR) {
            lastWindEstimate = windCandidate
        }

        val windUsableNow = windDecision.eligible &&
            windCandidate != null &&
            windCandidate.source == AirspeedSource.WIND_VECTOR

        return when (activeSource) {
            AirspeedSource.WIND_VECTOR -> selectWhenWindActive(
                now = now,
                windUsableNow = windUsableNow,
                windCandidate = windCandidate,
                gpsCandidate = gpsCandidate
            )
            AirspeedSource.GPS_GROUND -> selectWhenGpsActive(
                now = now,
                windUsableNow = windUsableNow,
                windCandidate = windCandidate,
                gpsCandidate = gpsCandidate
            )
            AirspeedSource.EXTERNAL -> gpsCandidate
        }
    }

    fun reset() {
        activeSource = AirspeedSource.GPS_GROUND
        lastSourceSwitchMs = 0L
        windDropoutStartMs = null
        lastDecisionTimeMs = 0L
        lastWindEstimate = null
        graceHoldEventRecordedForDropout = false
        dwellBlockEventRecordedForEpisode = false
        transitionEvents.clear()
    }

    private fun selectWhenWindActive(
        now: Long,
        windUsableNow: Boolean,
        windCandidate: AirspeedEstimate?,
        gpsCandidate: AirspeedEstimate?
    ): AirspeedEstimate? {
        if (windUsableNow) {
            windDropoutStartMs = null
            graceHoldEventRecordedForDropout = false
            return windCandidate ?: lastWindEstimate
        }

        val dropoutStart = windDropoutStartMs ?: now.also { windDropoutStartMs = it }
        val dropoutAge = (now - dropoutStart).coerceAtLeast(0L)
        val hasCurrentWindVector = windCandidate != null
        if (hasCurrentWindVector && dropoutAge < transientGraceMs) {
            if (!graceHoldEventRecordedForDropout) {
                recordTransitionEvent(AirspeedSourceTransitionEvent.WIND_GRACE_HOLD)
                graceHoldEventRecordedForDropout = true
            }
            return lastWindEstimate ?: windCandidate ?: gpsCandidate
        }

        graceHoldEventRecordedForDropout = false
        windDropoutStartMs = null
        activeSource = AirspeedSource.GPS_GROUND
        lastSourceSwitchMs = now
        dwellBlockEventRecordedForEpisode = false
        recordTransitionEvent(AirspeedSourceTransitionEvent.WIND_TO_GPS)
        return gpsCandidate
    }

    private fun selectWhenGpsActive(
        now: Long,
        windUsableNow: Boolean,
        windCandidate: AirspeedEstimate?,
        gpsCandidate: AirspeedEstimate?
    ): AirspeedEstimate? {
        if (!windUsableNow) {
            windDropoutStartMs = null
            dwellBlockEventRecordedForEpisode = false
            return gpsCandidate
        }

        val dwellElapsed = (now - lastSourceSwitchMs).coerceAtLeast(0L)
        val dwellSatisfied = lastSourceSwitchMs <= 0L || dwellElapsed >= minDwellMs
        if (!dwellSatisfied) {
            if (!dwellBlockEventRecordedForEpisode) {
                recordTransitionEvent(AirspeedSourceTransitionEvent.WIND_DWELL_BLOCK)
                dwellBlockEventRecordedForEpisode = true
            }
            return gpsCandidate
        }

        activeSource = AirspeedSource.WIND_VECTOR
        lastSourceSwitchMs = now
        windDropoutStartMs = null
        graceHoldEventRecordedForDropout = false
        dwellBlockEventRecordedForEpisode = false
        recordTransitionEvent(AirspeedSourceTransitionEvent.GPS_TO_WIND)
        return windCandidate ?: lastWindEstimate ?: gpsCandidate
    }

    private fun normalizedNow(currentTimeMillis: Long): Long {
        val now = if (lastDecisionTimeMs > 0L && currentTimeMillis < lastDecisionTimeMs) {
            lastDecisionTimeMs
        } else {
            currentTimeMillis
        }
        lastDecisionTimeMs = now
        return now
    }

    private fun recordTransitionEvent(event: AirspeedSourceTransitionEvent) {
        transitionEvents.addLast(event)
    }
}
