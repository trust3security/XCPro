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
     * @param airspeedMs indicated airspeed in meters per second.
     * @return still-air sink in m/s, or null if no glider/polar is configured.
     */
    fun sinkAtSpeed(airspeedMs: Double): Double?

    /**
     * @return IAS bounds derived from the active glider profile, or null if unavailable.
     */
    fun iasBoundsMs(): SpeedBoundsMs?

    /**
     * @return theoretical still-air L/D at the supplied indicated airspeed, or null if unavailable.
     */
    fun ldAtSpeed(airspeedMs: Double): Double? {
        val sinkMs = sinkAtSpeed(airspeedMs) ?: return null
        if (!airspeedMs.isFinite() || airspeedMs <= 0.0) return null
        if (!sinkMs.isFinite() || sinkMs <= 0.0) return null
        val ld = airspeedMs / sinkMs
        return ld.takeIf { it.isFinite() && it > 0.0 }
    }

    /**
     * @return best theoretical still-air L/D for the active polar, or null if unavailable.
     */
    fun bestLd(): Double? = null
}

/**
 * Uses the pilot's configured glider + polar to derive sink rates.
 */
@Singleton
class PolarStillAirSinkProvider @Inject constructor(
    private val gliderRepository: GliderRepository
) : StillAirSinkProvider {
    private data class CacheKey(
        val model: GliderModel,
        val config: GliderConfig
    )

    private val cacheLock = Any()
    private var bestLdCacheKey: CacheKey? = null
    private var bestLdCacheValue: Double? = null

    override fun sinkAtSpeed(airspeedMs: Double): Double? {
        val model: GliderModel = gliderRepository.effectiveModel.value
        val config: GliderConfig = gliderRepository.config.value
        if (!GliderSpeedBoundsResolver.hasPolar(model, config)) return null
        return runCatching { PolarCalculator.sinkMs(airspeedMs, model, config) }
            .getOrNull()
            ?.takeIf { it.isFinite() }
    }

    override fun iasBoundsMs(): SpeedBoundsMs? {
        val model: GliderModel = gliderRepository.effectiveModel.value
        val config: GliderConfig = gliderRepository.config.value
        return GliderSpeedBoundsResolver.resolveIasBoundsMs(model, config)
    }

    override fun ldAtSpeed(airspeedMs: Double): Double? {
        val model: GliderModel = gliderRepository.effectiveModel.value
        val config: GliderConfig = gliderRepository.config.value
        if (!GliderSpeedBoundsResolver.hasPolar(model, config)) return null
        return runCatching { GlidePolarMetricsResolver.ldAtSpeed(airspeedMs, model, config) }
            .getOrNull()
            ?.takeIf { it.isFinite() && it > 0.0 }
    }

    override fun bestLd(): Double? {
        val model: GliderModel = gliderRepository.effectiveModel.value
        val config: GliderConfig = gliderRepository.config.value
        if (!GliderSpeedBoundsResolver.hasPolar(model, config)) return null
        val key = CacheKey(model = model, config = config)
        synchronized(cacheLock) {
            if (bestLdCacheKey == key) {
                return bestLdCacheValue
            }
        }
        val computed = runCatching {
            GlidePolarMetricsResolver.deriveBestLd(model, config).bestLd
        }.getOrNull()?.takeIf { it.isFinite() && it > 0.0 }
        synchronized(cacheLock) {
            bestLdCacheKey = key
            bestLdCacheValue = computed
        }
        return computed
    }
}

