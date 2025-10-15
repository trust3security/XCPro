package com.example.xcpro.audio

import kotlin.math.abs

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
        // Reference point from XCTracer
        private const val XCTRACER_VS = 1.16  // m/s
        private const val XCTRACER_FREQ = 579.0  // Hz
        private const val XCTRACER_CYCLE = 527.0  // ms
    }

    /**
     * Map vertical speed to audio parameters
     *
     * @param verticalSpeedMs Vertical speed in m/s (TE-compensated)
     * @return Audio parameters for this vertical speed
     */
    fun mapVerticalSpeed(verticalSpeedMs: Double): AudioParams {
        return when (settings.profile) {
            VarioAudioProfile.COMPETITION -> mapCompetition(verticalSpeedMs)
            VarioAudioProfile.PARAGLIDING -> mapParagliding(verticalSpeedMs)
            VarioAudioProfile.SILENT_SINK -> mapSilentSink(verticalSpeedMs)
            VarioAudioProfile.FULL_AUDIO -> mapFullAudio(verticalSpeedMs)
        }
    }

    /**
     * Competition profile (XCTracer-style)
     * - Silence for all sink
     * - Beeping for lift (>0.2 m/s)
     * - Fast response, precise frequency mapping
     */
    private fun mapCompetition(vs: Double): AudioParams {
        return when {
            // LIFT ZONE (beeping)
            vs >= 5.0 -> AudioParams(1000.0, 200.0, settings.dutyCycle, AudioMode.BEEPING)
            vs >= 3.0 -> interpolate(vs, 3.0, 5.0, 800.0, 1000.0, 300.0, 200.0)
            vs >= 2.0 -> interpolate(vs, 2.0, 3.0, 700.0, 800.0, 400.0, 300.0)
            vs >= XCTRACER_VS -> interpolate(vs, XCTRACER_VS, 2.0, XCTRACER_FREQ, 700.0, XCTRACER_CYCLE, 400.0)
            vs >= 1.0 -> interpolate(vs, 1.0, XCTRACER_VS, 550.0, XCTRACER_FREQ, 600.0, XCTRACER_CYCLE)
            vs >= 0.5 -> interpolate(vs, 0.5, 1.0, 500.0, 550.0, 800.0, 600.0)
            vs >= settings.liftThreshold -> interpolate(vs, settings.liftThreshold, 0.5, 450.0, 500.0, 1000.0, 800.0)

            // DEADBAND (silence)
            vs > -settings.deadbandRange -> AudioParams(0.0, 0.0, 0.0, AudioMode.SILENCE)

            // SINK ZONE (silence for competition)
            else -> AudioParams(0.0, 0.0, 0.0, AudioMode.SILENCE)
        }
    }

    /**
     * Paragliding profile (gentler, slower beeps)
     * - Silence for sink
     * - Slower beeping for lift
     * - Less aggressive audio
     */
    private fun mapParagliding(vs: Double): AudioParams {
        return when {
            // LIFT ZONE (slower beeping)
            vs >= 5.0 -> AudioParams(900.0, 300.0, settings.dutyCycle, AudioMode.BEEPING)
            vs >= 3.0 -> interpolate(vs, 3.0, 5.0, 750.0, 900.0, 400.0, 300.0)
            vs >= 2.0 -> interpolate(vs, 2.0, 3.0, 650.0, 750.0, 500.0, 400.0)
            vs >= 1.0 -> interpolate(vs, 1.0, 2.0, 550.0, 650.0, 700.0, 500.0)
            vs >= 0.5 -> interpolate(vs, 0.5, 1.0, 480.0, 550.0, 900.0, 700.0)
            vs >= settings.liftThreshold -> interpolate(vs, settings.liftThreshold, 0.5, 420.0, 480.0, 1200.0, 900.0)

            // DEADBAND
            vs > -settings.deadbandRange -> AudioParams(0.0, 0.0, 0.0, AudioMode.SILENCE)

            // SINK (silence)
            else -> AudioParams(0.0, 0.0, 0.0, AudioMode.SILENCE)
        }
    }

    /**
     * Silent sink profile (most common)
     * - Silence for all sink
     * - Beeping for lift
     * - Warning tone for strong sink (< -2.0 m/s)
     */
    private fun mapSilentSink(vs: Double): AudioParams {
        return when {
            // LIFT (same as competition)
            vs >= settings.liftThreshold -> mapCompetition(vs)

            // DEADBAND
            vs > -settings.deadbandRange -> AudioParams(0.0, 0.0, 0.0, AudioMode.SILENCE)

            // WEAK SINK (silence)
            vs > settings.sinkSilenceThreshold -> AudioParams(0.0, 0.0, 0.0, AudioMode.SILENCE)

            // STRONG SINK WARNING (continuous tone)
            vs > settings.strongSinkThreshold -> AudioParams(250.0, 0.0, 1.0, AudioMode.CONTINUOUS)

            // EXTREME SINK WARNING
            else -> AudioParams(200.0, 0.0, 1.0, AudioMode.CONTINUOUS)
        }
    }

    /**
     * Full audio profile (both lift and sink tones)
     * - Beeping for lift
     * - Continuous low tone for sink
     */
    private fun mapFullAudio(vs: Double): AudioParams {
        return when {
            // LIFT
            vs >= settings.liftThreshold -> mapCompetition(vs)

            // DEADBAND
            abs(vs) <= settings.deadbandRange -> AudioParams(0.0, 0.0, 0.0, AudioMode.SILENCE)

            // WEAK SINK (low continuous tone)
            vs > -1.0 -> AudioParams(300.0, 0.0, 1.0, AudioMode.CONTINUOUS)

            // MODERATE SINK
            vs > -2.0 -> AudioParams(250.0, 0.0, 1.0, AudioMode.CONTINUOUS)

            // STRONG SINK
            vs > -3.0 -> AudioParams(200.0, 0.0, 1.0, AudioMode.CONTINUOUS)

            // EXTREME SINK
            else -> AudioParams(150.0, 0.0, 1.0, AudioMode.CONTINUOUS)
        }
    }

    /**
     * Linear interpolation between two points
     */
    private fun interpolate(
        vs: Double,
        vs1: Double,
        vs2: Double,
        freq1: Double,
        freq2: Double,
        cycle1: Double,
        cycle2: Double
    ): AudioParams {
        val ratio = (vs - vs1) / (vs2 - vs1)
        val frequency = freq1 + ratio * (freq2 - freq1)
        val cycleTime = cycle1 + ratio * (cycle2 - cycle1)

        return AudioParams(
            frequencyHz = frequency,
            cycleTimeMs = cycleTime,
            dutyCycle = settings.dutyCycle,
            mode = AudioMode.BEEPING
        )
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

/**
 * Variometer audio settings
 */
data class VarioAudioSettings(
    // General
    val enabled: Boolean = true,
    val volume: Float = 0.8f,  // 0.0 to 1.0

    // Lift thresholds
    val liftThreshold: Double = 0.2,  // m/s (start beeping)
    val weakLiftThreshold: Double = 0.5,  // m/s

    // Sink thresholds
    val sinkSilenceThreshold: Double = -2.0,  // m/s (audio warning below this)
    val strongSinkThreshold: Double = -3.0,  // m/s (louder warning)

    // Audio characteristics
    val minFrequency: Double = 400.0,  // Hz
    val maxFrequency: Double = 1200.0,  // Hz
    val dutyCycle: Double = 0.5,  // 0.0 to 1.0

    // Deadband
    val deadbandRange: Double = 0.2,  // m/s (±0.2 m/s)

    // Profile
    val profile: VarioAudioProfile = VarioAudioProfile.SILENT_SINK
)

/**
 * Variometer audio profile presets
 */
enum class VarioAudioProfile {
    COMPETITION,   // XCTracer-style, silence for sink
    PARAGLIDING,   // Gentler, slower beeps
    SILENT_SINK,   // No sink audio (most common)
    FULL_AUDIO     // Both lift and sink audio
}
