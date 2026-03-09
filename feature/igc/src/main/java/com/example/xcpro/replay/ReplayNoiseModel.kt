package com.example.xcpro.replay

import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Applies deterministic replay noise for baro/GPS samples to mimic live sensor variance.
 *
 * AI-NOTE: Noise is seeded and warmup-scaled so replay remains repeatable while avoiding
 * unrealistic early spikes before filters settle.
 */
class ReplayNoiseModel(
    private val config: ReplaySimConfig
) {
    private var seededRandom = Random(config.seed)

    val random: Random
        get() = seededRandom

    fun reset() {
        seededRandom = Random(config.seed)
    }

    fun baroNoise(timestampMillis: Long, startTimestampMillis: Long): Double {
        if (config.mode != ReplayMode.REALTIME_SIM || config.pressureNoiseSigmaHpa <= 0.0) return 0.0
        return nextGaussian() * config.pressureNoiseSigmaHpa * warmupNoiseScale(timestampMillis, startTimestampMillis)
    }

    fun gpsAltitudeNoise(timestampMillis: Long, startTimestampMillis: Long): Double {
        if (config.mode != ReplayMode.REALTIME_SIM || config.gpsAltitudeNoiseSigmaM <= 0.0) return 0.0
        return nextGaussian() * config.gpsAltitudeNoiseSigmaM * warmupNoiseScale(timestampMillis, startTimestampMillis)
    }

    private fun warmupNoiseScale(timestampMillis: Long, startTimestampMillis: Long): Double {
        if (config.mode != ReplayMode.REALTIME_SIM) return 1.0
        val elapsed = max(0L, timestampMillis - startTimestampMillis).toDouble()
        if (config.warmupMillis <= 0L) return 1.0
        val progress = min(1.0, elapsed / config.warmupMillis.toDouble())
        // AI-NOTE: Start slightly noisier, then settle to nominal by the end of warmup.
        return 1.5 - (0.5 * progress)
    }

    private fun nextGaussian(): Double {
        val u1 = max(1e-12, seededRandom.nextDouble())
        val u2 = seededRandom.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * Math.PI * u2)
    }
}
