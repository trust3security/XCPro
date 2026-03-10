package com.example.xcpro.igc.data

import com.example.xcpro.core.time.FakeClock
import com.example.xcpro.igc.domain.IgcGpsAltitudeDatum
import com.example.xcpro.igc.domain.IgcPressureAltitudeDatum
import com.example.xcpro.igc.domain.IgcProfileMetadata
import com.example.xcpro.igc.domain.IgcProfileMetadataSource
import com.example.xcpro.igc.domain.IgcRecorderMetadata
import com.example.xcpro.igc.domain.IgcRecoveryMetadata
import com.example.xcpro.igc.domain.IgcRecorderMetadataSource
import com.example.xcpro.igc.domain.IgcSecuritySignatureProfile
import com.example.xcpro.igc.domain.IgcTaskDeclarationSnapshot
import com.example.xcpro.igc.domain.IgcTaskDeclarationStartSnapshot
import com.example.xcpro.igc.domain.IgcTaskDeclarationSource
import com.example.xcpro.igc.domain.IgcTaskDeclarationWaypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IgcRecordingRuntimeActionSinkTest {

    @Test
    fun onStartRecording_buildsRequiredPreambleIncludingDeclaration() {
        val clock = FakeClock(
            monoMs = 0L,
            wallMs = 1_741_483_200_000L
        )
        val sink = newSink(
            clock = clock,
            declarationSnapshot = sampleDeclaration("TASK-A")
        )

        sink.onStartRecording(sessionId = 42L, preFlightGroundWindowMs = 20_000L)
        val lines = sink.snapshotSessionLines(42L)

        assertTrue(lines.first().startsWith("AXCS"))
        assertTrue(lines.any { it.startsWith("HFDTEDATE:") })
        assertTrue(lines.any { it == "HFDTEDATE:090325,01" })
        assertTrue(lines.any { it.startsWith("HFPLTPILOTINCHARGE:") })
        assertTrue(lines.any { it.startsWith("HFCM2CREW2:") })
        assertTrue(lines.any { it.startsWith("HFGTYGLIDERTYPE:") })
        assertTrue(lines.any { it.startsWith("HFGIDGLIDERID:") })
        assertTrue(lines.any { it.startsWith("HFDTMGPSDATUM:WGS84") })
        assertTrue(lines.any { it.startsWith("HFRFWFIRMWAREVERSION:") })
        assertTrue(lines.any { it.startsWith("HFRHWHARDWAREVERSION:") })
        assertTrue(lines.any { it.startsWith("HFFTYFRTYPE:XCPro,SignedMobile") })
        assertTrue(lines.any { it.startsWith("HFGPSRECEIVER:") })
        assertTrue(lines.any { it.startsWith("HFPRSPRESSALTSENSOR:") })
        assertTrue(lines.any { it.startsWith("HFFRSSECURITY:") })
        assertTrue(lines.any { it.startsWith("HFALGALTGPS:") })
        assertTrue(lines.any { it.startsWith("HFALPALTPRESSURE:") })
        assertTrue(lines.any { it == "I023638IAS3941TAS" })

        val iIndex = lines.indexOfFirst { it.startsWith("I") }
        val cIndex = lines.indexOfFirst { it.startsWith("C") }
        assertTrue(iIndex >= 0)
        assertTrue(cIndex > iIndex)
    }

    @Test
    fun declarationSnapshot_isImmutableAfterStart() {
        val clock = FakeClock(
            monoMs = 0L,
            wallMs = 1_741_483_200_000L
        )
        val taskSource = MutableTaskSource(
            IgcTaskDeclarationStartSnapshot.Available(sampleDeclaration("TASK-OLD"))
        )
        val sink = IgcRecordingRuntimeActionSink(
            clock = clock,
            profileMetadataSource = FixedProfileSource(),
            recorderMetadataSource = FixedRecorderSource(),
            taskDeclarationSource = taskSource
        )

        sink.onStartRecording(sessionId = 1L, preFlightGroundWindowMs = 20_000L)
        val cLinesBefore = sink.snapshotSessionLines(1L).filter { it.startsWith("C") }
        taskSource.snapshot = IgcTaskDeclarationStartSnapshot.Available(sampleDeclaration("TASK-NEW"))
        sink.onTaskEvent(sessionId = 1L, payload = "TASK_CHANGED")
        val cLinesAfter = sink.snapshotSessionLines(1L).filter { it.startsWith("C") }

        assertEquals(cLinesBefore, cLinesAfter)
        assertTrue(cLinesAfter.first().contains("TASK-OLD"))
    }

    @Test
    fun systemEvent_dedupeWindow_blocksDuplicate_thenAllowsAfterWindow() {
        val clock = FakeClock(
            monoMs = 0L,
            wallMs = 1_741_483_200_000L
        )
        val sink = newSink(clock = clock, declarationSnapshot = sampleDeclaration("TASK-1"))
        sink.onStartRecording(sessionId = 2L, preFlightGroundWindowMs = 20_000L)

        clock.setMonoMs(10_000L)
        clock.setWallMs(1_741_483_210_000L)
        sink.onSystemEvent(sessionId = 2L, payload = "thermalling")

        clock.setMonoMs(11_000L)
        clock.setWallMs(1_741_483_211_000L)
        sink.onSystemEvent(sessionId = 2L, payload = "thermalling")

        clock.setMonoMs(16_500L)
        clock.setWallMs(1_741_483_216_500L)
        sink.onSystemEvent(sessionId = 2L, payload = "thermalling")

        val sysLines = sink.snapshotSessionLines(2L).filter { it.contains("SYSTHERMALLING") }
        assertEquals(2, sysLines.size)
    }

    @Test
    fun identicalInputs_produceDeterministicSessionOutput() {
        val firstClock = FakeClock(
            monoMs = 0L,
            wallMs = 1_741_483_200_000L
        )
        val secondClock = FakeClock(
            monoMs = 0L,
            wallMs = 1_741_483_200_000L
        )
        val declaration = sampleDeclaration("TASK-DET")
        val sinkA = newSink(firstClock, declaration)
        val sinkB = newSink(secondClock, declaration)

        sinkA.onStartRecording(9L, 20_000L)
        sinkB.onStartRecording(9L, 20_000L)

        firstClock.setMonoMs(10_000L)
        firstClock.setWallMs(1_741_483_210_000L)
        secondClock.setMonoMs(10_000L)
        secondClock.setWallMs(1_741_483_210_000L)

        val bLine = "B1234563351900S15112540EA0085000900072080"
        sinkA.onBRecord(9L, bLine, 1_741_483_210_000L)
        sinkB.onBRecord(9L, bLine, 1_741_483_210_000L)
        sinkA.onSystemEvent(9L, "ok")
        sinkB.onSystemEvent(9L, "ok")
        sinkA.onMarkCompleted(9L)
        sinkB.onMarkCompleted(9L)

        assertEquals(sinkA.snapshotSessionLines(9L), sinkB.snapshotSessionLines(9L))
    }

    @Test
    fun firstBRecord_rewritesDteHeaderUsingFirstFixUtcDate() {
        val clock = FakeClock(
            monoMs = 0L,
            wallMs = 1_741_564_798_000L
        )
        val sink = newSink(
            clock = clock,
            declarationSnapshot = sampleDeclaration("TASK-DTE")
        )
        sink.onStartRecording(sessionId = 7L, preFlightGroundWindowMs = 20_000L)

        val beforeFirstB = sink.snapshotSessionLines(7L)
        assertTrue(beforeFirstB.contains("HFDTEDATE:090325,01"))

        sink.onBRecord(
            sessionId = 7L,
            line = "B0000023351900S15112540EA0085000900072080",
            sampleWallTimeMs = 1_741_564_802_000L
        )

        val afterFirstB = sink.snapshotSessionLines(7L)
        assertTrue(afterFirstB.contains("HFDTEDATE:100325,01"))
    }

    @Test
    fun invalidTaskSnapshot_emitsDeterministicDeclarationDiagnosticLine() {
        val clock = FakeClock(
            monoMs = 0L,
            wallMs = 1_741_483_200_000L
        )
        val sink = IgcRecordingRuntimeActionSink(
            clock = clock,
            profileMetadataSource = FixedProfileSource(),
            recorderMetadataSource = FixedRecorderSource(),
            taskDeclarationSource = MutableTaskSource(
                IgcTaskDeclarationStartSnapshot.Invalid("WAYPOINT_COUNT_LT_2")
            )
        )

        sink.onStartRecording(sessionId = 77L, preFlightGroundWindowMs = 20_000L)
        val lines = sink.snapshotSessionLines(77L)

        assertTrue(lines.none { it.startsWith("C") })
        assertTrue(lines.contains("LXCSDECLARATION_OMITTED:WAYPOINT_COUNT_LT_2"))
    }

    @Test
    fun startRecording_persistsStructuredRecoveryMetadata_andFirstBUpdatesFirstFix() {
        val clock = FakeClock(
            monoMs = 0L,
            wallMs = 1_741_483_200_000L
        )
        val metadataStore = InMemoryRecoveryMetadataStore()
        val sink = IgcRecordingRuntimeActionSink(
            clock = clock,
            profileMetadataSource = FixedProfileSource(),
            recorderMetadataSource = FixedRecorderSource(),
            taskDeclarationSource = MutableTaskSource(IgcTaskDeclarationStartSnapshot.Absent),
            recoveryMetadataStore = metadataStore,
            flightLogRepository = NoopIgcFlightLogRepository,
            exportDiagnosticsRepository = NoOpIgcExportDiagnosticsRepository
        )

        sink.onStartRecording(sessionId = 14L, preFlightGroundWindowMs = 20_000L)

        assertEquals(
            IgcRecoveryMetadata(
                manufacturerId = "XCS",
                sessionSerial = "000014",
                sessionStartWallTimeMs = 1_741_483_200_000L,
                firstValidFixWallTimeMs = null,
                signatureProfile = IgcSecuritySignatureProfile.XCS
            ),
            metadataStore.loadMetadata(14L)
        )

        sink.onBRecord(
            sessionId = 14L,
            line = "B0000023351900S15112540EA0085000900072080",
            sampleWallTimeMs = 1_741_483_202_000L
        )

        assertEquals(
            IgcRecoveryMetadata(
                manufacturerId = "XCS",
                sessionSerial = "000014",
                sessionStartWallTimeMs = 1_741_483_200_000L,
                firstValidFixWallTimeMs = 1_741_483_202_000L,
                signatureProfile = IgcSecuritySignatureProfile.XCS
            ),
            metadataStore.loadMetadata(14L)
        )
    }

    @Test
    fun finalizeRecording_publishesSessionPayloadWithCompletionLine() {
        val clock = FakeClock(
            monoMs = 0L,
            wallMs = 1_741_483_200_000L
        )
        val flightLogRepository = CapturingFlightLogRepository()
        val sink = IgcRecordingRuntimeActionSink(
            clock = clock,
            profileMetadataSource = FixedProfileSource(),
            recorderMetadataSource = FixedRecorderSource(),
            taskDeclarationSource = MutableTaskSource(
                IgcTaskDeclarationStartSnapshot.Available(sampleDeclaration("TASK-PUBLISH"))
            ),
            recoveryMetadataStore = NoopIgcRecoveryMetadataStore,
            flightLogRepository = flightLogRepository,
            exportDiagnosticsRepository = NoOpIgcExportDiagnosticsRepository
        )

        sink.onStartRecording(sessionId = 5L, preFlightGroundWindowMs = 20_000L)
        sink.onBRecord(
            sessionId = 5L,
            line = "B0000023351900S15112540EA0085000900072080",
            sampleWallTimeMs = 1_741_483_202_000L
        )
        val result = sink.onFinalizeRecording(sessionId = 5L, postFlightGroundWindowMs = 5_000L)
        sink.onMarkCompleted(sessionId = 5L)

        val request = flightLogRepository.lastRequest
        assertTrue(request != null)
        requireNotNull(request)
        assertTrue(result is IgcFinalizeResult.Published)
        assertEquals(5L, request.sessionId)
        assertEquals(1_741_483_202_000L, request.firstValidFixWallTimeMs)
        assertEquals(IgcSecuritySignatureProfile.XCS, request.signatureProfile)
        assertTrue(request.lines.any { it.startsWith("E") && it.contains("FLTCOMPLETED") })

        val lines = sink.snapshotSessionLines(5L)
        assertEquals(1, lines.count { it.startsWith("E") && it.contains("FLTCOMPLETED") })
    }

    @Test
    fun finalizeRecording_returnsFailureAndPublishesDiagnostic_whenRepositoryFails() {
        val clock = FakeClock(
            monoMs = 0L,
            wallMs = 1_741_483_200_000L
        )
        val diagnosticsRepository = InMemoryIgcExportDiagnosticsRepository()
        val sink = IgcRecordingRuntimeActionSink(
            clock = clock,
            profileMetadataSource = FixedProfileSource(),
            recorderMetadataSource = FixedRecorderSource(),
            taskDeclarationSource = MutableTaskSource(IgcTaskDeclarationStartSnapshot.Absent),
            recoveryMetadataStore = NoopIgcRecoveryMetadataStore,
            flightLogRepository = FailingFlightLogRepository(),
            exportDiagnosticsRepository = diagnosticsRepository
        )

        sink.onStartRecording(sessionId = 6L, preFlightGroundWindowMs = 20_000L)
        val result = sink.onFinalizeRecording(sessionId = 6L, postFlightGroundWindowMs = 5_000L)

        require(result is IgcFinalizeResult.Failure)
        assertEquals(IgcFinalizeResult.ErrorCode.LINT_VALIDATION_FAILED, result.code)
        assertEquals(IgcExportDiagnosticCode.LINT_VALIDATION_FAILED, diagnosticsRepository.latest.value?.code)
        assertEquals("IGC export failed: A record must be first (line 1)", diagnosticsRepository.latest.value?.message)
    }

    private fun newSink(
        clock: FakeClock,
        declarationSnapshot: IgcTaskDeclarationSnapshot?
    ): IgcRecordingRuntimeActionSink {
        return IgcRecordingRuntimeActionSink(
            clock = clock,
            profileMetadataSource = FixedProfileSource(),
            recorderMetadataSource = FixedRecorderSource(),
            taskDeclarationSource = MutableTaskSource(
                declarationSnapshot?.let { IgcTaskDeclarationStartSnapshot.Available(it) }
                    ?: IgcTaskDeclarationStartSnapshot.Absent
            )
        )
    }

    private class CapturingFlightLogRepository : IgcFlightLogRepository {
        var lastRequest: IgcFinalizeRequest? = null

        override fun finalizeSession(request: IgcFinalizeRequest): IgcFinalizeResult {
            lastRequest = request
            return IgcFinalizeResult.Published(
                entry = IgcLogEntry(
                    document = com.example.xcpro.common.documents.DocumentRef(
                        uri = "content://downloads/igc/1",
                        displayName = "2025-03-09-XCS-000005-01.IGC"
                    ),
                    displayName = "2025-03-09-XCS-000005-01.IGC",
                    sizeBytes = 123L,
                    lastModifiedEpochMillis = 0L,
                    utcDate = java.time.LocalDate.of(2025, 3, 9),
                    durationSeconds = 0L
                ),
                fileName = "2025-03-09-XCS-000005-01.IGC"
            )
        }
    }

    private class FailingFlightLogRepository : IgcFlightLogRepository {
        override fun finalizeSession(request: IgcFinalizeRequest): IgcFinalizeResult {
            return IgcFinalizeResult.Failure(
                code = IgcFinalizeResult.ErrorCode.LINT_VALIDATION_FAILED,
                message = "IGC export failed: A record must be first (line 1)"
            )
        }
    }

    private class InMemoryRecoveryMetadataStore : IgcRecoveryMetadataStore {
        private val metadataBySessionId = mutableMapOf<Long, IgcRecoveryMetadata>()

        override fun saveMetadata(sessionId: Long, metadata: IgcRecoveryMetadata) {
            metadataBySessionId[sessionId] = metadata
        }

        override fun loadMetadata(sessionId: Long): IgcRecoveryMetadata? {
            return metadataBySessionId[sessionId]
        }

        override fun clearMetadata(sessionId: Long) {
            metadataBySessionId.remove(sessionId)
        }
    }

    private fun sampleDeclaration(taskId: String): IgcTaskDeclarationSnapshot {
        return IgcTaskDeclarationSnapshot(
            taskId = taskId,
            capturedAtUtcMs = 1_741_483_200_000L,
            waypoints = listOf(
                IgcTaskDeclarationWaypoint("START", -33.865, 151.209),
                IgcTaskDeclarationWaypoint("TP1", -33.900, 151.250),
                IgcTaskDeclarationWaypoint("FINISH", -33.920, 151.280)
            )
        )
    }

    private class FixedProfileSource : IgcProfileMetadataSource {
        override fun activeProfileMetadata(): IgcProfileMetadata {
            return IgcProfileMetadata(
                pilotName = "Pilot One",
                crew2 = null,
                gliderType = "Sailplane",
                gliderId = "VH-XYZ"
            )
        }
    }

    private class FixedRecorderSource : IgcRecorderMetadataSource {
        override fun recorderMetadata(): IgcRecorderMetadata {
            return IgcRecorderMetadata(
                manufacturerId = "XCS",
                recorderType = "XCPro,SignedMobile",
                firmwareVersion = "1.0.0",
                hardwareVersion = "Pixel / Android 16",
                gpsReceiver = "NKN",
                pressureSensor = "ANDROID_BARO",
                securityStatus = "SIGNED",
                securitySignatureProfile = IgcSecuritySignatureProfile.XCS,
                gpsAltitudeDatum = IgcGpsAltitudeDatum.ELL,
                pressureAltitudeDatum = IgcPressureAltitudeDatum.ISA
            )
        }
    }

    private class MutableTaskSource(
        var snapshot: IgcTaskDeclarationStartSnapshot
    ) : IgcTaskDeclarationSource {
        override fun snapshotForStart(
            sessionId: Long,
            capturedAtUtcMs: Long
        ): IgcTaskDeclarationStartSnapshot = snapshot
    }
}
