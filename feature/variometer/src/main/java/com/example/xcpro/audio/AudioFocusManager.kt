package com.example.xcpro.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import com.example.xcpro.core.common.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioFocusManager @Inject constructor(
    @ApplicationContext context: Context
) {

    companion object {
        private const val TAG = "AudioFocusManager"
        private val AUDIO_ATTRIBUTES: AudioAttributes =
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile
    private var hasFocus = false

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> hasFocus = true
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> hasFocus = false
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Keep focus; we do not duck vario audio.
            }
        }
    }

    private val focusRequest: AudioFocusRequest? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(AUDIO_ATTRIBUTES)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
        } else {
            null
        }

    fun requestFocus(): Boolean {
        if (hasFocus) return true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!hasFocus) {
            if (AppLogger.rateLimit(TAG, "focus_not_granted", 5_000L)) {
                AppLogger.w(TAG, "Audio focus not granted (result=$result)")
            }
        }
        return hasFocus
    }

    fun abandonFocus() {
        if (!hasFocus && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
            return
        }
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
        hasFocus = false
        AppLogger.d(TAG, "Audio focus abandoned (result=$result)")
    }

    fun hasFocus(): Boolean = hasFocus
}
