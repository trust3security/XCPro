package com.example.xcpro.glider

import com.example.xcpro.common.glider.GliderModel
import com.example.xcpro.common.glider.GliderConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides still-air sink (m/s) for the currently selected glider at a given true airspeed.
 *
 * Implementations must be thread-safe; callers reside on sensor/IO threads.
 */
interface StillAirSinkProvider {
    /**
     * @param airspeedMs true airspeed in meters per second.
     * @return still-air sink in m/s, or null if no glider/polar is configured.
     */
    fun sinkAtSpeed(airspeedMs: Double): Double?
}

/**
 * Uses the pilot's configured glider + polar to derive sink rates.
 */
@Singleton
class PolarStillAirSinkProvider @Inject constructor(
    private val gliderRepository: GliderRepository
) : StillAirSinkProvider {

    override fun sinkAtSpeed(airspeedMs: Double): Double? {
        val model: GliderModel = gliderRepository.selectedModel.value ?: return null
        val config: GliderConfig = gliderRepository.config.value
        return runCatching { PolarCalculator.sinkMs(airspeedMs, model, config) }
            .getOrNull()
    }
}

