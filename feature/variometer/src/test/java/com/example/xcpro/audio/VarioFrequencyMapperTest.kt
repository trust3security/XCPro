package com.example.xcpro.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class VarioFrequencyMapperTest {

    @Test
    fun lift_beeps_start_at_higher_of_lift_threshold_and_deadband_max() {
        val mapper = VarioFrequencyMapper(
            VarioAudioSettings(liftStartThreshold = 0.5)
        )

        assertEquals(AudioMode.SILENCE, mapper.mapVerticalSpeed(0.49).mode)
        assertEquals(AudioMode.BEEPING, mapper.mapVerticalSpeed(0.5).mode)
    }

    @Test
    fun sink_tone_starts_from_lower_of_sink_threshold_and_deadband_min() {
        val mapper = VarioFrequencyMapper(
            VarioAudioSettings(sinkStartThreshold = -1.5)
        )

        assertEquals(AudioMode.SILENCE, mapper.mapVerticalSpeed(-0.3).mode)
        assertEquals(AudioMode.CONTINUOUS, mapper.mapVerticalSpeed(-1.6).mode)
        assertEquals(AudioMode.CONTINUOUS, mapper.mapVerticalSpeed(-1.45).mode)
    }

    @Test
    fun default_thresholds_match_current_behavior() {
        val mapper = VarioFrequencyMapper()

        assertEquals(AudioMode.SILENCE, mapper.mapVerticalSpeed(0.09).mode)
        assertEquals(AudioMode.BEEPING, mapper.mapVerticalSpeed(0.1).mode)
        assertEquals(AudioMode.SILENCE, mapper.mapVerticalSpeed(-0.39).mode)
        assertEquals(AudioMode.CONTINUOUS, mapper.mapVerticalSpeed(-0.4).mode)
    }
}
