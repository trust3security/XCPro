package com.example.xcpro.audio

import android.content.Context
import android.util.Log
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
 * val engine = VarioAudioEngine(context)
 * engine.initialize()
 * engine.start()
 * engine.updateVerticalSpeed(verticalSpeedMs)
 * engine.stop()
 * engine.release()
 * ```
 */
class VarioAudioEngine(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

    companion object {
        private const val TAG = "VarioAudioEngine"
    }

    // Components
    private val toneGenerator = VarioToneGenerator()
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

    // Statistics
    private var audioUpdatesCount = 0L
    private var lastLogTime = 0L

    /**
     * Initialize the audio engine
     * Must be called before start()
     */
    fun initialize(): Boolean {
        if (isInitialized) {
            Log.w(TAG, "Already initialized")
            return true
        }

        try {
            // Initialize tone generator
            if (!toneGenerator.initialize()) {
                Log.e(TAG, "Failed to initialize tone generator")
                return false
            }

            // Create frequency mapper with current settings
            frequencyMapper = VarioFrequencyMapper(_settings.value)

            // Create beep controller
            beepController = VarioBeepController(toneGenerator, scope)

            // Apply initial volume
            toneGenerator.setVolume(_settings.value.volume)

            isInitialized = true
            Log.i(TAG, "Audio engine initialized (profile: ${_settings.value.profile})")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize audio engine", e)
            return false
        }
    }

    /**
     * Start the audio engine
     * Begins processing vertical speed updates
     */
    fun start() {
        if (!isInitialized) {
            Log.w(TAG, "Not initialized, call initialize() first")
            return
        }

        if (isStarted) {
            Log.w(TAG, "Already started")
            return
        }

        if (!_isEnabled.value) {
            Log.i(TAG, "Audio engine disabled, not starting")
            return
        }

        try {
            beepController.start()
            isStarted = true
            Log.i(TAG, "Audio engine started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio engine", e)
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
            beepController.stop()
            isStarted = false
            Log.i(TAG, "Audio engine stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop audio engine", e)
        }
    }

    /**
     * Update vertical speed and adjust audio
     * Call this from FlightDataCalculator whenever V/S changes
     *
     * @param verticalSpeedMs TE-compensated vertical speed in m/s
     */
    fun updateVerticalSpeed(verticalSpeedMs: Double) {
        if (!isInitialized || !isStarted || !_isEnabled.value) {
            return
        }

        try {
            // Map vertical speed to audio parameters
            val audioParams = frequencyMapper.mapVerticalSpeed(verticalSpeedMs)

            // Update beep controller
            beepController.updateAudioParams(audioParams)

            // Update state flows
            _currentFrequency.value = beepController.getCurrentFrequency()
            _currentMode.value = audioParams.mode

            // Statistics
            audioUpdatesCount++
            logStatistics(verticalSpeedMs, audioParams)

        } catch (e: Exception) {
            Log.e(TAG, "Error updating vertical speed", e)
        }
    }

    /**
     * Update settings
     * Recreates frequency mapper with new settings
     */
    fun updateSettings(newSettings: VarioAudioSettings) {
        _settings.value = newSettings

        // Recreate frequency mapper with new settings
        frequencyMapper = VarioFrequencyMapper(newSettings)

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

        Log.i(TAG, "Settings updated (profile: ${newSettings.profile}, enabled: ${newSettings.enabled})")
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
     * Set audio profile
     */
    fun setProfile(profile: VarioAudioProfile) {
        val newSettings = _settings.value.copy(profile = profile)
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
     * Play test tone at specific frequency
     * Useful for settings UI
     */
    fun playTestTone(frequencyHz: Double, durationMs: Long = 1000) {
        if (!isInitialized) {
            Log.w(TAG, "Not initialized")
            return
        }

        scope.launch {
            try {
                toneGenerator.playTone(frequencyHz, durationMs)
                delay(durationMs)
            } catch (e: Exception) {
                Log.e(TAG, "Error playing test tone", e)
            }
        }
    }

    /**
     * Play test beep pattern for specific vertical speed
     * Useful for settings UI
     */
    fun playTestPattern(verticalSpeedMs: Double, durationMs: Long = 3000) {
        if (!isInitialized) {
            Log.w(TAG, "Not initialized")
            return
        }

        scope.launch {
            try {
                val wasStarted = isStarted

                // Start temporarily if not running
                if (!wasStarted) {
                    beepController.start()
                }

                // Map vertical speed and play for duration
                val audioParams = frequencyMapper.mapVerticalSpeed(verticalSpeedMs)
                beepController.updateAudioParams(audioParams)

                delay(durationMs)

                // Stop if we started it
                if (!wasStarted) {
                    beepController.stop()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error playing test pattern", e)
            }
        }
    }

    /**
     * Check if audio is currently active (making sound)
     */
    fun isAudioActive(): Boolean {
        return isStarted && beepController.isAudioActive()
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
            profile = _settings.value.profile,
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
            beepController.release()
            toneGenerator.release()
            scope.cancel()
            isInitialized = false
            Log.i(TAG, "Audio engine released (total updates: $audioUpdatesCount)")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio engine", e)
        }
    }

    /**
     * Log statistics periodically (every 10 seconds)
     */
    private fun logStatistics(vs: Double, params: AudioParams) {
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastLogTime > 10000) {
            Log.d(TAG, "Audio stats: V/S=${String.format("%.2f", vs)}m/s, " +
                    "Freq=${String.format("%.0f", params.frequencyHz)}Hz, " +
                    "Mode=${params.mode}, " +
                    "Updates=$audioUpdatesCount")
            lastLogTime = currentTime
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
    val profile: VarioAudioProfile,
    val volume: Float,
    val audioActive: Boolean
)
