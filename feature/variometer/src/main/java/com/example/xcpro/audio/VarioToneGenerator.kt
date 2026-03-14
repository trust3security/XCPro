package com.example.xcpro.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.xcpro.core.common.logging.AppLogger
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Low-latency tone generator for variometer audio
 *
 * Uses AudioTrack with pre-generated sine waves for minimal latency (<10ms).
 * Generates pure sine tones at specified frequencies for lift/sink indication.
 *
 * Performance targets:
 * - Latency: <10ms
 * - CPU usage: <5%
 * - Zero dropouts
 */
data class ToneEnvelope(
    val attackMs: Long = 0,
    val releaseMs: Long = 0
)

data class ToneComponent(
    val ratio: Double,
    val gain: Double
)

class VarioToneGenerator {

    companion object {
        private const val TAG = "VarioToneGenerator"
        private const val SAMPLE_RATE = 44100 // Hz (CD quality)
        private const val MAX_FREQUENCY = 2000.0 // Hz (reasonable upper limit)
        private const val SILENCE_RAMP_MS = 5L
    }

    // Audio track for low-latency playback
    private var audioTrack: AudioTrack? = null
    private var isInitialized = false
    private var currentVolume = 0.8f
    private var phaseAccumulator = 0.0
    private var lastSampleValue: Short = 0

    private val silenceBuffer = ShortArray(SAMPLE_RATE)

    /**
     * Initialize the AudioTrack
     * Must be called before playing tones
     */
    @Synchronized
    fun initialize(): Boolean {
        if (isInitialized) {
            AppLogger.d(TAG, "Initialize ignored; AudioTrack already initialized")
            return true
        }

        try {
            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (bufferSize == AudioTrack.ERROR_BAD_VALUE || bufferSize == AudioTrack.ERROR || bufferSize <= 0) {
                AppLogger.e(TAG, "Invalid AudioTrack buffer size: $bufferSize")
                return false
            }

            // Use 4x min buffer for smooth playback
            val optimalBufferSize = bufferSize * 4

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(optimalBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            val track = audioTrack
            if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
                AppLogger.e(TAG, "AudioTrack not initialized (state=${track?.state})")
                resetAudioTrack("init_state")
                return false
            }

            track.play()
            isInitialized = true

            AppLogger.d(TAG, "AudioTrack initialized (buffer=$optimalBufferSize, sampleRate=$SAMPLE_RATE)")
            return true

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to initialize AudioTrack", e)
            return false
        }
    }

    /**
     * Generate and play a tone at specified frequency
     *
     * @param frequencyHz Frequency in Hz (200-2000 Hz typical)
     * @param durationMs Duration in milliseconds
     * @param volume Volume 0.0 to 1.0
     */
    @Synchronized
    fun playTone(
        frequencyHz: Double,
        durationMs: Long,
        volume: Float = currentVolume,
        envelope: ToneEnvelope = ToneEnvelope(),
        components: List<ToneComponent> = emptyList(),
        preservePhase: Boolean = false
    ) {
        if (!isInitialized) {
            AppLogger.dRateLimited(TAG, "play_before_init", 5_000L) {
                "playTone ignored; AudioTrack not initialized"
            }
            return
        }

        if (frequencyHz <= 0 || frequencyHz > MAX_FREQUENCY) {
            AppLogger.dRateLimited(TAG, "invalid_frequency", 5_000L) {
                "Invalid tone frequency: $frequencyHz Hz"
            }
            return
        }

        val track = audioTrack ?: return
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            resetAudioTrack("invalid_state_playTone")
            return
        }

        try {
            // Calculate number of samples for duration
            val numSamples = maxOf(1, ((durationMs * SAMPLE_RATE) / 1000L).toInt())
            val samples = ShortArray(numSamples)

            val baseAngularStep = 2.0 * PI * frequencyHz / SAMPLE_RATE
            val attackSamples = ((envelope.attackMs * SAMPLE_RATE) / 1000L)
                .toInt()
                .coerceAtMost(numSamples)
            val releaseSamples = ((envelope.releaseMs * SAMPLE_RATE) / 1000L)
                .toInt()
                .coerceAtMost(numSamples)
            val releaseStart = if (releaseSamples == 0) numSamples else maxOf(numSamples - releaseSamples, 0)

            var phase = if (preservePhase) phaseAccumulator else 0.0
            val componentSteps = components.map { 2.0 * PI * frequencyHz * it.ratio / SAMPLE_RATE }
            val componentPhases = DoubleArray(components.size)

            for (i in 0 until numSamples) {
                var sampleValue = sin(phase)
                components.forEachIndexed { idx, component ->
                    sampleValue += component.gain * sin(componentPhases[idx])
                    componentPhases[idx] += componentSteps[idx]
                }
                phase += baseAngularStep

                val envelopeFactor = when {
                    attackSamples > 1 && i < attackSamples -> {
                        i.toDouble() / (attackSamples - 1).toDouble()
                    }
                    attackSamples == 1 && i == 0 -> 1.0
                    releaseSamples > 1 && i >= releaseStart -> {
                        val remaining = (numSamples - 1 - i).toDouble()
                        (remaining / (releaseSamples - 1).toDouble()).coerceIn(0.0, 1.0)
                    }
                    releaseSamples == 1 && i >= releaseStart -> 0.0
                    else -> 1.0
                }

                val clamped = (sampleValue * envelopeFactor).coerceIn(-1.0, 1.0)
                samples[i] = (clamped * Short.MAX_VALUE * volume).toInt().toShort()
            }

            if (preservePhase) {
                phaseAccumulator = phase % (2.0 * PI)
            }
            lastSampleValue = samples[numSamples - 1]

            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                track.play()
            }

            // Write samples to audio track
            val written = track.write(samples, 0, numSamples)

            if (written < 0) {
                if (AppLogger.rateLimit(TAG, "write_samples_error", 2_000L)) {
                    AppLogger.e(TAG, "Error writing tone samples: $written")
                }
                resetAudioTrack("write_error_playTone", written)
            }

        } catch (e: Exception) {
            if (AppLogger.rateLimit(TAG, "play_tone_exception", 2_000L)) {
                AppLogger.e(TAG, "Error playing tone at ${frequencyHz}Hz", e)
            }
            resetAudioTrack("exception_playTone")
        }
    }

    /**
     * Play silence for specified duration
     * Used for beep pattern off-time
     */
    @Synchronized
    fun playSilence(durationMs: Long) {
        if (!isInitialized) return

        val track = audioTrack ?: return
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            resetAudioTrack("invalid_state_playSilence")
            return
        }

        try {
            val totalSamples = ((durationMs * SAMPLE_RATE) / 1000L).toInt()
            if (totalSamples <= 0) {
                return
            }

            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                track.play()
            }

            var remaining = totalSamples
            if (remaining > 0 && lastSampleValue != 0.toShort()) {
                val rampSamples = ((SILENCE_RAMP_MS * SAMPLE_RATE) / 1000L).toInt().coerceAtLeast(1)
                val rampCount = min(remaining, rampSamples)
                val ramp = ShortArray(rampCount)
                if (rampCount == 1) {
                    ramp[0] = 0
                } else {
                    val start = lastSampleValue.toDouble()
                    val denom = (rampCount - 1).toDouble()
                    for (i in 0 until rampCount) {
                        val factor = 1.0 - (i.toDouble() / denom)
                        ramp[i] = (start * factor).toInt().toShort()
                    }
                }
                val written = track.write(ramp, 0, rampCount)
                if (written < 0) {
                    if (AppLogger.rateLimit(TAG, "silence_ramp_error", 2_000L)) {
                        AppLogger.e(TAG, "Error writing silence ramp: $written")
                    }
                    resetAudioTrack("write_error_playSilence", written)
                    return
                }
                remaining -= rampCount
                lastSampleValue = 0
            }
            while (remaining > 0) {
                val chunk = min(remaining, silenceBuffer.size)
                val written = track.write(silenceBuffer, 0, chunk)
                if (written < 0) {
                    if (AppLogger.rateLimit(TAG, "silence_samples_error", 2_000L)) {
                        AppLogger.e(TAG, "Error writing silence samples: $written")
                    }
                    resetAudioTrack("write_error_playSilence", written)
                    break
                }
                if (written == 0) {
                    break
                }
                remaining -= written
            }
        } catch (e: Exception) {
            if (AppLogger.rateLimit(TAG, "play_silence_exception", 2_000L)) {
                AppLogger.e(TAG, "Error playing silence", e)
            }
            resetAudioTrack("exception_playSilence")
        }
    }

    /**
     * Set volume
     * @param volume 0.0 (mute) to 1.0 (full)
     */
    @Synchronized
    fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, 1f)
        audioTrack?.setVolume(currentVolume)
    }

    @Synchronized
    fun getVolume(): Float = currentVolume

    /**
     * Stop playback and flush buffer
     */
    @Synchronized
    fun stop() {
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error stopping audio", e)
        }
    }

    /**
     * Resume playback
     */
    @Synchronized
    fun resume() {
        if (!isInitialized) return

        try {
            audioTrack?.play()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error resuming audio", e)
        }
    }

    /**
     * Release AudioTrack resources
     * Must be called when done
     */
    @Synchronized
    fun release() {
        try {
            resetAudioTrack("release")
            AppLogger.d(TAG, "AudioTrack released")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error releasing AudioTrack", e)
        }
    }

    /**
     * Check if audio system is ready
     */
    @Synchronized
    fun isReady(): Boolean {
        val track = audioTrack ?: return false
        return isInitialized && track.state == AudioTrack.STATE_INITIALIZED
    }

    /**
     * Get current playback state
     */
    @Synchronized
    fun getPlaybackState(): Int {
        return audioTrack?.playState ?: AudioTrack.PLAYSTATE_STOPPED
    }

    @Synchronized
    fun resetPhase() {
        phaseAccumulator = 0.0
    }

    @Synchronized
    private fun resetAudioTrack(reason: String, errorCode: Int? = null) {
        val suffix = errorCode?.let { " code=$it" } ?: ""
        if (AppLogger.rateLimit(TAG, "reset_audio_track:$reason", 2_000L)) {
            AppLogger.w(TAG, "Resetting AudioTrack ($reason)$suffix")
        }
        try {
            audioTrack?.stop()
        } catch (_: Exception) {
        }
        try {
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null
        isInitialized = false
    }
}
