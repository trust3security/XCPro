package com.example.xcpro.sensors

/**
 * Tracks thermal state (current/last climb), TC30 averages, and gain.
 * Pure domain helper: no Android deps, no side effects beyond its own state.
 */
internal class ThermalTracker {
    private val currentThermalInfo = ThermalClimbInfo()
    private val lastThermalInfo = ThermalClimbInfo()
    private var lastThermalTimestamp = 0L
    private var lastCircling = false
    private var lastTurning = false
    private var turningEndTimeMs = 0L
    private var turningEndTeAltitude = Double.NaN
    private var turningEndCaptured = false
    private var circlingSegmentStartTimeMs = 0L
    private var circlingSegmentStartTeAltitude = Double.NaN
    private var totalCirclingSeconds = 0.0
    private var totalHeightGain = 0.0

    var thermalAverageTotal: Float = 0f
        private set
    var thermalAverageCurrent: Float = 0f
        private set
    var thermalGainCurrent: Double = 0.0
        private set
    var thermalGainValid: Boolean = false
        private set

    val currentThermalLiftRate: Double
        get() = when {
            lastCircling && currentThermalInfo.isDefined() -> currentThermalInfo.liftRate
            lastThermalInfo.isDefined() -> lastThermalInfo.liftRate
            else -> Double.NaN
        }

    val currentThermalValid: Boolean
        get() = (lastCircling && currentThermalInfo.isDefined()) || lastThermalInfo.isDefined()

    fun update(
        timestampMillis: Long,
        teAltitudeMeters: Double,
        verticalSpeedMs: Double,
        isCircling: Boolean,
        isTurning: Boolean
    ) {
        if (!teAltitudeMeters.isFinite()) return
        if (timestampMillis < lastThermalTimestamp) reset()

        if (isTurning && !lastTurning) {
            if (!lastCircling) {
                currentThermalInfo.clear()
                currentThermalInfo.startTime = timestampMillis
                currentThermalInfo.startTeAltitude = teAltitudeMeters
                currentThermalInfo.endTime = timestampMillis
                currentThermalInfo.endTeAltitude = teAltitudeMeters
            }
            turningEndCaptured = false
        } else if (!isTurning && lastTurning) {
            turningEndTimeMs = timestampMillis
            turningEndTeAltitude = teAltitudeMeters
            turningEndCaptured = true
            if (!isCircling) {
                currentThermalInfo.clear()
            }
        }

        if (isCircling && !lastCircling) {
            circlingSegmentStartTimeMs = timestampMillis
            circlingSegmentStartTeAltitude = teAltitudeMeters
            if (currentThermalInfo.startTime <= 0L || !currentThermalInfo.startTeAltitude.isFinite()) {
                currentThermalInfo.startTime = timestampMillis
                currentThermalInfo.startTeAltitude = teAltitudeMeters
            }
            currentThermalInfo.endTime = timestampMillis
            currentThermalInfo.endTeAltitude = teAltitudeMeters
            turningEndCaptured = false
        } else if (!isCircling && lastCircling) {
            val segment = buildSegment(
                startTimeMs = circlingSegmentStartTimeMs,
                startTeAltitude = circlingSegmentStartTeAltitude,
                endTimeMs = timestampMillis,
                endTeAltitude = teAltitudeMeters
            )
            if (segment != null) {
                totalCirclingSeconds += segment.durationSeconds
                totalHeightGain += segment.gain
            }
            circlingSegmentStartTimeMs = 0L
            circlingSegmentStartTeAltitude = Double.NaN

            val endTimeMs = if (turningEndCaptured) turningEndTimeMs else currentThermalInfo.endTime
            val endTeAltitude = if (turningEndCaptured) turningEndTeAltitude else currentThermalInfo.endTeAltitude
            finalizeLastThermal(endTimeMs, endTeAltitude)
            currentThermalInfo.clear()
            turningEndCaptured = false
        }

        lastCircling = isCircling
        lastTurning = isTurning
        lastThermalTimestamp = timestampMillis

        if (isCircling) {
            updateCurrentThermal(timestampMillis, teAltitudeMeters)
            thermalGainValid = true
        } else {
            if (lastThermalInfo.isDefined()) {
                thermalGainCurrent = lastThermalInfo.gain
                thermalAverageCurrent = lastThermalInfo.liftRate.toFloat()
            } else {
                thermalGainCurrent = 0.0
                thermalAverageCurrent = 0f
            }
            thermalGainValid = lastThermalInfo.isDefined()
        }

        val currentSegment = if (isCircling) {
            buildSegment(
                startTimeMs = circlingSegmentStartTimeMs,
                startTeAltitude = circlingSegmentStartTeAltitude,
                endTimeMs = timestampMillis,
                endTeAltitude = teAltitudeMeters
            )
        } else {
            null
        }
        val cumulativeSeconds = totalCirclingSeconds + (currentSegment?.durationSeconds ?: 0.0)
        val cumulativeGain = totalHeightGain + (currentSegment?.gain ?: 0.0)

        thermalAverageTotal = if (cumulativeSeconds > 0.5) {
            (cumulativeGain / cumulativeSeconds).toFloat()
        } else {
            0f
        }
    }

    fun reset() {
        currentThermalInfo.clear()
        lastThermalInfo.clear()
        lastThermalTimestamp = 0L
        lastCircling = false
        lastTurning = false
        turningEndTimeMs = 0L
        turningEndTeAltitude = Double.NaN
        turningEndCaptured = false
        circlingSegmentStartTimeMs = 0L
        circlingSegmentStartTeAltitude = Double.NaN
        totalCirclingSeconds = 0.0
        totalHeightGain = 0.0
        thermalAverageTotal = 0f
        thermalAverageCurrent = 0f
        thermalGainCurrent = 0.0
        thermalGainValid = false
    }

    private data class ThermalSegment(
        val durationSeconds: Double,
        val gain: Double
    )

    private fun buildSegment(
        startTimeMs: Long,
        startTeAltitude: Double,
        endTimeMs: Long,
        endTeAltitude: Double
    ): ThermalSegment? {
        if (startTimeMs <= 0L || endTimeMs < startTimeMs) return null
        if (!startTeAltitude.isFinite() || !endTeAltitude.isFinite()) return null
        val durationSeconds = (endTimeMs - startTimeMs).coerceAtLeast(0L) / 1000.0
        if (durationSeconds <= 0.0) return null
        val gain = endTeAltitude - startTeAltitude
        return ThermalSegment(durationSeconds = durationSeconds, gain = gain)
    }

    private fun updateCurrentThermal(timestampMillis: Long, teAltitudeMeters: Double) {
        if (currentThermalInfo.startTime <= 0L || !currentThermalInfo.startTeAltitude.isFinite()) {
            currentThermalInfo.startTime = timestampMillis
            currentThermalInfo.startTeAltitude = teAltitudeMeters
        }
        currentThermalInfo.endTime = timestampMillis
        currentThermalInfo.endTeAltitude = teAltitudeMeters
        currentThermalInfo.gain = currentThermalInfo.endTeAltitude - currentThermalInfo.startTeAltitude
        val durationSeconds = currentThermalInfo.durationSeconds
        val liftRate = if (durationSeconds > 0.0) currentThermalInfo.gain / durationSeconds else 0.0
        currentThermalInfo.liftRate = liftRate
        thermalGainCurrent = currentThermalInfo.gain
        thermalAverageCurrent = liftRate.toFloat()
    }

    private fun finalizeLastThermal(endTimeMs: Long, endTeAltitude: Double) {
        val segment = buildSegment(
            startTimeMs = currentThermalInfo.startTime,
            startTeAltitude = currentThermalInfo.startTeAltitude,
            endTimeMs = endTimeMs,
            endTeAltitude = endTeAltitude
        ) ?: return

        val qualifies = segment.durationSeconds >= THERMAL_MIN_DURATION_SECONDS &&
            segment.gain > THERMAL_MIN_GAIN_METERS
        if (!qualifies) return

        val liftRate = segment.gain / segment.durationSeconds
        currentThermalInfo.endTime = endTimeMs
        currentThermalInfo.endTeAltitude = endTeAltitude
        currentThermalInfo.gain = segment.gain
        currentThermalInfo.liftRate = liftRate
        lastThermalInfo.copyFrom(currentThermalInfo)
    }

    private companion object {
        private const val THERMAL_MIN_DURATION_SECONDS = 45.0
        private const val THERMAL_MIN_GAIN_METERS = 0.0
    }
}
