package com.trust3.xcpro.audio

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Singleton
class DefaultVarioAudioControllerFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioFocusManager: AudioFocusManager
) : VarioAudioControllerFactory {
    override fun create(scope: CoroutineScope, enableAudio: Boolean): VarioAudioControllerPort {
        val controller = VarioAudioController(
            context = context,
            audioFocusManager = audioFocusManager,
            scope = scope,
            enableAudio = enableAudio
        )
        return DefaultVarioAudioControllerPort(controller)
    }
}

private class DefaultVarioAudioControllerPort(
    private val controller: VarioAudioController
) : VarioAudioControllerPort {
    override val settings = controller.engine.settings

    override fun update(
        teSample: Double?,
        rawVario: Double,
        currentTime: Long,
        validUntil: Long
    ): Double? = controller.update(
        teSample = teSample,
        rawVario = rawVario,
        currentTime = currentTime,
        validUntil = validUntil
    )

    override fun updateSettings(settings: VarioAudioSettings) {
        controller.engine.updateSettings(settings)
    }

    override fun silence() {
        controller.engine.setSilence()
    }

    override fun stop() {
        controller.stop()
    }
}
