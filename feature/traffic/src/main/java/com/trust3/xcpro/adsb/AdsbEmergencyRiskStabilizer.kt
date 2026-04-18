package com.trust3.xcpro.adsb

internal class AdsbEmergencyRiskStabilizer(
    private val exitFreshSampleCount: Int = EMERGENCY_EXIT_FRESH_SAMPLE_COUNT
) {

    private val stateByTargetId = HashMap<Icao24, EmergencyState>()

    fun clear() {
        stateByTargetId.clear()
    }

    fun removeTarget(id: Icao24) {
        stateByTargetId.remove(id)
    }

    fun stabilize(
        id: Icao24,
        candidateEmergencyRisk: Boolean,
        hasFreshTrendSample: Boolean,
        forceClear: Boolean
    ): Boolean {
        if (forceClear) {
            stateByTargetId.remove(id)
            return false
        }

        val previous = stateByTargetId[id]
        val next = when {
            candidateEmergencyRisk -> EmergencyState(isEmergencyActive = true, safeFreshSampleCount = 0)
            previous == null || !previous.isEmergencyActive ->
                EmergencyState(isEmergencyActive = false, safeFreshSampleCount = 0)
            hasFreshTrendSample -> {
                val nextSafeCount = previous.safeFreshSampleCount + 1
                if (nextSafeCount >= exitFreshSampleCount) {
                    EmergencyState(isEmergencyActive = false, safeFreshSampleCount = 0)
                } else {
                    EmergencyState(isEmergencyActive = true, safeFreshSampleCount = nextSafeCount)
                }
            }
            else -> previous
        }

        if (next.isEmergencyActive) {
            stateByTargetId[id] = next
        } else {
            stateByTargetId.remove(id)
        }
        return next.isEmergencyActive
    }

    private data class EmergencyState(
        val isEmergencyActive: Boolean,
        val safeFreshSampleCount: Int
    )

    private companion object {
        const val EMERGENCY_EXIT_FRESH_SAMPLE_COUNT = 2
    }
}
