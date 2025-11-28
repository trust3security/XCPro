package com.example.xcpro.sensors

/**
 * Tracks thermal state (current/last climb), TC30 averages, and gain.
 * Pure domain helper: no Android deps, no side effects beyond its own state.
 */
internal class ThermalTracker {
    private val currentThermalInfo = ThermalClimbInfo()
    private val lastThermalInfo = ThermalClimbInfo()
    private var lastThermalLiftRate = 0.0
    private var lastThermalGain = 0.0
    private var lastThermalTimestamp = 0L
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
            currentThermalInfo.isDefined() -> currentThermalInfo.liftRate
            lastThermalInfo.isDefined() -> lastThermalInfo.liftRate
            else -> Double.NaN
        }
    val currentThermalValid: Boolean
        get() = currentThermalInfo.isDefined() || lastThermalInfo.isDefined()

    fun update(
        timestampMillis: Long,
        teAltitudeMeters: Double,
        verticalSpeedMs: Double,
        isCircling: Boolean
    ) {
        if (!teAltitudeMeters.isFinite()) {
            // leave last thermal intact; before first thermal it'll remain undefined
            return
        }

        if (timestampMillis < lastThermalTimestamp) {
            reset()
        }

        lastThermalTimestamp = timestampMillis

        if (isCircling) {
            if (!currentThermalInfo.isDefined()) {
                currentThermalInfo.startTime = timestampMillis
                currentThermalInfo.startTeAltitude = teAltitudeMeters
            }
            currentThermalInfo.endTime = timestampMillis
            currentThermalInfo.endTeAltitude = teAltitudeMeters
            currentThermalInfo.gain =
                currentThermalInfo.endTeAltitude - currentThermalInfo.startTeAltitude
            val durationSeconds = currentThermalInfo.durationSeconds
            val liftRate = when {
                durationSeconds > 0.5 -> currentThermalInfo.gain / durationSeconds
                verticalSpeedMs.isFinite() -> verticalSpeedMs
                else -> 0.0
            }
            currentThermalInfo.liftRate = liftRate
            thermalGainCurrent = currentThermalInfo.gain
            thermalAverageCurrent = liftRate.toFloat()
            lastThermalLiftRate = liftRate
            lastThermalGain = currentThermalInfo.gain
            thermalGainValid = true
        } else {
            if (currentThermalInfo.isDefined()) {
                totalCirclingSeconds += currentThermalInfo.durationSeconds
                totalHeightGain += currentThermalInfo.gain
                lastThermalLiftRate = currentThermalInfo.liftRate
                lastThermalGain = currentThermalInfo.gain
                lastThermalInfo.copyFrom(currentThermalInfo)
            }
            // Keep showing last thermal stats like XCSoar (current_thermal falls back to last_thermal)
            currentThermalInfo.copyFrom(lastThermalInfo)
            thermalGainCurrent = lastThermalGain
            thermalGainValid = lastThermalInfo.isDefined()
            thermalAverageCurrent = lastThermalLiftRate.toFloat()
        }

        val cumulativeSeconds = totalCirclingSeconds +
            if (currentThermalInfo.isDefined()) currentThermalInfo.durationSeconds else 0.0
        val cumulativeGain = totalHeightGain +
            if (currentThermalInfo.isDefined()) currentThermalInfo.gain else 0.0

        thermalAverageTotal = if (cumulativeSeconds > 0.5) {
            (cumulativeGain / cumulativeSeconds).toFloat()
        } else {
            0f
        }
    }

    fun reset() {
        currentThermalInfo.clear()
        lastThermalInfo.clear()
        lastThermalLiftRate = 0.0
        lastThermalGain = 0.0
        lastThermalTimestamp = 0L
        totalCirclingSeconds = 0.0
        totalHeightGain = 0.0
        thermalAverageTotal = 0f
        thermalAverageCurrent = 0f
        thermalGainCurrent = 0.0
        thermalGainValid = false
    }
}
