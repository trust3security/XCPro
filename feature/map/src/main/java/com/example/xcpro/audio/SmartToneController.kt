package com.example.xcpro.audio

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

private enum class SmartToneMode {
    LIFT,
    SINK,
    SILENCE
}

private data class SmartToneFrame(
    val mode: SmartToneMode,
    val frequencyHz: Double = 0.0,
    val durationMs: Long = 0,
    val silenceMs: Long = 0,
    val envelope: ToneEnvelope = ToneEnvelope(),
    val components: List<ToneComponent> = emptyList(),
    val volumeScale: Float = 1f
)

class SmartToneController(
    private val toneGenerator: VarioToneGenerator,
    private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "SmartToneController"
        private const val LIFT_ON_THRESHOLD = 0.25
        private const val LIFT_OFF_THRESHOLD = 0.1
        private const val SINK_ON_THRESHOLD = -1.5
        private const val SINK_OFF_THRESHOLD = -1.2
        private const val SINK_GATE_MS = 300L
        private const val SUBHARMONIC_WINDOW_MS = 200L
        private const val DUCK_WINDOW_MS = 150L
        private const val SILENCE_TICK_MS = 60L
    }

    @Volatile
    private var latestVerticalSpeed = 0.0
    @Volatile
    private var forceSilence = false

    private var controllerJob: Job? = null
    private var isRunning = false
    private var lastMode = SmartToneMode.SILENCE
    private var lastSign = 0
    private var sinkGateStart = 0L
    private var subharmonicUntil = 0L
    private var duckUntil = 0L
    private var lastFrequency = 0.0
    private var currentMode: AudioMode = AudioMode.SILENCE

    fun start() {
        if (isRunning) {
            return
        }
        isRunning = true
        controllerJob = scope.launch {
            while (isRunning) {
                try {
                    val frame = buildFrame(System.currentTimeMillis())
                    playFrame(frame)
                } catch (e: Exception) {
                    Log.e(TAG, "Smart tone loop error", e)
                    delay(20)
                }
            }
        }
        Log.i(TAG, "Smart tone controller started")
    }

    fun stop() {
        isRunning = false
        controllerJob?.cancel()
        controllerJob = null
        toneGenerator.stop()
        lastMode = SmartToneMode.SILENCE
        currentMode = AudioMode.SILENCE
        Log.i(TAG, "Smart tone controller stopped")
    }

    fun release() {
        stop()
    }

    fun updateVerticalSpeed(verticalSpeed: Double) {
        latestVerticalSpeed = verticalSpeed
    }

    fun setSilence() {
        forceSilence = true
        toneGenerator.stop()
        lastMode = SmartToneMode.SILENCE
        currentMode = AudioMode.SILENCE
    }

    fun isAudioActive(): Boolean = lastMode != SmartToneMode.SILENCE

    fun getCurrentFrequency(): Double = lastFrequency

    fun getCurrentMode(): AudioMode = currentMode

    private suspend fun playFrame(frame: SmartToneFrame) {
        when (frame.mode) {
            SmartToneMode.LIFT, SmartToneMode.SINK -> {
                val volumeScale = frame.volumeScale.coerceIn(0f, 1f)
                val scaledVolume = (toneGenerator.getVolume() * volumeScale).coerceIn(0f, 1f)
                toneGenerator.playTone(
                    frequencyHz = frame.frequencyHz,
                    durationMs = frame.durationMs,
                    volume = scaledVolume,
                    envelope = frame.envelope,
                    components = frame.components
                )
                delay(frame.durationMs)
                if (frame.silenceMs > 0) {
                    toneGenerator.playSilence(frame.silenceMs)
                    delay(frame.silenceMs)
                }
            }
            SmartToneMode.SILENCE -> {
                toneGenerator.playSilence(frame.silenceMs)
                delay(frame.silenceMs)
            }
        }
    }

    private fun buildFrame(now: Long): SmartToneFrame {
        if (!isRunning) {
            return SmartToneFrame(SmartToneMode.SILENCE, silenceMs = SILENCE_TICK_MS)
        }

        if (forceSilence) {
            forceSilence = false
            lastMode = SmartToneMode.SILENCE
            currentMode = AudioMode.SILENCE
            return SmartToneFrame(SmartToneMode.SILENCE, silenceMs = SILENCE_TICK_MS)
        }

        val vz = latestVerticalSpeed
        val clampedVz = when {
            vz.isNaN() -> 0.0
            vz > 10 -> 10.0
            vz < -10 -> -10.0
            else -> vz
        }

        val newSign = when {
            clampedVz > LIFT_ON_THRESHOLD -> 1
            clampedVz < -LIFT_ON_THRESHOLD -> -1
            else -> 0
        }
        if (newSign > 0 && lastSign <= 0) {
            subharmonicUntil = now + SUBHARMONIC_WINDOW_MS
        } else if (newSign < 0 && lastSign >= 0) {
            duckUntil = now + DUCK_WINDOW_MS
        }
        if (newSign != 0) {
            lastSign = newSign
        }

        val liftActive = when {
            clampedVz >= LIFT_ON_THRESHOLD -> true
            lastMode == SmartToneMode.LIFT && clampedVz > LIFT_OFF_THRESHOLD -> true
            else -> false
        }

        if (liftActive) {
            sinkGateStart = 0L
            val frame = buildLiftFrame(clampedVz, now)
            lastMode = SmartToneMode.LIFT
            currentMode = AudioMode.SMART
            lastFrequency = frame.frequencyHz
            return frame
        }

        val sinkActive = when {
            clampedVz <= SINK_ON_THRESHOLD -> {
                if (sinkGateStart == 0L) sinkGateStart = now
                (now - sinkGateStart) >= SINK_GATE_MS
            }
            clampedVz < SINK_OFF_THRESHOLD && sinkGateStart != 0L -> (now - sinkGateStart) >= SINK_GATE_MS
            else -> {
                sinkGateStart = 0L
                false
            }
        }

        if (sinkActive) {
            val frame = buildSinkFrame(clampedVz, now)
            lastMode = SmartToneMode.SINK
            currentMode = AudioMode.SMART
            lastFrequency = frame.frequencyHz
            return frame
        }

        lastMode = SmartToneMode.SILENCE
        currentMode = AudioMode.SILENCE
        return SmartToneFrame(SmartToneMode.SILENCE, silenceMs = SILENCE_TICK_MS)
    }

    private fun buildLiftFrame(vz: Double, now: Long): SmartToneFrame {
        val clampedVz = vz.coerceIn(0.0, 5.0)
        val baseFrequency = 500.0 + 160.0 * clampedVz

        val toneDuration = (70.0 + 25.0 * clampedVz).coerceAtMost(250.0).toLong()
        val silenceDuration = (400.0 - 50.0 * clampedVz).coerceAtLeast(80.0).toLong()

        val envelope = when {
            clampedVz < 0.8 -> ToneEnvelope(attackMs = 100, releaseMs = 70)
            clampedVz < 1.5 -> ToneEnvelope(attackMs = 40, releaseMs = 60)
            else -> ToneEnvelope(attackMs = 15, releaseMs = 50)
        }

        val harmonics = mutableListOf<ToneComponent>()
        if (clampedVz > 2.0) {
            harmonics += ToneComponent(ratio = 2.0, gain = dbToGain(-20.0))
        }
        if (now < subharmonicUntil) {
            harmonics += ToneComponent(ratio = 0.5, gain = dbToGain(-20.0))
        }

        lastFrequency = baseFrequency

        return SmartToneFrame(
            mode = SmartToneMode.LIFT,
            frequencyHz = baseFrequency,
            durationMs = max(40, toneDuration),
            silenceMs = silenceDuration,
            envelope = envelope,
            components = harmonics
        )
    }

    private fun buildSinkFrame(vz: Double, now: Long): SmartToneFrame {
        val sinkStrength = abs(vz)
        val frequency = max(220.0, 380.0 - 60.0 * sinkStrength)

        val toneDuration = (80.0 + (sinkStrength - 1.5) * 25.0).coerceIn(60.0, 180.0).toLong()
        val silenceDuration = (420.0 - (sinkStrength - 1.5) * 70.0).coerceIn(180.0, 600.0).toLong()
        val envelope = ToneEnvelope(attackMs = 10, releaseMs = 45)

        val volumeScale = if (now < duckUntil) 0.5f else 1f

        lastFrequency = frequency

        return SmartToneFrame(
            mode = SmartToneMode.SINK,
            frequencyHz = frequency,
            durationMs = toneDuration,
            silenceMs = silenceDuration,
            envelope = envelope,
            volumeScale = volumeScale
        )
    }

    private fun dbToGain(db: Double): Double {
        return 10.0.pow(db / 20.0)
    }
}
