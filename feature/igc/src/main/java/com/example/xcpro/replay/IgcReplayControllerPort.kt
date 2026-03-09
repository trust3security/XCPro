package com.example.xcpro.replay

import com.example.xcpro.common.documents.DocumentRef
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface IgcReplayControllerPort {
    val session: StateFlow<SessionState>
    val events: SharedFlow<ReplayEvent>

    suspend fun loadDocument(document: DocumentRef)
    fun setSpeed(multiplier: Double)
    fun play()
    fun stop(emitCancelledEvent: Boolean)
    fun seekTo(fraction: Float)
}
