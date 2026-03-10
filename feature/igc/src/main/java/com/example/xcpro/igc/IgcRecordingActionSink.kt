package com.example.xcpro.igc

import com.example.xcpro.igc.data.IgcFinalizeResult
import com.example.xcpro.igc.domain.IgcSessionStateMachine

/**
 * Runtime hook for IGC state machine actions.
 *
 * Phase 2 keeps this as an execution contract; concrete persistence/writer
 * behavior is implemented when phase 3+ owns recording side effects.
 */
interface IgcRecordingActionSink {
    fun onSessionArmed(monoTimeMs: Long)

    fun onStartRecording(sessionId: Long, preFlightGroundWindowMs: Long)

    fun onFinalizeRecording(sessionId: Long, postFlightGroundWindowMs: Long): IgcFinalizeResult

    fun onMarkCompleted(sessionId: Long)

    fun onMarkFailed(sessionId: Long, reason: String)

    fun onBRecord(sessionId: Long, line: String, sampleWallTimeMs: Long)

    fun onTaskEvent(sessionId: Long, payload: String)

    fun onSystemEvent(sessionId: Long, payload: String)
}

object NoopIgcRecordingActionSink : IgcRecordingActionSink {
    override fun onSessionArmed(monoTimeMs: Long) = Unit
    override fun onStartRecording(sessionId: Long, preFlightGroundWindowMs: Long) = Unit
    override fun onFinalizeRecording(sessionId: Long, postFlightGroundWindowMs: Long): IgcFinalizeResult {
        return IgcFinalizeResult.Failure(
            code = IgcFinalizeResult.ErrorCode.WRITE_FAILED,
            message = "IGC recording action sink not configured"
        )
    }
    override fun onMarkCompleted(sessionId: Long) = Unit
    override fun onMarkFailed(sessionId: Long, reason: String) = Unit
    override fun onBRecord(sessionId: Long, line: String, sampleWallTimeMs: Long) = Unit
    override fun onTaskEvent(sessionId: Long, payload: String) = Unit
    override fun onSystemEvent(sessionId: Long, payload: String) = Unit
}
