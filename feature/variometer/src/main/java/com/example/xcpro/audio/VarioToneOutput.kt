package com.example.xcpro.audio

interface VarioToneOutput {
    fun isReady(): Boolean

    fun playTone(
        frequencyHz: Double,
        durationMs: Long,
        volume: Float = 0.8f,
        envelope: ToneEnvelope = ToneEnvelope(),
        components: List<ToneComponent> = emptyList(),
        preservePhase: Boolean = false
    )

    fun playSilence(durationMs: Long)

    fun setVolume(volume: Float)

    fun stop()

    fun resetPhase()
}
