package com.trust3.xcpro.audio

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VarioBeepControllerTest {

    @Test
    fun stop_stops_tone_synchronously_and_prevents_further_cycles() = runTest {
        val toneOutput = FakeVarioToneOutput()
        val controller = VarioBeepController(
            toneOutput = toneOutput,
            scope = this
        )

        controller.updateAudioParams(BEEPING_PARAMS)

        assertTrue(controller.start())
        runCurrent()
        assertEquals(1, toneOutput.playToneCalls)

        controller.stop()

        assertEquals(1, toneOutput.stopCalls)
        assertFalse(toneOutput.isPlaying)

        val toneCallsAfterStop = toneOutput.playToneCalls
        advanceTimeBy(500)
        runCurrent()

        assertEquals(toneCallsAfterStop, toneOutput.playToneCalls)
    }

    @Test
    fun stop_allows_immediate_restart_on_same_owner_scope() = runTest {
        val toneOutput = FakeVarioToneOutput()
        val controller = VarioBeepController(
            toneOutput = toneOutput,
            scope = this
        )

        controller.updateAudioParams(BEEPING_PARAMS)

        assertTrue(controller.start())
        runCurrent()
        controller.stop()

        assertTrue(controller.start())
        runCurrent()

        assertEquals(2, toneOutput.playToneCalls)
        assertEquals(1, toneOutput.stopCalls)

        controller.stop()
    }

    private class FakeVarioToneOutput : VarioToneOutput {
        var playToneCalls = 0
        var playSilenceCalls = 0
        var stopCalls = 0
        var resetPhaseCalls = 0
        var isPlaying = false

        override fun isReady(): Boolean = true

        override fun playTone(
            frequencyHz: Double,
            durationMs: Long,
            volume: Float,
            envelope: ToneEnvelope,
            components: List<ToneComponent>,
            preservePhase: Boolean
        ) {
            playToneCalls++
            isPlaying = true
        }

        override fun playSilence(durationMs: Long) {
            playSilenceCalls++
            isPlaying = false
        }

        override fun setVolume(volume: Float) = Unit

        override fun stop() {
            stopCalls++
            isPlaying = false
        }

        override fun resetPhase() {
            resetPhaseCalls++
        }
    }

    private companion object {
        val BEEPING_PARAMS = AudioParams(
            frequencyHz = 900.0,
            cycleTimeMs = 100.0,
            dutyCycle = 0.5,
            mode = AudioMode.BEEPING
        )
    }
}
