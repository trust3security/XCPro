package com.example.xcpro.map.trail.domain

import com.example.xcpro.sensors.CompleteFlightData

/**
 * Selects the best vario value for trail rendering.
 */
internal class ResolveTrailVarioUseCase {

    fun resolve(data: CompleteFlightData, isReplay: Boolean): Double {
        if (isReplay) {
            val igc = data.realIgcVario?.value
            if (igc != null && igc.isFinite()) return igc
            val baseline = data.baselineDisplayVario.value
            if (data.baselineVarioValid && baseline.isFinite()) return baseline
            val displayNetto = data.displayNetto.value
            if (data.nettoValid && displayNetto.isFinite()) return displayNetto
        }

        if (data.nettoValid && data.netto.value.isFinite()) {
            return data.netto.value
        }

        return data.displayVario.value.takeIf { it.isFinite() } ?: data.verticalSpeed.value
    }
}
