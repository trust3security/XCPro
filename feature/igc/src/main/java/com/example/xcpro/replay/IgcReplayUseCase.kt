package com.example.xcpro.replay

import com.example.xcpro.common.documents.DocumentRef
import javax.inject.Inject
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class IgcReplayUseCase @Inject constructor(
    private val controller: IgcReplayControllerPort
) {
    val session: StateFlow<SessionState> = controller.session
    val events: SharedFlow<ReplayEvent> = controller.events

    suspend fun loadDocument(document: DocumentRef) {
        controller.loadDocument(document)
    }

    fun setSpeed(multiplier: Double) {
        controller.setSpeed(multiplier)
    }

    fun play() {
        controller.play()
    }

    fun stop() {
        controller.stop(emitCancelledEvent = true)
    }

    fun seekTo(fraction: Float) {
        controller.seekTo(fraction)
    }
}
