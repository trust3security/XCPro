package com.trust3.xcpro.audio

import com.trust3.xcpro.core.common.logging.AppLogger
import kotlinx.coroutines.*

/**
 * Controls variometer beep patterns using coroutines
 *
 * Manages:
 * - Beep timing (duty cycle, intervals)
 * - Smooth transitions between lift rates
 * - Continuous tones for sink
 * - Silence for deadband
 *
 * Performance: <100ms response time to match sensor fusion
 */
class VarioBeepController(
    private val toneOutput: VarioToneOutput,
    private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "VarioBeepController"
        private const val UPDATE_RATE_MS = 50L  // 20Hz update rate
        private const val TRANSITION_SMOOTHING = 0.3  // Smooth frequency transitions
        private const val TONE_ATTACK_MS = 5L
        private const val TONE_RELEASE_MS = 5L
    }

    // Current audio state
    private var currentParams: AudioParams? = null
    private var targetParams: AudioParams? = null
    private var isRunning = false
    private var lastMode: AudioMode? = null

    // Coroutine job for beep loop
    private var beepJob: Job? = null

    // Smoothed frequency for transitions
    private var smoothedFrequency = 0.0
    private var smoothedCycleTime = 0.0

    /**
     * Start the beep controller
     */
    fun start(): Boolean {
        if (isRunning) {
            AppLogger.d(TAG, "Start ignored; beep controller already running")
            return true
        }

        if (!toneOutput.isReady()) {
            AppLogger.w(TAG, "Beep controller start blocked; tone generator not ready")
            return false
        }

        isRunning = true
        startBeepLoop()
        AppLogger.i(TAG, "Beep controller started")
        return true
    }

    /**
     * Stop the beep controller
     */
    fun stop() {
        stopInternal(resetAudioState = false)
        AppLogger.i(TAG, "Beep controller stopped")
    }

    private fun stopInternal(resetAudioState: Boolean) {
        isRunning = false
        val runningJob = beepJob
        beepJob = null
        runningJob?.cancel()
        toneOutput.stop()
        lastMode = null
        if (resetAudioState) {
            currentParams = null
            targetParams = null
            smoothedFrequency = 0.0
            smoothedCycleTime = 0.0
        }
    }

    /**
     * Update audio parameters based on vertical speed
     *
     * @param params New audio parameters
     */
    fun updateAudioParams(params: AudioParams) {
        targetParams = params

        // If no current params, set immediately
        if (currentParams == null) {
            currentParams = params
            smoothedFrequency = params.frequencyHz
            smoothedCycleTime = params.cycleTimeMs
        }
    }

    /**
     * Main beep loop - runs continuously while started
     */
    private fun startBeepLoop() {
        beepJob = scope.launch {
            while (isRunning) {
                try {
                    val target = targetParams
                    val current = currentParams

                    if (target != null) {
                        // Smooth transition to target parameters
                        smoothTransition(target)

                        // Update current params
                        currentParams = AudioParams(
                            frequencyHz = smoothedFrequency,
                            cycleTimeMs = smoothedCycleTime,
                            dutyCycle = target.dutyCycle,
                            mode = target.mode
                        )

                        val previousMode = lastMode
                        lastMode = target.mode

                        // Play audio based on mode
                        when (target.mode) {
                            AudioMode.BEEPING -> {
                                if (previousMode == AudioMode.CONTINUOUS) {
                                    toneOutput.resetPhase()
                                }
                                playBeepCycle()
                            }
                            AudioMode.CONTINUOUS -> playContinuousTone(applyEnvelope = previousMode != AudioMode.CONTINUOUS)
                            AudioMode.SILENCE -> {
                                if (previousMode == AudioMode.CONTINUOUS) {
                                    toneOutput.resetPhase()
                                }
                                playSilence()
                            }
                        }
                    } else {
                        // No params yet, wait
                        delay(UPDATE_RATE_MS)
                    }

                } catch (e: CancellationException) {
                    // Coroutine cancelled, exit gracefully
                    break
                } catch (e: Exception) {
                    if (AppLogger.rateLimit(TAG, "beep_loop_error", 5_000L)) {
                        AppLogger.e(TAG, "Error in beep loop", e)
                    }
                    delay(100)  // Brief pause before retry
                }
            }
        }
    }

    /**
     * Smooth transition to target parameters
     * Prevents jarring frequency jumps
     */
    private fun smoothTransition(target: AudioParams) {
        // Exponential smoothing
        val alpha = TRANSITION_SMOOTHING

        smoothedFrequency = if (smoothedFrequency == 0.0) {
            target.frequencyHz
        } else {
            smoothedFrequency * (1 - alpha) + target.frequencyHz * alpha
        }

        smoothedCycleTime = if (smoothedCycleTime == 0.0) {
            target.cycleTimeMs
        } else {
            smoothedCycleTime * (1 - alpha) + target.cycleTimeMs * alpha
        }
    }

    /**
     * Play one beep cycle (tone + silence)
     */
    private suspend fun playBeepCycle() {
        val params = currentParams ?: return

        if (!params.isActive()) {
            delay(UPDATE_RATE_MS)
            return
        }

        val toneDuration = params.getToneDurationMs()
        val silenceDuration = params.getSilenceDurationMs()

        // Play tone
        if (toneDuration > 0) {
            toneOutput.playTone(
                frequencyHz = params.frequencyHz,
                durationMs = toneDuration,
                envelope = ToneEnvelope(
                    attackMs = TONE_ATTACK_MS,
                    releaseMs = TONE_RELEASE_MS
                )
            )
        }

        // Wait for tone to finish playing
        delay(toneDuration)

        // Play silence (pause between beeps)
        if (silenceDuration > 0) {
            toneOutput.playSilence(silenceDuration)
            delay(silenceDuration)
        }
    }

    /**
     * Play continuous tone (for sink warning)
     */
    private suspend fun playContinuousTone(applyEnvelope: Boolean) {
        val params = currentParams ?: return

        if (!params.isActive()) {
            delay(UPDATE_RATE_MS)
            return
        }

        // Play 1-second chunks of continuous tone
        val duration = 1000L
        toneOutput.playTone(
            frequencyHz = params.frequencyHz,
            durationMs = duration,
            envelope = if (applyEnvelope) {
                ToneEnvelope(
                    attackMs = TONE_ATTACK_MS,
                    releaseMs = TONE_RELEASE_MS
                )
            } else {
                ToneEnvelope()
            },
            preservePhase = true
        )

        delay(duration)
    }

    /**
     * Play silence (deadband)
     */
    private suspend fun playSilence() {
        toneOutput.playSilence(UPDATE_RATE_MS)
        delay(UPDATE_RATE_MS)
    }

    /**
     * Set volume
     */
    fun setVolume(volume: Float) {
        toneOutput.setVolume(volume)
    }

    /**
     * Get current audio mode
     */
    fun getCurrentMode(): AudioMode? {
        return currentParams?.mode
    }

    /**
     * Get current frequency
     */
    fun getCurrentFrequency(): Double {
        return smoothedFrequency
    }

    /**
     * Check if audio is currently active
     */
    fun isAudioActive(): Boolean {
        return currentParams?.isActive() == true
    }

    /**
     * Release resources
     */
    fun release() {
        stopInternal(resetAudioState = true)
    }
}
