package com.trust3.xcpro.igc.usecase

import com.trust3.xcpro.igc.data.IgcFlightLogRepository
import com.trust3.xcpro.igc.domain.IgcRecoveryDiagnosticsReporter
import com.trust3.xcpro.igc.domain.IgcRecoveryErrorCode
import com.trust3.xcpro.igc.domain.IgcRecoveryResult
import com.trust3.xcpro.igc.domain.IgcSessionStateMachine
import javax.inject.Inject

class IgcRecoveryBootstrapUseCase @Inject constructor(
    private val flightLogRepository: IgcFlightLogRepository,
    private val diagnosticsReporter: IgcRecoveryDiagnosticsReporter
) {

    fun bootstrap(
        snapshot: IgcSessionStateMachine.Snapshot
    ): BootstrapResult {
        val phaseName = snapshot.state.phase.name
        val sessionId = snapshot.state.activeSessionId
        if (sessionId == null) {
            reportEvent(
                event = EVENT_UNSUPPORTED,
                sessionId = null,
                phase = phaseName,
                attributes = mapOf("reason" to "snapshot_has_no_active_session")
            )
            return BootstrapResult.Unsupported("Snapshot has no active session")
        }
        return when (snapshot.state.phase) {
            IgcSessionStateMachine.Phase.Recording -> {
                reportEvent(
                    event = EVENT_RESUME_EXISTING,
                    sessionId = sessionId,
                    phase = phaseName
                )
                BootstrapResult.ResumeExisting(
                    reason = "Recording snapshot resumes existing session"
                )
            }
            IgcSessionStateMachine.Phase.Finalizing -> recoverFinalizing(sessionId)
            else -> {
                reportEvent(
                    event = EVENT_UNSUPPORTED,
                    sessionId = sessionId,
                    phase = phaseName,
                    attributes = mapOf("reason" to "phase_not_eligible")
                )
                BootstrapResult.Unsupported(
                    reason = "Phase ${snapshot.state.phase} is not eligible for startup recovery"
                )
            }
        }
    }

    private fun recoverFinalizing(sessionId: Long): BootstrapResult {
        val phaseName = IgcSessionStateMachine.Phase.Finalizing.name
        val recoveryResult = runCatching {
            flightLogRepository.recoverSession(sessionId = sessionId)
        }.getOrElse {
            reportEvent(
                event = EVENT_EXCEPTION,
                sessionId = sessionId,
                phase = phaseName,
                attributes = mapOf(
                    "exception" to it::class.java.simpleName,
                    "message" to normalizeAttributeValue(it.message ?: "IGC startup recovery failed")
                )
            )
            reportEvent(
                event = EVENT_TERMINAL_FAILURE,
                sessionId = sessionId,
                phase = phaseName,
                attributes = mapOf(
                    "code" to IgcRecoveryErrorCode.PENDING_ROW_WRITE_FAILED.name,
                    "message" to normalizeAttributeValue(it.message ?: "IGC startup recovery failed"),
                    "source" to "exception"
                )
            )
            return BootstrapResult.TerminalFailure(
                code = IgcRecoveryErrorCode.PENDING_ROW_WRITE_FAILED,
                message = it.message ?: "IGC startup recovery failed"
            )
        }
        return when (recoveryResult) {
            is IgcRecoveryResult.Recovered -> {
                reportEvent(
                    event = EVENT_RECOVERED,
                    sessionId = sessionId,
                    phase = phaseName,
                    attributes = mapOf("entry_name" to recoveryResult.entryName)
                )
                BootstrapResult.Recovered(recoveryResult.entryName)
            }
            is IgcRecoveryResult.Failure -> {
                reportEvent(
                    event = EVENT_TERMINAL_FAILURE,
                    sessionId = sessionId,
                    phase = phaseName,
                    attributes = mapOf(
                        "code" to recoveryResult.code.name,
                        "message" to normalizeAttributeValue(recoveryResult.message),
                        "source" to "repository"
                    )
                )
                BootstrapResult.TerminalFailure(
                    code = recoveryResult.code,
                    message = recoveryResult.message
                )
            }
            is IgcRecoveryResult.NoRecoveryWork -> {
                reportEvent(
                    event = EVENT_UNSUPPORTED,
                    sessionId = sessionId,
                    phase = phaseName,
                    attributes = mapOf(
                        "reason" to normalizeAttributeValue(recoveryResult.reason),
                        "source" to "repository"
                    )
                )
                BootstrapResult.Unsupported(recoveryResult.reason)
            }
        }
    }

    private fun reportEvent(
        event: String,
        sessionId: Long?,
        phase: String,
        attributes: Map<String, String> = emptyMap()
    ) {
        val payload = linkedMapOf("phase" to phase)
        if (sessionId != null) {
            payload["session_id"] = sessionId.toString()
        }
        payload.putAll(attributes)
        diagnosticsReporter.report(event = event, attributes = payload)
    }

    private fun normalizeAttributeValue(value: String): String = value.trim().take(MAX_ATTRIBUTE_LENGTH)

    sealed interface BootstrapResult {
        data class Recovered(val entryName: String) : BootstrapResult

        data class TerminalFailure(
            val code: IgcRecoveryErrorCode,
            val message: String
        ) : BootstrapResult

        data class ResumeExisting(val reason: String) : BootstrapResult

        data class Unsupported(val reason: String) : BootstrapResult
    }

    private companion object {
        private const val EVENT_RESUME_EXISTING = "igc_recovery_resume_existing"
        private const val EVENT_RECOVERED = "igc_recovery_recovered"
        private const val EVENT_TERMINAL_FAILURE = "igc_recovery_terminal_failure"
        private const val EVENT_UNSUPPORTED = "igc_recovery_unsupported"
        private const val EVENT_EXCEPTION = "igc_recovery_exception"
        private const val MAX_ATTRIBUTE_LENGTH = 160
    }
}
