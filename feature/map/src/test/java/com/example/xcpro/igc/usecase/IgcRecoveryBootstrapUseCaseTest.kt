package com.example.xcpro.igc.usecase

import com.example.xcpro.igc.data.IgcFinalizeRequest
import com.example.xcpro.igc.data.IgcFinalizeResult
import com.example.xcpro.igc.data.IgcFlightLogRepository
import com.example.xcpro.igc.domain.IgcRecoveryDiagnosticsReporter
import com.example.xcpro.igc.domain.IgcRecoveryErrorCode
import com.example.xcpro.igc.domain.IgcRecoveryResult
import com.example.xcpro.igc.domain.IgcSessionStateMachine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IgcRecoveryBootstrapUseCaseTest {

    @Test
    fun recordingSnapshot_returnsResumeExisting_withoutCallingRepository() {
        val repository = FakeFlightLogRepository(
            recoveryResult = IgcRecoveryResult.Recovered("2025-03-09-XCP-000021-01.IGC")
        )
        val diagnostics = RecordingDiagnosticsReporter()
        val useCase = IgcRecoveryBootstrapUseCase(repository, diagnostics)

        val result = useCase.bootstrap(recordingSnapshot(sessionId = 21L, nextSessionId = 22L))

        assertTrue(result is IgcRecoveryBootstrapUseCase.BootstrapResult.ResumeExisting)
        assertNull(repository.lastRecoveredSessionId)
        assertEquals(
            listOf(
                DiagnosticEvent(
                    event = "igc_recovery_resume_existing",
                    attributes = mapOf("phase" to "Recording", "session_id" to "21")
                )
            ),
            diagnostics.events
        )
    }

    @Test
    fun finalizingSnapshot_withRecoveredEntry_returnsRecovered() {
        val repository = FakeFlightLogRepository(
            recoveryResult = IgcRecoveryResult.Recovered("2025-03-09-XCP-000031-01.IGC")
        )
        val diagnostics = RecordingDiagnosticsReporter()
        val useCase = IgcRecoveryBootstrapUseCase(repository, diagnostics)

        val result = useCase.bootstrap(finalizingSnapshot(sessionId = 31L, nextSessionId = 32L))

        assertEquals(31L, repository.lastRecoveredSessionId)
        assertEquals(
            IgcRecoveryBootstrapUseCase.BootstrapResult.Recovered(
                entryName = "2025-03-09-XCP-000031-01.IGC"
            ),
            result
        )
        assertEquals(
            listOf(
                DiagnosticEvent(
                    event = "igc_recovery_recovered",
                    attributes = mapOf(
                        "entry_name" to "2025-03-09-XCP-000031-01.IGC",
                        "phase" to "Finalizing",
                        "session_id" to "31"
                    )
                )
            ),
            diagnostics.events
        )
    }

    @Test
    fun finalizingSnapshot_withFailure_returnsTerminalFailure() {
        val repository = FakeFlightLogRepository(
            recoveryResult = IgcRecoveryResult.Failure(
                code = IgcRecoveryErrorCode.STAGING_CORRUPT,
                message = "staging corrupt"
            )
        )
        val diagnostics = RecordingDiagnosticsReporter()
        val useCase = IgcRecoveryBootstrapUseCase(repository, diagnostics)

        val result = useCase.bootstrap(finalizingSnapshot(sessionId = 41L, nextSessionId = 42L))

        assertEquals(41L, repository.lastRecoveredSessionId)
        assertEquals(
            IgcRecoveryBootstrapUseCase.BootstrapResult.TerminalFailure(
                code = IgcRecoveryErrorCode.STAGING_CORRUPT,
                message = "staging corrupt"
            ),
            result
        )
        assertEquals(
            listOf(
                DiagnosticEvent(
                    event = "igc_recovery_terminal_failure",
                    attributes = mapOf(
                        "code" to "STAGING_CORRUPT",
                        "message" to "staging corrupt",
                        "phase" to "Finalizing",
                        "session_id" to "41",
                        "source" to "repository"
                    )
                )
            ),
            diagnostics.events
        )
    }

    @Test
    fun k1_finalizingSnapshotWithoutStagingMaterial_returnsTerminalFailure() {
        val repository = FakeFlightLogRepository(
            recoveryResult = IgcRecoveryResult.Failure(
                code = IgcRecoveryErrorCode.STAGING_MISSING,
                message = "staging missing"
            )
        )
        val diagnostics = RecordingDiagnosticsReporter()
        val useCase = IgcRecoveryBootstrapUseCase(repository, diagnostics)

        val result = useCase.bootstrap(finalizingSnapshot(sessionId = 45L, nextSessionId = 46L))

        assertEquals(45L, repository.lastRecoveredSessionId)
        assertEquals(
            IgcRecoveryBootstrapUseCase.BootstrapResult.TerminalFailure(
                code = IgcRecoveryErrorCode.STAGING_MISSING,
                message = "staging missing"
            ),
            result
        )
        assertEquals(
            listOf(
                DiagnosticEvent(
                    event = "igc_recovery_terminal_failure",
                    attributes = mapOf(
                        "code" to "STAGING_MISSING",
                        "message" to "staging missing",
                        "phase" to "Finalizing",
                        "session_id" to "45",
                        "source" to "repository"
                    )
                )
            ),
            diagnostics.events
        )
    }

    @Test
    fun finalizingSnapshot_withUnsupportedRepository_returnsUnsupported() {
        val repository = FakeFlightLogRepository(
            recoveryResult = IgcRecoveryResult.NoRecoveryWork("repository unsupported")
        )
        val diagnostics = RecordingDiagnosticsReporter()
        val useCase = IgcRecoveryBootstrapUseCase(repository, diagnostics)

        val result = useCase.bootstrap(finalizingSnapshot(sessionId = 51L, nextSessionId = 52L))

        assertEquals(51L, repository.lastRecoveredSessionId)
        assertEquals(
            IgcRecoveryBootstrapUseCase.BootstrapResult.Unsupported(
                reason = "repository unsupported"
            ),
            result
        )
        assertEquals(
            listOf(
                DiagnosticEvent(
                    event = "igc_recovery_unsupported",
                    attributes = mapOf(
                        "phase" to "Finalizing",
                        "reason" to "repository unsupported",
                        "session_id" to "51",
                        "source" to "repository"
                    )
                )
            ),
            diagnostics.events
        )
    }

    @Test
    fun finalizingSnapshot_withException_reportsExceptionAndTerminalFailure() {
        val repository = FakeFlightLogRepository(recoveryException = IllegalStateException("boom"))
        val diagnostics = RecordingDiagnosticsReporter()
        val useCase = IgcRecoveryBootstrapUseCase(repository, diagnostics)

        val result = useCase.bootstrap(finalizingSnapshot(sessionId = 61L, nextSessionId = 62L))

        assertEquals(61L, repository.lastRecoveredSessionId)
        assertEquals(
            IgcRecoveryBootstrapUseCase.BootstrapResult.TerminalFailure(
                code = IgcRecoveryErrorCode.PENDING_ROW_WRITE_FAILED,
                message = "boom"
            ),
            result
        )
        assertEquals(
            listOf(
                DiagnosticEvent(
                    event = "igc_recovery_exception",
                    attributes = mapOf(
                        "exception" to "IllegalStateException",
                        "message" to "boom",
                        "phase" to "Finalizing",
                        "session_id" to "61"
                    )
                ),
                DiagnosticEvent(
                    event = "igc_recovery_terminal_failure",
                    attributes = mapOf(
                        "code" to "PENDING_ROW_WRITE_FAILED",
                        "message" to "boom",
                        "phase" to "Finalizing",
                        "session_id" to "61",
                        "source" to "exception"
                    )
                )
            ),
            diagnostics.events
        )
    }

    private fun recordingSnapshot(sessionId: Long, nextSessionId: Long): IgcSessionStateMachine.Snapshot {
        return IgcSessionStateMachine.Snapshot(
            state = IgcSessionStateMachine.State(
                phase = IgcSessionStateMachine.Phase.Recording,
                activeSessionId = sessionId
            ),
            nextSessionId = nextSessionId,
            lastMonoTimeMs = null,
            armingCandidateSinceMs = null,
            takeoffCandidateSinceMs = null,
            landingCandidateSinceMs = null,
            finalizingSinceMs = null,
            preFlightGroundFixMonoTimes = emptyList(),
            postFlightGroundFixMonoTimes = emptyList()
        )
    }

    private fun finalizingSnapshot(sessionId: Long, nextSessionId: Long): IgcSessionStateMachine.Snapshot {
        return IgcSessionStateMachine.Snapshot(
            state = IgcSessionStateMachine.State(
                phase = IgcSessionStateMachine.Phase.Finalizing,
                activeSessionId = sessionId
            ),
            nextSessionId = nextSessionId,
            lastMonoTimeMs = null,
            armingCandidateSinceMs = null,
            takeoffCandidateSinceMs = null,
            landingCandidateSinceMs = null,
            finalizingSinceMs = 10L,
            preFlightGroundFixMonoTimes = emptyList(),
            postFlightGroundFixMonoTimes = emptyList()
        )
    }

    private class FakeFlightLogRepository(
        private val recoveryResult: IgcRecoveryResult = IgcRecoveryResult.NoRecoveryWork("unused"),
        private val recoveryException: Throwable? = null
    ) : IgcFlightLogRepository {
        var lastRecoveredSessionId: Long? = null

        override fun finalizeSession(request: IgcFinalizeRequest): IgcFinalizeResult {
            error("finalizeSession should not be called in this test")
        }

        override fun recoverSession(sessionId: Long): IgcRecoveryResult {
            lastRecoveredSessionId = sessionId
            recoveryException?.let { throw it }
            return recoveryResult
        }
    }

    private data class DiagnosticEvent(
        val event: String,
        val attributes: Map<String, String>
    )

    private class RecordingDiagnosticsReporter : IgcRecoveryDiagnosticsReporter {
        val events = mutableListOf<DiagnosticEvent>()

        override fun report(event: String, attributes: Map<String, String>) {
            events += DiagnosticEvent(event = event, attributes = attributes)
        }
    }
}
