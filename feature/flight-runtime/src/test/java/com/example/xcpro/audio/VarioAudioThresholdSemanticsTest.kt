package com.example.xcpro.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class VarioAudioThresholdSemanticsTest {

    @Test
    fun effective_lift_start_returns_canonical_value() {
        val settings = VarioAudioSettings(liftStartThreshold = 0.5)

        assertEquals(0.5, settings.effectiveLiftStartThreshold(), 0.0)
    }

    @Test
    fun effective_sink_start_returns_canonical_value() {
        val settings = VarioAudioSettings(sinkStartThreshold = -1.5)

        assertEquals(-1.5, settings.effectiveSinkStartThreshold(), 0.0)
    }

    @Test
    fun legacy_effective_lift_start_preserves_current_guard_for_inverted_deadband_values() {
        assertEquals(
            0.6,
            legacyEffectiveLiftStartThreshold(
                liftThreshold = 0.1,
                deadbandMin = 0.6,
                deadbandMax = 0.2
            ),
            0.0
        )
    }

    @Test
    fun legacy_effective_sink_start_uses_lower_of_sink_threshold_and_deadband_min() {
        assertEquals(
            -1.5,
            legacyEffectiveSinkStartThreshold(
                sinkSilenceThreshold = -1.5,
                deadbandMin = -0.1
            ),
            0.0
        )
    }

    @Test
    fun default_settings_preserve_current_effective_thresholds() {
        val settings = VarioAudioSettings()

        assertEquals(0.1, settings.effectiveLiftStartThreshold(), 0.0)
        assertEquals(-0.3, settings.effectiveSinkStartThreshold(), 0.0)
    }

    @Test
    fun with_effective_lift_start_threshold_updates_canonical_field() {
        val updated = VarioAudioSettings().withEffectiveLiftStartThreshold(0.7)

        assertEquals(0.7, updated.liftStartThreshold, 0.0)
        assertEquals(0.7, updated.effectiveLiftStartThreshold(), 0.0)
    }

    @Test
    fun with_effective_sink_start_threshold_updates_canonical_field() {
        val updated = VarioAudioSettings().withEffectiveSinkStartThreshold(-1.8)

        assertEquals(-1.8, updated.sinkStartThreshold, 0.0)
        assertEquals(-1.8, updated.effectiveSinkStartThreshold(), 0.0)
    }

    @Test
    fun canonical_audio_settings_from_legacy_threshold_pairs_collapses_divergent_raw_values() {
        val normalized = canonicalAudioSettingsFromLegacyThresholdPairs(
            enabled = false,
            volume = 0.33f,
            liftThreshold = 0.8,
            sinkSilenceThreshold = -0.5,
            dutyCycle = 0.55,
            deadbandMin = -1.5,
            deadbandMax = 0.2
        )

        assertEquals(false, normalized.enabled)
        assertEquals(0.33f, normalized.volume)
        assertEquals(0.8, normalized.liftStartThreshold, 0.0)
        assertEquals(-1.5, normalized.sinkStartThreshold, 0.0)
        assertEquals(0.55, normalized.dutyCycle, 0.0)
        assertEquals(0.8, normalized.effectiveLiftStartThreshold(), 0.0)
        assertEquals(-1.5, normalized.effectiveSinkStartThreshold(), 0.0)
    }
}
