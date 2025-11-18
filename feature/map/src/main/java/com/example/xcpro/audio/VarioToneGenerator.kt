package com.example.xcpro.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
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
    }

    // Audio track for low-latency playback
    private var audioTrack: AudioTrack? = null
    private var isInitialized = false
    private var currentVolume = 0.8f

    private val silenceBuffer = ShortArray(SAMPLE_RATE)

    /**
     * Initialize the AudioTrack
     * Must be called before playing tones
     */
    fun initialize(): Boolean {
        if (isInitialized) {
            Log.w(TAG, "Already initialized")
            return true
        }

        try {
            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (bufferSize == AudioTrack.ERROR_BAD_VALUE || bufferSize == AudioTrack.ERROR) {
                Log.e(TAG, "Invalid buffer size: $bufferSize")
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

            audioTrack?.play()
            isInitialized = true

            Log.i(TAG, "AudioTrack initialized (buffer: $optimalBufferSize bytes, sample rate: $SAMPLE_RATE Hz)")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack", e)
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
    fun playTone(
        frequencyHz: Double,
        durationMs: Long,
        volume: Float = currentVolume,
        envelope: ToneEnvelope = ToneEnvelope(),
        components: List<ToneComponent> = emptyList()
    ) {
        if (!isInitialized) {
            Log.w(TAG, "Not initialized, call initialize() first")
            return
        }

        if (frequencyHz <= 0 || frequencyHz > MAX_FREQUENCY) {
            Log.w(TAG, "Invalid frequency: $frequencyHz Hz")
            return
        }

        val track = audioTrack ?: return

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
                .coerceAtMost(numSamples - 1)
            val releaseStart = if (releaseSamples == 0) numSamples else maxOf(numSamples - releaseSamples, 0)

            var phase = 0.0
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
                    attackSamples > 0 && i < attackSamples -> (i + 1).toDouble() / attackSamples
                    releaseSamples > 0 && i >= releaseStart -> {
                        val remaining = (numSamples - i).toDouble()
                        (remaining / releaseSamples).coerceIn(0.0, 1.0)
                    }
                    else -> 1.0
                }

                val clamped = (sampleValue * envelopeFactor).coerceIn(-1.0, 1.0)
                samples[i] = (clamped * Short.MAX_VALUE * volume).toInt().toShort()
            }

            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                track.play()
            }

            // Write samples to audio track
            val written = track.write(samples, 0, numSamples)

            if (written < 0) {
                Log.e(TAG, "Error writing samples: $written")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error playing tone at ${frequencyHz}Hz", e)
        }
    }

    /**
     * Play silence for specified duration
     * Used for beep pattern off-time
     */
    fun playSilence(durationMs: Long) {
        if (!isInitialized) return

        val track = audioTrack ?: return

        try {
            val totalSamples = ((durationMs * SAMPLE_RATE) / 1000L).toInt()
            if (totalSamples <= 0) {
                return
            }

            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                track.play()
            }

            var remaining = totalSamples
            while (remaining > 0) {
                val chunk = min(remaining, silenceBuffer.size)
                val written = track.write(silenceBuffer, 0, chunk)
                if (written < 0) {
                    Log.e(TAG, "Error writing silence samples: $written")
                    break
                }
                if (written == 0) {
                    break
                }
                remaining -= written
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing silence", e)
        }
    }

    /**
     * Set volume
     * @param volume 0.0 (mute) to 1.0 (full)
     */
    fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, 1f)
        audioTrack?.setVolume(currentVolume)
    }

    fun getVolume(): Float = currentVolume

    /**
     * Stop playback and flush buffer
     */
    fun stop() {
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio", e)
        }
    }

    /**
     * Resume playback
     */
    fun resume() {
        if (!isInitialized) return

        try {
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming audio", e)
        }
    }

    /**
     * Release AudioTrack resources
     * Must be called when done
     */
    fun release() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
            isInitialized = false
            Log.i(TAG, "AudioTrack released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack", e)
        }
    }

    /**
     * Check if audio system is ready
     */
    fun isReady(): Boolean = isInitialized && audioTrack != null

    /**
     * Get current playback state
     */
    fun getPlaybackState(): Int {
        return audioTrack?.playState ?: AudioTrack.PLAYSTATE_STOPPED
    }
}
