package com.trust3.xcpro.audio

import android.content.Context
import com.trust3.xcpro.core.common.logging.AppLogger
import kotlinx.coroutines.CoroutineScope

/**
 * Thin wrapper around [VarioAudioEngine] that keeps flight-domain code side-effect free.
 * Sensors push vertical speed samples here; the controller decides whether to emit audio or silence.
 */
class VarioAudioController(
    context: Context,
    audioFocusManager: AudioFocusManager,
    scope: CoroutineScope,
    private val enableAudio: Boolean
) {

    companion object {
        private const val TAG = "VarioAudioController"
    }

    val engine: VarioAudioEngine = VarioAudioEngine(context, audioFocusManager, scope)

    init {
        val initialized = engine.initialize()
        if (initialized && enableAudio) {
            engine.start()
            AppLogger.i(TAG, "Audio engine initialized and started")
        } else if (!initialized) {
            AppLogger.w(TAG, "Audio engine initialization failed; disabling audio")
        }
    }

    fun update(teSample: Double?, rawVario: Double, currentTime: Long, validUntil: Long): Double? {
        val selected = when {
            teSample != null && currentTime <= validUntil -> teSample
            currentTime <= validUntil -> rawVario
            else -> null
        }
        if (!enableAudio) {
            return selected
        }
        if (selected != null) {
            engine.updateVerticalSpeed(selected)
        } else {
            engine.setSilence()
        }
        return selected
    }

    fun stop() {
        engine.stop()
        engine.release()
    }
}
