package com.example.xcpro.glider

import com.example.xcpro.common.glider.GliderConfig
import com.example.xcpro.common.glider.GliderModel
import javax.inject.Inject
import javax.inject.Singleton

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
