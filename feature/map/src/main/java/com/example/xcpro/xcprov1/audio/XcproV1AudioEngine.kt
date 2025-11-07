package com.example.xcpro.xcprov1.audio

import android.content.Context
import android.util.Log
import com.example.xcpro.audio.AudioMode
import com.example.xcpro.audio.AudioParams
import com.example.xcpro.audio.VarioBeepController
import com.example.xcpro.audio.VarioToneGenerator
import com.example.xcpro.xcprov1.model.FlightDataV1Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Dedicated audio engine for the XCPro V1 (HAWK-style) variometer.
 *
 * Features:
 * - Sub-100 ms latency
 * - Confidence-weighted lift detection
 * - Dual response to actual vs potential climb (anticipatory beeps)
 * - Rolling timbre shift for wind shear / stall warnings
 * - Independent from legacy audio engine
 */
class XcproV1AudioEngine(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

    companion object {
        private const val TAG = "XcproV1AudioEngine"
    }

    private val toneGenerator = VarioToneGenerator()
    private val beepController = VarioBeepController(toneGenerator, scope)
    private val mapper = XcproV1FrequencyMapper()

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _audioStats = MutableStateFlow(AudioTelemetry())
    val audioStats: StateFlow<AudioTelemetry> = _audioStats.asStateFlow()

    private var isInitialized = false

    fun initialize(): Boolean {
        if (isInitialized) return true
        return try {
            if (!toneGenerator.initialize()) {
                Log.e(TAG, "Tone generator failed to initialize")
                false
            } else {
                toneGenerator.setVolume(0.85f)
                beepController.start()
                isInitialized = true
                Log.i(TAG, "XCPro V1 audio engine initialized")
                true
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Error initialising XCPro V1 audio engine", ex)
            false
        }
    }

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        if (!enabled) {
            scope.launch {
                beepController.updateAudioParams(
                    AudioParams(
                        frequencyHz = 0.0,
                        cycleTimeMs = 0.0,
                        dutyCycle = 0.0,
                        mode = AudioMode.SILENCE
                    )
                )
            }
        }
    }

    fun updateFromSnapshot(snapshot: FlightDataV1Snapshot) {
        if (!isInitialized || !_enabled.value) return

        val params = mapper.map(snapshot)
        beepController.updateAudioParams(params)

        _audioStats.value = AudioTelemetry(
            actualClimb = snapshot.actualClimb,
            potentialClimb = snapshot.potentialClimb,
            frequencyHz = params.frequencyHz,
            cycleTimeMs = params.cycleTimeMs,
            dutyCycle = params.dutyCycle,
            mode = params.mode,
            confidence = snapshot.confidence
        )
    }

    fun release() {
        try {
            beepController.stop()
            beepController.release()
            toneGenerator.release()
            scope.cancel()
        } catch (ex: Exception) {
            Log.e(TAG, "Error releasing XCPro V1 audio engine", ex)
        } finally {
            isInitialized = false
        }
    }

    data class AudioTelemetry(
        val actualClimb: Double = 0.0,
        val potentialClimb: Double = 0.0,
        val frequencyHz: Double = 0.0,
        val cycleTimeMs: Double = 0.0,
        val dutyCycle: Double = 0.0,
        val mode: AudioMode = AudioMode.SILENCE,
        val confidence: Double = 0.0
    )

    /**
     * Frequency mapper tuned for HAWK-style behaviour.
     */
    private class XcproV1FrequencyMapper {

        private var smoothedPotentialGap = 0.0

        fun map(snapshot: FlightDataV1Snapshot): AudioParams {
            val vs = snapshot.actualClimb
            val confidence = snapshot.confidence.coerceIn(0.0, 1.0)
            val potentialGap = (snapshot.potentialClimb - vs).coerceIn(-2.0, 4.0)
            val netto = snapshot.netto

            // Smooth potential gap to avoid chatter
            smoothedPotentialGap = smoothedPotentialGap * 0.7 + potentialGap * 0.3

            val anticipatoryBoost = smoothedPotentialGap * 0.4 * confidence
            val synthesizedLift = vs + anticipatoryBoost

            val frequency = when {
                synthesizedLift >= 6.0 -> 1600.0
                synthesizedLift >= 4.0 -> interpolate(synthesizedLift, 4.0, 6.0, 1300.0, 1600.0)
                synthesizedLift >= 2.0 -> interpolate(synthesizedLift, 2.0, 4.0, 1050.0, 1300.0)
                synthesizedLift >= 1.0 -> interpolate(synthesizedLift, 1.0, 2.0, 850.0, 1050.0)
                synthesizedLift >= 0.4 -> interpolate(synthesizedLift, 0.4, 1.0, 650.0, 850.0)
                else -> 0.0
            }

            val cycleTime = when {
                synthesizedLift >= 6.0 -> 180.0
                synthesizedLift >= 4.0 -> interpolate(synthesizedLift, 4.0, 6.0, 240.0, 180.0)
                synthesizedLift >= 2.0 -> interpolate(synthesizedLift, 2.0, 4.0, 320.0, 240.0)
                synthesizedLift >= 1.0 -> interpolate(synthesizedLift, 1.0, 2.0, 450.0, 320.0)
                synthesizedLift >= 0.4 -> interpolate(synthesizedLift, 0.4, 1.0, 620.0, 450.0)
                else -> 0.0
            }

        val dutyBase = when {
                synthesizedLift >= 3.0 -> 0.6
                synthesizedLift >= 1.5 -> 0.5
                synthesizedLift >= 0.7 -> 0.45
                else -> 0.4
            }
            val dutyCycle = (dutyBase + confidence * 0.1).coerceIn(0.3, 0.75)

            val sinkMode = computeSinkWarning(vs, netto, confidence)
            if (sinkMode != null) return sinkMode

            return if (frequency > 0.0 && cycleTime > 0.0) {
                AudioParams(
                    frequencyHz = frequency,
                    cycleTimeMs = cycleTime,
                    dutyCycle = dutyCycle,
                    mode = AudioMode.BEEPING
                )
            } else {
                AudioParams(
                    frequencyHz = 0.0,
                    cycleTimeMs = 0.0,
                    dutyCycle = 0.0,
                    mode = AudioMode.SILENCE
                )
            }
        }

        private fun computeSinkWarning(
            verticalSpeed: Double,
            netto: Double,
            confidence: Double
        ): AudioParams? {
            if (verticalSpeed >= -0.5) return null

            val sinkSeverity = max(abs(verticalSpeed), abs(netto))
            val warningFrequency = when {
                sinkSeverity >= 6.0 -> 180.0
                sinkSeverity >= 3.5 -> interpolate(sinkSeverity, 3.5, 6.0, 210.0, 180.0)
                sinkSeverity >= 2.0 -> interpolate(sinkSeverity, 2.0, 3.5, 240.0, 210.0)
                sinkSeverity >= 1.0 -> interpolate(sinkSeverity, 1.0, 2.0, 280.0, 240.0)
                else -> return null
            }

            val tremoloDuty = 0.55 + 0.25 * min(confidence, 0.8)
            return AudioParams(
                frequencyHz = warningFrequency,
                cycleTimeMs = 0.0,
                dutyCycle = tremoloDuty.coerceIn(0.5, 0.8),
                mode = AudioMode.CONTINUOUS
            )
        }

        private fun interpolate(value: Double, minValue: Double, maxValue: Double, minOut: Double, maxOut: Double): Double {
            val clamped = value.coerceIn(minValue, maxValue)
            val ratio = (clamped - minValue) / (maxValue - minValue)
            return minOut + ratio * (maxOut - minOut)
        }
    }
}
