package com.trust3.xcpro.adsb

import android.media.AudioManager
import android.media.ToneGenerator
import com.trust3.xcpro.audio.AudioFocusManager
import com.trust3.xcpro.common.di.IoDispatcher
import com.trust3.xcpro.core.common.logging.AppLogger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class AndroidAdsbEmergencyAudioOutputAdapter @Inject constructor(
    private val audioFocusManager: AudioFocusManager,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : AdsbEmergencyAudioOutputPort {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val playbackMutex = Mutex()

    override fun playEmergencyAlert(triggerMonoMs: Long, emergencyTargetId: String?) {
        scope.launch {
            playbackMutex.withLock {
                playAlertLocked(triggerMonoMs, emergencyTargetId)
            }
        }
    }

    private suspend fun playAlertLocked(triggerMonoMs: Long, emergencyTargetId: String?) {
        if (!audioFocusManager.requestFocus()) {
            AppLogger.w(
                TAG,
                "Skipping ADS-B emergency alert (focus denied, target=$emergencyTargetId, monoMs=$triggerMonoMs)"
            )
            return
        }

        val toneGenerator = runCatching {
            ToneGenerator(AudioManager.STREAM_ALARM, ALERT_VOLUME_PERCENT)
        }.getOrElse { throwable ->
            AppLogger.w(
                TAG,
                "Failed to create tone generator (${throwable::class.java.simpleName})"
            )
            audioFocusManager.abandonFocus()
            return
        }

        try {
            val started = runCatching {
                toneGenerator.startTone(ALERT_TONE, ALERT_DURATION_MS)
            }.getOrElse { throwable ->
                AppLogger.w(
                    TAG,
                    "Failed to start emergency tone (${throwable::class.java.simpleName})"
                )
                false
            }
            if (!started) {
                AppLogger.w(
                    TAG,
                    "Emergency tone start returned false (target=$emergencyTargetId, monoMs=$triggerMonoMs)"
                )
            }
            delay((ALERT_DURATION_MS + FOCUS_RELEASE_BUFFER_MS).toLong())
        } finally {
            runCatching { toneGenerator.release() }
            audioFocusManager.abandonFocus()
        }
    }

    private companion object {
        private const val TAG = "AdsbEmergencyAudio"
        private const val ALERT_DURATION_MS = 700
        private const val FOCUS_RELEASE_BUFFER_MS = 100
        private const val ALERT_VOLUME_PERCENT = 100
        private const val ALERT_TONE = ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
    }
}

