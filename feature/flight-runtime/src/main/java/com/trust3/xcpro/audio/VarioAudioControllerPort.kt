package com.trust3.xcpro.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Shared runtime port for flight-domain audio control.
 *
 * Implementations own Android/audio side effects outside `feature:flight-runtime`.
 */
interface VarioAudioControllerPort {
    val settings: StateFlow<VarioAudioSettings>

    fun update(teSample: Double?, rawVario: Double, currentTime: Long, validUntil: Long): Double?

    fun updateSettings(settings: VarioAudioSettings)

    fun silence()

    fun stop()
}

interface VarioAudioControllerFactory {
    fun create(scope: CoroutineScope, enableAudio: Boolean): VarioAudioControllerPort
}
