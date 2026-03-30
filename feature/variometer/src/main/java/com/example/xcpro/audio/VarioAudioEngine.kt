package com.example.xcpro.audio

import android.content.Context
import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.core.time.TimeBridge
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Professional variometer audio engine
 *
 * Integrates:
 * - ToneGenerator (low-latency audio)
 * - FrequencyMapper (V/S to audio params)
 * - BeepController (timing and patterns)
 *
 * Features:
 * - Zero-lag response (<100ms)
 * - Professional frequency mapping (XCTracer-style)
 * - Configurable profiles (Competition, Paragliding, etc.)
 * - Background operation
 *
 * Usage:
 * ```
 * val engine = VarioAudioEngine(context, audioFocusManager, scope)
 * engine.initialize()
 * engine.start()
 * engine.updateVerticalSpeed(verticalSpeedMs)
 * engine.stop()
 * engine.release()
 * ```
 */
class VarioAudioEngine(
    private val context: Context,
    private val audioFocusManager: AudioFocusManager,
    scope: CoroutineScope
) {

    companion object {
        private const val TAG = "VarioAudioEngine"
        private const val ENSURE_BACKOFF_MS = 1_000L
    }

    // Components
    private val toneGenerator = VarioToneGenerator()
    private val audioScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
    private lateinit var frequencyMapper: VarioFrequencyMapper
    private lateinit var beepController: VarioBeepController

    // Settings
    private val _settings = MutableStateFlow(VarioAudioSettings())
    val settings: StateFlow<VarioAudioSettings> = _settings.asStateFlow()

    // Current state
    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _currentFrequency = MutableStateFlow(0.0)
    val currentFrequency: StateFlow<Double> = _currentFrequency.asStateFlow()

    private val _currentMode = MutableStateFlow(AudioMode.SILENCE)
    val currentMode: StateFlow<AudioMode> = _currentMode.asStateFlow()

    private var isInitialized = false
    private var isStarted = false
    private var lastEnsureAttemptElapsedMs = 0L
    private var ensureAttemptCount = 0

    // Statistics
    private var audioUpdatesCount = 0L

    /**
     * Initialize the audio engine
     * Must be called before start()
     */
    fun initialize(): Boolean {
        if (isInitialized && toneGenerator.isReady()) {
            AppLogger.d(TAG, "Initialize ignored; engine already ready")
            return true
        }

        try {
            if (isInitialized && !toneGenerator.isReady()) {
                if (AppLogger.rateLimit(TAG, "tone_generator_reinit", 5_000L)) {
                    AppLogger.w(TAG, "Tone generator lost readiness; reinitializing audio engine")
                }
                cleanupForReinit()
            } else if (!isInitialized && this::beepController.isInitialized) {
                // A previous init attempt partially succeeded; clean it up before retrying.
                cleanupForReinit()
            }
            // Initialize tone generator
            if (!toneGenerator.initialize()) {
                AppLogger.e(TAG, "Failed to initialize tone generator")
                return false
            }

            // Create frequency mapper with current settings
            frequencyMapper = VarioFrequencyMapper(_settings.value)

            // Create controller
            beepController = VarioBeepController(toneGenerator, audioScope)

            // Apply initial volume
            toneGenerator.setVolume(_settings.value.volume)

            isInitialized = true
            AppLogger.i(TAG, "Audio engine initialized")
            return true

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to initialize audio engine", e)
            return false
        }
    }

    /**
     * Start the audio engine
     * Begins processing vertical speed updates
     */
    fun start() {
        if (isStarted) {
            AppLogger.d(TAG, "Start ignored; audio engine already started")
            return
        }

        if (!_isEnabled.value) {
            AppLogger.d(TAG, "Start skipped; audio engine disabled")
            return
        }

        try {
            if (!isInitialized && !initialize()) {
                AppLogger.w(TAG, "Start requested but initialization failed")
                return
            }
            val focusGranted = audioFocusManager.requestFocus()
            if (!focusGranted) {
                if (AppLogger.rateLimit(TAG, "focus_not_granted", 5_000L)) {
                    AppLogger.w(TAG, "Audio focus not granted; will retry on updates")
                }
                return
            }
            val started = beepController.start()
            isStarted = started
            if (started) {
                AppLogger.i(TAG, "Audio engine started")
            } else {
                if (AppLogger.rateLimit(TAG, "start_failed", 5_000L)) {
                    AppLogger.w(TAG, "Audio engine start failed; will retry on updates")
                }
                audioFocusManager.abandonFocus()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to start audio engine", e)
            audioFocusManager.abandonFocus()
            isStarted = false
        }
    }

    /**
     * Stop the audio engine
     * Stops audio output but keeps engine initialized
     */
    fun stop() {
        if (!isStarted) {
            return
        }

        try {
            if (this::beepController.isInitialized) {
                beepController.stop()
            }
            isStarted = false
            audioFocusManager.abandonFocus()
            AppLogger.i(TAG, "Audio engine stopped")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to stop audio engine", e)
        }
    }

    private fun shouldAttemptEnsure(): Boolean {
        val now = TimeBridge.nowMonoMs()
        if (now - lastEnsureAttemptElapsedMs < ENSURE_BACKOFF_MS) {
            return false
        }
        lastEnsureAttemptElapsedMs = now
        ensureAttemptCount++
        return true
    }

    private fun cleanupForReinit() {
        runCatching {
            if (this::beepController.isInitialized) {
                beepController.release()
            }
        }
        runCatching { toneGenerator.release() }
        runCatching { audioFocusManager.abandonFocus() }
        isStarted = false
        isInitialized = false
        _currentMode.value = AudioMode.SILENCE
        _currentFrequency.value = 0.0
    }

    private fun ensureStarted(): Boolean {
        if (!_isEnabled.value) {
            return false
        }
        if (isInitialized && !toneGenerator.isReady()) {
            if (AppLogger.rateLimit(TAG, "tone_generator_not_ready", 5_000L)) {
                AppLogger.w(TAG, "Tone generator not ready; resetting audio engine")
            }
            cleanupForReinit()
        }
        if (isInitialized && isStarted && audioFocusManager.hasFocus()) {
            return true
        }
        if (isInitialized && isStarted && !audioFocusManager.hasFocus()) {
            if (AppLogger.rateLimit(TAG, "focus_lost", 5_000L)) {
                AppLogger.w(TAG, "Audio focus lost; stopping beep controller")
            }
            if (this::beepController.isInitialized) {
                beepController.stop()
            }
            isStarted = false
        }
        if (!shouldAttemptEnsure()) {
            return false
        }
        start()
        val ready = isInitialized && isStarted
        if (ready) {
            ensureAttemptCount = 0
        } else {
            if (AppLogger.rateLimit(TAG, "ensure_failed", 5_000L)) {
                AppLogger.w(TAG, "Audio ensure attempt $ensureAttemptCount failed")
            }
        }
        return ready
    }

    /**
     * Update vertical speed and adjust audio
     * Call this from FlightDataCalculator whenever V/S changes
     *
     * @param verticalSpeedMs TE-compensated vertical speed in m/s
     */
    fun updateVerticalSpeed(verticalSpeedMs: Double) {
        if (!_isEnabled.value) {
            return
        }
        if (!ensureStarted()) {
            return
        }

        try {
            val audioParams = frequencyMapper.mapVerticalSpeed(verticalSpeedMs)
            beepController.updateAudioParams(audioParams)
            _currentFrequency.value = beepController.getCurrentFrequency()
            _currentMode.value = audioParams.mode
            audioUpdatesCount++
            logStatistics(verticalSpeedMs, audioParams)
        } catch (e: Exception) {
            if (AppLogger.rateLimit(TAG, "update_vertical_speed_error", 2_000L)) {
                AppLogger.e(TAG, "Error updating vertical speed", e)
            }
        }
    }

    /**
     * Force the audio engine into silence (used when no fresh vario data).
     */
    fun setSilence() {
        if (!isInitialized || !isStarted || !this::beepController.isInitialized) {
            return
        }
        try {
            val silenceParams = AudioParams(
                frequencyHz = 0.0,
                cycleTimeMs = 1000.0,
                dutyCycle = 0.0,
                mode = AudioMode.SILENCE
            )
            beepController.updateAudioParams(silenceParams)
            _currentMode.value = AudioMode.SILENCE
            _currentFrequency.value = 0.0
        } catch (e: Exception) {
            if (AppLogger.rateLimit(TAG, "set_silence_error", 2_000L)) {
                AppLogger.e(TAG, "Error setting silence", e)
            }
        }
    }

    /**
     * Update settings
     * Recreates frequency mapper with new settings
     */
    fun updateSettings(newSettings: VarioAudioSettings) {
        _settings.value = newSettings

        // Recreate frequency mapper with new settings once initialization succeeds.
        if (isInitialized) {
            frequencyMapper = VarioFrequencyMapper(newSettings)
        }

        // Update volume
        toneGenerator.setVolume(newSettings.volume)

        // Update enabled state
        _isEnabled.value = newSettings.enabled

        // Restart if needed
        if (newSettings.enabled && !isStarted) {
            start()
        } else if (!newSettings.enabled && isStarted) {
            stop()
        }

        AppLogger.d(TAG, "Settings updated (enabled=${newSettings.enabled})")
    }

    /**
     * Set volume
     * @param volume 0.0 (mute) to 1.0 (full)
     */
    fun setVolume(volume: Float) {
        val newSettings = _settings.value.copy(volume = volume.coerceIn(0f, 1f))
        updateSettings(newSettings)
    }

    /**
     * Enable/disable audio
     */
    fun setEnabled(enabled: Boolean) {
        val newSettings = _settings.value.copy(enabled = enabled)
        updateSettings(newSettings)
    }

    /**
     * Check if audio is currently active (making sound)
     */
    fun isAudioActive(): Boolean {
        if (!isStarted || !this::beepController.isInitialized) return false
        return beepController.isAudioActive()
    }

    /**
     * Get current playback state
     */
    fun getPlaybackState(): AudioEngineState {
        return AudioEngineState(
            initialized = isInitialized,
            started = isStarted,
            enabled = _isEnabled.value,
            currentMode = _currentMode.value,
            currentFrequency = _currentFrequency.value,
            volume = _settings.value.volume,
            audioActive = isAudioActive()
        )
    }

    /**
     * Release all resources
     * Call when done with audio engine
     */
    fun release() {
        try {
            stop()
            if (this::beepController.isInitialized) {
                beepController.release()
            }
            toneGenerator.release()
            // Keep the engine scope alive: this engine is reused by a DI singleton after
            // foreground-service restarts, so cancellation here would make future beep loops inert.
            isInitialized = false
            ensureAttemptCount = 0
            lastEnsureAttemptElapsedMs = 0L
            _currentMode.value = AudioMode.SILENCE
            _currentFrequency.value = 0.0
            AppLogger.i(TAG, "Audio engine released (total updates=$audioUpdatesCount)")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error releasing audio engine", e)
        }
    }

    /**
     * Log statistics periodically (every 10 seconds)
     */
    private fun logStatistics(vs: Double, params: AudioParams) {
        AppLogger.dRateLimited(TAG, "audio_stats", 10_000L) {
            "Audio stats: V/S=${String.format(Locale.US, "%.2f", vs)}m/s, " +
                "Freq=${String.format(Locale.US, "%.0f", params.frequencyHz)}Hz, " +
                "Mode=${params.mode}, " +
                "Updates=$audioUpdatesCount"
        }
    }
}

/**
 * Audio engine state snapshot
 */
data class AudioEngineState(
    val initialized: Boolean,
    val started: Boolean,
    val enabled: Boolean,
    val currentMode: AudioMode,
    val currentFrequency: Double,
    val volume: Float,
    val audioActive: Boolean
)
