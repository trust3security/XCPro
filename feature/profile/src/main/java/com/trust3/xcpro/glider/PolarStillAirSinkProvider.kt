package com.trust3.xcpro.glider

import com.trust3.xcpro.common.glider.GliderConfig
import com.trust3.xcpro.common.glider.GliderModel
import com.trust3.xcpro.external.ExternalFlightSettingsReadPort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PolarStillAirSinkProvider @Inject constructor(
    private val gliderRepository: GliderRepository,
    private val externalFlightSettingsReadPort: ExternalFlightSettingsReadPort
) : StillAirSinkProvider {
    private data class CacheKey(
        val model: GliderModel,
        val config: GliderConfig
    )

    private val cacheLock = Any()
    private var bestLdCacheKey: CacheKey? = null
    private var bestLdCacheValue: Double? = null

    override fun sinkAtSpeed(indicatedAirspeedMs: Double): Double? {
        val model: GliderModel = gliderRepository.effectiveModel.value
        val config: GliderConfig = effectiveConfig()
        if (!GliderSpeedBoundsResolver.hasPolar(model, config)) return null
        return runCatching { PolarCalculator.sinkMs(indicatedAirspeedMs, model, config) }
            .getOrNull()
            ?.takeIf { it.isFinite() }
    }

    override fun iasBoundsMs(): SpeedBoundsMs? {
        val model: GliderModel = gliderRepository.effectiveModel.value
        val config: GliderConfig = effectiveConfig()
        return GliderSpeedBoundsResolver.resolveIasBoundsMs(model, config)
    }

    override fun ldAtSpeed(indicatedAirspeedMs: Double): Double? {
        val model: GliderModel = gliderRepository.effectiveModel.value
        val config: GliderConfig = effectiveConfig()
        if (!GliderSpeedBoundsResolver.hasPolar(model, config)) return null
        return runCatching { GlidePolarMetricsResolver.ldAtSpeed(indicatedAirspeedMs, model, config) }
            .getOrNull()
            ?.takeIf { it.isFinite() && it > 0.0 }
    }

    override fun bestLd(): Double? {
        val model: GliderModel = gliderRepository.effectiveModel.value
        val config: GliderConfig = effectiveConfig()
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

    private fun effectiveConfig(): GliderConfig {
        val config = gliderRepository.config.value
        val externalBugs = externalFlightSettingsReadPort.externalFlightSettingsSnapshot.value.bugsPercent
            ?: return config
        return config.copy(bugsPercent = externalBugs.coerceIn(0, 50))
    }
}
