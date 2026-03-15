package com.example.xcpro.audio

 

/**
 * Maps vertical speed to audio parameters (frequency, cycle time, duty cycle, mode)
 *
 * Based on professional variometer standards:
 * - XCTracer: 1.16 m/s = 579Hz, 527ms cycle
 * - Higher pitch = stronger lift
 * - Faster beeping = stronger lift
 * - Silence for weak sink, warning tone for strong sink
 */
class VarioFrequencyMapper(
    private val settings: VarioAudioSettings = VarioAudioSettings()
) {

    companion object {
        private const val MIN_VARIO = -5.0  // m/s
        private const val MAX_VARIO = 5.0   // m/s
        private const val MIN_FREQ = 200.0  // Hz
        private const val ZERO_FREQ = 500.0 // Hz
        private const val MAX_FREQ = 1500.0 // Hz
        private const val MIN_PERIOD_MS = 150.0
        private const val MAX_PERIOD_MS = 600.0
        private const val SINK_HYSTERESIS_MS = 0.1
    }

    private var sinkActive = false

    fun mapVerticalSpeed(verticalSpeedMs: Double): AudioParams {
        val clipped = verticalSpeedMs.coerceIn(MIN_VARIO, MAX_VARIO)
        return when {
            clipped > 0 -> {
                sinkActive = false
                mapLift(clipped)
            }
            clipped < 0 -> mapSink(clipped)
            else -> {
                sinkActive = false
                AudioParams(0.0, 0.0, 0.0, AudioMode.SILENCE)
            }
        }
    }

    private fun mapLift(vs: Double): AudioParams {
        if (vs < maxOf(settings.liftThreshold, effectiveDeadbandMax())) {
            return AudioParams(0.0, 0.0, 0.0, AudioMode.SILENCE)
        }
        val frequency = varioToFrequency(vs)
        val period = liftPeriodFor(vs)
        return AudioParams(frequency, period, settings.dutyCycle, AudioMode.BEEPING)
    }

    private fun mapSink(vs: Double): AudioParams {
        val effectiveThreshold = minOf(settings.sinkSilenceThreshold, settings.deadbandMin)
        val startThreshold = effectiveThreshold - SINK_HYSTERESIS_MS
        val stopThreshold = effectiveThreshold + SINK_HYSTERESIS_MS
        sinkActive = when {
            sinkActive && vs <= stopThreshold -> true
            sinkActive && vs > stopThreshold -> false
            !sinkActive && vs <= startThreshold -> true
            else -> false
        }
        if (!sinkActive) {
            return AudioParams(0.0, 0.0, 0.0, AudioMode.SILENCE)
        }
        val frequency = varioToFrequency(vs)
        return AudioParams(frequency, 0.0, 1.0, AudioMode.CONTINUOUS)
    }

    private fun inDeadband(vs: Double): Boolean {
        val min = minOf(settings.deadbandMin, settings.deadbandMax - 0.01)
        val max = maxOf(settings.deadbandMin + 0.01, settings.deadbandMax)
        return vs in min..max
    }

    private fun effectiveDeadbandMax(): Double =
        maxOf(settings.deadbandMax, settings.deadbandMin)

    private fun varioToFrequency(vario: Double): Double {
        val clamped = vario.coerceIn(MIN_VARIO, MAX_VARIO)
        return if (clamped >= 0) {
            ZERO_FREQ + (clamped / MAX_VARIO) * (MAX_FREQ - ZERO_FREQ)
        } else {
            ZERO_FREQ - (clamped / MIN_VARIO) * (ZERO_FREQ - MIN_FREQ)
        }
    }

    private fun liftPeriodFor(vs: Double): Double {
        val ratio = (MAX_VARIO - vs.coerceIn(0.0, MAX_VARIO)) / MAX_VARIO
        return MIN_PERIOD_MS + ratio * (MAX_PERIOD_MS - MIN_PERIOD_MS)
    }
}

/**
 * Audio parameters for a given vertical speed
 */
data class AudioParams(
    val frequencyHz: Double,      // Tone frequency in Hz
    val cycleTimeMs: Double,      // Total time for one beep cycle (tone + silence)
    val dutyCycle: Double,        // Ratio of tone-on time (0.0-1.0, typically 0.5)
    val mode: AudioMode           // Beeping, continuous, or silence
) {
    /**
     * Calculate tone duration (on-time)
     */
    fun getToneDurationMs(): Long {
        return if (mode == AudioMode.CONTINUOUS) {
            1000L  // 1 second chunks for continuous tone
        } else {
            (cycleTimeMs * dutyCycle).toLong()
        }
    }

    /**
     * Calculate silence duration (off-time)
     */
    fun getSilenceDurationMs(): Long {
        return if (mode == AudioMode.CONTINUOUS) {
            0L  // No silence in continuous mode
        } else {
            (cycleTimeMs * (1.0 - dutyCycle)).toLong()
        }
    }

    /**
     * Is audio active (not silence)?
     */
    fun isActive(): Boolean {
        return mode != AudioMode.SILENCE && frequencyHz > 0
    }
}

/**
 * Audio mode
 */
enum class AudioMode {
    BEEPING,      // Beep pattern (lift)
    CONTINUOUS,   // Continuous tone (strong sink)
    SILENCE       // No audio
}

