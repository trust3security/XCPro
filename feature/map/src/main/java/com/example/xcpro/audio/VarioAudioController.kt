package com.example.xcpro.audio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope

/**
 * Thin wrapper around [VarioAudioEngine] that keeps flight-domain code side-effect free.
 * Sensors push vertical speed samples here; the controller decides whether to emit audio or silence.
 */
class VarioAudioController(
    context: Context,
    scope: CoroutineScope,
    private val enableAudio: Boolean
) {

    companion object {
        private const val TAG = "VarioAudioController"
    }

    val engine: VarioAudioEngine = VarioAudioEngine(context, scope)

    init {
        val initialized = engine.initialize()
        if (initialized && enableAudio) {
            engine.start()
            Log.i(TAG, "Audio engine initialized and started")
        } else if (!initialized) {
            Log.w(TAG, "Audio engine initialization failed; disabling audio")
        }
    }

    fun update(teSample: Double?, rawVario: Double, currentTime: Long, validUntil: Long) {
        if (!enableAudio) {
            return
        }
        when {
            teSample != null && currentTime <= validUntil -> engine.updateVerticalSpeed(teSample)
            currentTime <= validUntil -> engine.updateVerticalSpeed(rawVario)
            else -> engine.setSilence()
        }
    }

    fun stop() {
        engine.stop()
        engine.release()
    }
}
