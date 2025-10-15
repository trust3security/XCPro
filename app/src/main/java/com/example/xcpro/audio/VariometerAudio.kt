package com.example.xcpro.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*
import kotlin.math.*

/**
 * Safe, professional-grade variometer audio system
 * Prevents speaker damage while providing clear audio feedback
 */
class VariometerAudioManager(private val context: Context) {
    
    private var audioTrack: AudioTrack? = null
    private var audioJob: Job? = null
    private var isPlaying = false
    
    // Audio configuration - optimized for clarity and safety
    private val sampleRate = 22050  // Lower sample rate for better performance
    private val bufferSizeBytes = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO, 
        AudioFormat.ENCODING_PCM_16BIT
    ) * 4 // 4x min buffer for smooth playback
    
    // Safety limits to prevent speaker damage
    private val maxSafeAmplitude = 0.15f  // Maximum 15% of full scale
    private val minAudibleAmplitude = 0.03f // Minimum audible level
    
    // Audio generation parameters
    private var currentFrequency = 0.0
    private var currentAmplitude = 0.0f
    private var targetFrequency = 0.0
    private var targetAmplitude = 0.0f
    
    // Smooth transitions to prevent crackling
    private val frequencyTransitionSpeed = 50.0 // Hz per update
    private val amplitudeTransitionSpeed = 0.1f // Amplitude per update
    
    init {
        initializeAudioTrack()
    }
    
    private fun initializeAudioTrack() {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSizeBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
                
            // Verify audio track creation was successful
            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                android.util.Log.e("VariometerAudio", "❌ AudioTrack initialization failed")
                audioTrack = null
            } else {
                android.util.Log.d("VariometerAudio", "✅ AudioTrack initialized successfully")
            }
                
        } catch (e: Exception) {
            android.util.Log.e("VariometerAudio", "❌ Error initializing AudioTrack: ${e.message}")
            audioTrack = null
        }
    }
    
    /**
     * Start variometer audio based on vertical speed
     * @param verticalSpeed in m/s (positive = climb, negative = sink)
     * @param enabled whether audio should play
     */
    fun updateAudio(verticalSpeed: Float, enabled: Boolean) {
        if (!enabled || audioTrack == null) {
            stopAudio()
            return
        }
        
        if (shouldPlayAudio(verticalSpeed)) {
            val frequency = mapVerticalSpeedToFrequency(verticalSpeed)
            val amplitude = mapVerticalSpeedToAmplitude(verticalSpeed)
            startAudioWithParams(frequency, amplitude)
        } else {
            stopAudio()
        }
    }
    
    private fun shouldPlayAudio(verticalSpeed: Float): Boolean {
        // Only play audio for significant vertical movement
        return abs(verticalSpeed) > 0.3f // 0.3 m/s threshold (about 60 fpm)
    }
    
    private fun mapVerticalSpeedToFrequency(verticalSpeed: Float): Double {
        return when {
            // Strong sink: 200-350 Hz (low, urgent)
            verticalSpeed <= -4f -> 200.0 + (verticalSpeed + 8f) * 18.75f
            
            // Moderate sink: 350-450 Hz 
            verticalSpeed <= -1f -> 350.0 + (verticalSpeed + 4f) * 33.33f
            
            // Weak sink/zero: 450-500 Hz
            verticalSpeed <= 0f -> 450.0 + verticalSpeed * 50f
            
            // Weak climb: 500-600 Hz  
            verticalSpeed <= 2f -> 500.0 + verticalSpeed * 50f
            
            // Good climb: 600-750 Hz
            verticalSpeed <= 5f -> 600.0 + (verticalSpeed - 2f) * 50f
            
            // Excellent climb: 750-900 Hz (capped for comfort)
            else -> 750.0 + (verticalSpeed - 5f) * 30f
        }.coerceIn(180.0, 900.0) // Safe frequency range
    }
    
    private fun mapVerticalSpeedToAmplitude(verticalSpeed: Float): Float {
        val baseAmplitude = minAudibleAmplitude
        val intensityMultiplier = (abs(verticalSpeed) * 0.02f).coerceAtMost(0.1f)
        return (baseAmplitude + intensityMultiplier).coerceIn(minAudibleAmplitude, maxSafeAmplitude)
    }
    
    private fun startAudioWithParams(frequency: Double, amplitude: Float) {
        targetFrequency = frequency
        targetAmplitude = amplitude
        
        if (!isPlaying) {
            isPlaying = true
            audioTrack?.play()
            startAudioGeneration()
        }
    }
    
    private fun startAudioGeneration() {
        audioJob?.cancel()
        audioJob = CoroutineScope(Dispatchers.Default).launch {
            
            val chunkDurationMs = 50L // 50ms chunks for smooth audio
            val samplesPerChunk = (sampleRate * chunkDurationMs / 1000).toInt()
            
            while (isPlaying && audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                try {
                    // Smooth parameter transitions to prevent clicking
                    updateTransitionParameters()
                    
                    // Generate audio chunk with anti-aliasing
                    val audioBuffer = generateSmoothAudioChunk(samplesPerChunk)
                    
                    // Write to audio track
                    val written = audioTrack?.write(audioBuffer, 0, audioBuffer.size) ?: 0
                    
                    if (written < 0) {
                        android.util.Log.e("VariometerAudio", "❌ Audio write error: $written")
                        break
                    }
                    
                    // Small delay to prevent overwhelming the audio system
                    delay(chunkDurationMs / 2)
                    
                } catch (e: Exception) {
                    android.util.Log.e("VariometerAudio", "❌ Audio generation error: ${e.message}")
                    break
                }
            }
        }
    }
    
    private fun updateTransitionParameters() {
        // Smooth frequency transitions
        val freqDiff = targetFrequency - currentFrequency
        currentFrequency += when {
            abs(freqDiff) < frequencyTransitionSpeed -> freqDiff
            freqDiff > 0 -> frequencyTransitionSpeed
            else -> -frequencyTransitionSpeed
        }
        
        // Smooth amplitude transitions  
        val ampDiff = targetAmplitude - currentAmplitude
        currentAmplitude += when {
            abs(ampDiff) < amplitudeTransitionSpeed -> ampDiff
            ampDiff > 0 -> amplitudeTransitionSpeed
            else -> -amplitudeTransitionSpeed
        }
    }
    
    private fun generateSmoothAudioChunk(samples: Int): ShortArray {
        val buffer = ShortArray(samples)
        val maxValue = Short.MAX_VALUE
        
        // Envelope parameters for click-free audio
        val envelopeRampSamples = (samples * 0.05).toInt() // 5% ramp time
        
        for (i in 0 until samples) {
            // Calculate sine wave sample
            val time = i.toDouble() / sampleRate
            val rawSample = sin(2 * PI * currentFrequency * time)
            
            // Apply envelope to prevent clicking at chunk boundaries
            val envelope = when {
                i < envelopeRampSamples -> i.toFloat() / envelopeRampSamples
                i >= samples - envelopeRampSamples -> (samples - i).toFloat() / envelopeRampSamples
                else -> 1.0f
            }
            
            // Apply safe amplitude limiting
            val finalSample = (rawSample * currentAmplitude * envelope * maxValue).toInt()
            buffer[i] = finalSample.coerceIn(-maxValue.toInt(), maxValue.toInt()).toShort()
        }
        
        return buffer
    }
    
    fun stopAudio() {
        if (isPlaying) {
            isPlaying = false
            audioJob?.cancel()
            
            try {
                audioTrack?.pause()
                audioTrack?.flush()
            } catch (e: Exception) {
                android.util.Log.e("VariometerAudio", "❌ Error stopping audio: ${e.message}")
            }
        }
    }
    
    fun cleanup() {
        stopAudio()
        try {
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            android.util.Log.e("VariometerAudio", "❌ Error cleaning up AudioTrack: ${e.message}")
        }
    }
    
    /**
     * Check system volume and warn if too high
     */
    fun checkSafeVolumeLevel(): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volumePercent = (currentVolume.toFloat() / maxVolume * 100).toInt()
        
        return if (volumePercent > 70) {
            android.util.Log.w("VariometerAudio", "⚠️ System volume is ${volumePercent}% - consider lowering for safety")
            false
        } else {
            true
        }
    }
}

/**
 * Composable wrapper for easy integration
 */
@Composable
fun VariometerAudioProvider(
    verticalSpeed: Float,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val audioManager = remember { VariometerAudioManager(context) }
    
    // Update audio based on vertical speed changes
    LaunchedEffect(verticalSpeed, enabled) {
        audioManager.updateAudio(verticalSpeed, enabled)
    }
    
    // Cleanup when composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            audioManager.cleanup()
        }
    }
    
    content()
}

/**
 * Audio configuration presets for different flying conditions
 */
object VariometerAudioPresets {
    
    data class AudioSettings(
        val sensitivityThreshold: Float,
        val frequencyRange: Pair<Double, Double>,
        val volumeMultiplier: Float,
        val description: String
    )
    
    val THERMAL_FLYING = AudioSettings(
        sensitivityThreshold = 0.2f, // Very sensitive for thermal detection
        frequencyRange = 300.0 to 800.0,
        volumeMultiplier = 1.0f,
        description = "Optimized for thermal flying"
    )
    
    val CROSS_COUNTRY = AudioSettings(  
        sensitivityThreshold = 0.5f, // Less sensitive for cruise flight
        frequencyRange = 250.0 to 700.0,
        volumeMultiplier = 0.8f,
        description = "Optimized for cross-country flight"
    )
    
    val TRAINING = AudioSettings(
        sensitivityThreshold = 0.4f, // Moderate sensitivity
        frequencyRange = 200.0 to 600.0,
        volumeMultiplier = 0.6f,
        description = "Gentler tones for training flights"
    )
}