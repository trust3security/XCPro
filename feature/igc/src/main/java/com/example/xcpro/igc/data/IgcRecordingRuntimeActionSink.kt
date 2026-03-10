package com.example.xcpro.igc.data

import com.example.xcpro.core.time.Clock
import com.example.xcpro.igc.IgcRecordingActionSink
import com.example.xcpro.igc.domain.IgcEventDedupePolicy
import com.example.xcpro.igc.domain.IgcHeaderContext
import com.example.xcpro.igc.domain.IgcHeaderMapper
import com.example.xcpro.igc.domain.IgcProfileMetadataSource
import com.example.xcpro.igc.domain.IgcRecordFormatter
import com.example.xcpro.igc.domain.IgcRecoveryMetadata
import com.example.xcpro.igc.domain.IgcRecorderMetadataSource
import com.example.xcpro.igc.domain.IgcSecuritySignatureProfile
import com.example.xcpro.igc.domain.IgcTaskDeclarationMapper
import com.example.xcpro.igc.domain.IgcTaskDeclarationStartSnapshot
import com.example.xcpro.igc.domain.IgcTaskDeclarationSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory runtime sink that owns Phase 4 A/H/I/C/E assembly per session.
 *
 * Persistence/export wiring remains phase 5, but this sink is the concrete
 * session authority for deterministic metadata/declaration/event coverage.
 */
@Singleton
class IgcRecordingRuntimeActionSink @Inject constructor(
    private val clock: Clock,
    private val profileMetadataSource: IgcProfileMetadataSource,
    private val recorderMetadataSource: IgcRecorderMetadataSource,
    private val taskDeclarationSource: IgcTaskDeclarationSource,
    private val recoveryMetadataStore: IgcRecoveryMetadataStore,
    private val flightLogRepository: IgcFlightLogRepository,
    private val exportDiagnosticsRepository: IgcExportDiagnosticsRepository
) : IgcRecordingActionSink {

    private data class SessionBuffer(
        val sessionId: Long,
        val sessionStartWallTimeMs: Long,
        val manufacturerId: String,
        val sessionSerial: String,
        val signatureProfile: IgcSecuritySignatureProfile,
        val lRecordSource: String,
        val dteHeaderLineIndex: Int,
        val lines: MutableList<String>,
        var firstValidBWallTimeMs: Long? = null,
        var finalizePublished: Boolean = false,
        var lastFinalizeResult: IgcFinalizeResult? = null,
        var dteResolvedFromFirstB: Boolean = false,
        var eventState: IgcEventDedupePolicy.State = IgcEventDedupePolicy.State()
    )

    private val lock = Any()
    private val formatter = IgcRecordFormatter()
    private val headerMapper = IgcHeaderMapper()
    private val taskDeclarationMapper = IgcTaskDeclarationMapper(formatter)
    private val eventPolicy = IgcEventDedupePolicy()

    private val activeSessions = mutableMapOf<Long, SessionBuffer>()
    private val completedSessions = mutableMapOf<Long, SessionBuffer>()
    private val flightCounterByUtcDate = mutableMapOf<String, Int>()

    constructor(
        clock: Clock,
        profileMetadataSource: IgcProfileMetadataSource,
        recorderMetadataSource: IgcRecorderMetadataSource,
        taskDeclarationSource: IgcTaskDeclarationSource
    ) : this(
        clock = clock,
        profileMetadataSource = profileMetadataSource,
        recorderMetadataSource = recorderMetadataSource,
        taskDeclarationSource = taskDeclarationSource,
        recoveryMetadataStore = NoopIgcRecoveryMetadataStore,
        flightLogRepository = NoopIgcFlightLogRepository,
        exportDiagnosticsRepository = NoOpIgcExportDiagnosticsRepository
    )

    override fun onSessionArmed(monoTimeMs: Long) = Unit

    override fun onStartRecording(sessionId: Long, preFlightGroundWindowMs: Long) {
        val wallNow = clock.nowWallMs()
        val sessionStartUtcDate = Instant.ofEpochMilli(wallNow).atOffset(ZoneOffset.UTC).toLocalDate()
        var recoveryMetadata: IgcRecoveryMetadata? = null
        synchronized(lock) {
            if (activeSessions.containsKey(sessionId)) return
            val recorderMetadata = recorderMetadataSource.recorderMetadata()
            val profileMetadata = profileMetadataSource.activeProfileMetadata()
            val headerContext = IgcHeaderContext(
                utcDate = sessionStartUtcDate,
                flightNumberOfDay = FALLBACK_FLIGHT_NUMBER,
                profileMetadata = profileMetadata,
                recorderMetadata = recorderMetadata
            )

            val lines = mutableListOf<String>()
            lines += formatter.formatA(
                manufacturerId = recorderMetadata.manufacturerId,
                serialId = serialForSession(sessionId)
            )
            val headers = headerMapper.map(headerContext)
            lines += headers.map(formatter::formatH)
            lines += formatter.formatI(IgcRecordFormatter.IAS_TAS_EXTENSIONS)

            val declarationSnapshot = taskDeclarationSource.snapshotForStart(
                sessionId = sessionId,
                capturedAtUtcMs = wallNow
            )
            val dteHeaderOffset = headers.indexOfFirst { it.code == "DTE" }
            require(dteHeaderOffset >= 0) { "Required DTE header missing from mapper output" }
            val dteHeaderLineIndex = 1 + dteHeaderOffset

            val session = SessionBuffer(
                sessionId = sessionId,
                sessionStartWallTimeMs = wallNow,
                manufacturerId = recorderMetadata.manufacturerId,
                sessionSerial = serialForSession(sessionId),
                signatureProfile = recorderMetadata.securitySignatureProfile,
                lRecordSource = recorderMetadata.manufacturerId,
                dteHeaderLineIndex = dteHeaderLineIndex,
                lines = lines
            )
            appendTaskDeclarationLocked(session, declarationSnapshot)

            activeSessions[sessionId] = session
            recoveryMetadata = session.toRecoveryMetadata()
            emitEventLocked(
                session = session,
                code = "FLT",
                payload = "START"
            )
        }
        recoveryMetadata?.let { metadata ->
            recoveryMetadataStore.saveMetadata(sessionId = sessionId, metadata = metadata)
        }
    }

    override fun onFinalizeRecording(
        sessionId: Long,
        postFlightGroundWindowMs: Long
    ): IgcFinalizeResult {
        val publishRequest: IgcFinalizeRequest
        val completionLine: String
        synchronized(lock) {
            val session = activeSessions[sessionId]
                ?: return IgcFinalizeResult.Failure(
                    code = IgcFinalizeResult.ErrorCode.WRITE_FAILED,
                    message = "No active IGC session to finalize"
                )
            emitEventLocked(
                session = session,
                code = "FLT",
                payload = "FINALIZE"
            )
            if (session.finalizePublished) {
                return session.lastFinalizeResult ?: IgcFinalizeResult.Failure(
                    code = IgcFinalizeResult.ErrorCode.WRITE_FAILED,
                    message = "IGC session already finalized without stored result"
                )
            }
            completionLine = formatCompletionLine()
            publishRequest = IgcFinalizeRequest(
                sessionId = session.sessionId,
                sessionStartWallTimeMs = session.sessionStartWallTimeMs,
                firstValidFixWallTimeMs = session.firstValidBWallTimeMs,
                manufacturerId = session.manufacturerId,
                sessionSerial = session.sessionSerial,
                signatureProfile = session.signatureProfile,
                lines = session.lines.toList() + completionLine
            )
        }
        return when (val publishResult = flightLogRepository.finalizeSession(publishRequest)) {
            is IgcFinalizeResult.Published -> synchronized(lock) {
                activeSessions[sessionId]?.let { session ->
                    session.lines += completionLine
                    session.finalizePublished = true
                    session.lastFinalizeResult = publishResult
                }
                exportDiagnosticsRepository.clear()
                publishResult
            }
            is IgcFinalizeResult.AlreadyPublished -> synchronized(lock) {
                activeSessions[sessionId]?.let { session ->
                    session.finalizePublished = true
                    session.lastFinalizeResult = publishResult
                }
                exportDiagnosticsRepository.clear()
                publishResult
            }
            is IgcFinalizeResult.Failure -> {
                exportDiagnosticsRepository.publish(
                    publishResult.toExportDiagnostic(sessionId = sessionId)
                )
                publishResult
            }
        }
    }

    override fun onMarkCompleted(sessionId: Long) {
        synchronized(lock) {
            val session = activeSessions[sessionId] ?: return
            if (!session.finalizePublished) {
                emitEventLocked(
                    session = session,
                    code = "FLT",
                    payload = "COMPLETED"
                )
            }
            completedSessions[sessionId] = session
            activeSessions.remove(sessionId)
        }
    }

    override fun onMarkFailed(sessionId: Long, reason: String) {
        synchronized(lock) {
            val session = activeSessions[sessionId] ?: return
            emitEventLocked(
                session = session,
                code = "FLT",
                payload = "FAILED:${reason.trim()}"
            )
            completedSessions[sessionId] = session
            activeSessions.remove(sessionId)
        }
    }

    override fun onBRecord(sessionId: Long, line: String, sampleWallTimeMs: Long) {
        var recoveryMetadata: IgcRecoveryMetadata? = null
        synchronized(lock) {
            val session = activeSessions[sessionId] ?: return
            if (!line.startsWith("B")) return
            if (line.any { it == '\n' || it == '\r' }) return
            if (sampleWallTimeMs < 0L) return
            resolveDteFromFirstBLocked(session, sampleWallTimeMs)
            if (session.firstValidBWallTimeMs == null) {
                session.firstValidBWallTimeMs = sampleWallTimeMs
                recoveryMetadata = session.toRecoveryMetadata()
            }
            session.lines += line
        }
        recoveryMetadata?.let { metadata ->
            recoveryMetadataStore.saveMetadata(sessionId = sessionId, metadata = metadata)
        }
    }

    override fun onTaskEvent(sessionId: Long, payload: String) {
        synchronized(lock) {
            val session = activeSessions[sessionId] ?: return
            emitEventLocked(
                session = session,
                code = "TSK",
                payload = payload
            )
        }
    }

    override fun onSystemEvent(sessionId: Long, payload: String) {
        synchronized(lock) {
            val session = activeSessions[sessionId] ?: return
            emitEventLocked(
                session = session,
                code = "SYS",
                payload = payload
            )
        }
    }

    fun snapshotSessionLines(sessionId: Long): List<String> {
        synchronized(lock) {
            return (activeSessions[sessionId] ?: completedSessions[sessionId])
                ?.lines
                ?.toList()
                ?: emptyList()
        }
    }

    private fun emitEventLocked(
        session: SessionBuffer,
        code: String,
        payload: String
    ) {
        val normalizedPayload = IgcEventDedupePolicy.normalizePayload(payload)
        val dedupeKey = "${session.sessionId}|${normalizeCode(code)}|$normalizedPayload"
        val decision = eventPolicy.evaluate(
            state = session.eventState,
            dedupeKey = dedupeKey,
            monoNowMs = clock.nowMonoMs()
        )
        session.eventState = decision.nextState
        if (!decision.shouldEmit) return
        val wallNow = clock.nowWallMs()
        val utcTime = Instant.ofEpochMilli(wallNow).atOffset(ZoneOffset.UTC).toLocalTime()
        session.lines += formatter.formatE(
            IgcRecordFormatter.EventRecord(
                timeUtc = utcTime,
                code = normalizeCode(code),
                payload = normalizedPayload.ifBlank { null }
            )
        )
    }

    private fun normalizeCode(raw: String): String {
        val base = raw.trim().uppercase()
        return when {
            base.length == 3 -> base
            base.length > 3 -> base.take(3)
            else -> base.padEnd(3, 'X')
        }
    }

    private fun serialForSession(sessionId: Long): String {
        val normalized = Math.floorMod(sessionId, 1_000_000L).toInt()
        return normalized.toString().padStart(6, '0')
    }

    private fun formatCompletionLine(): String {
        val wallNow = clock.nowWallMs()
        val utcTime = Instant.ofEpochMilli(wallNow).atOffset(ZoneOffset.UTC).toLocalTime()
        return formatter.formatE(
            IgcRecordFormatter.EventRecord(
                timeUtc = utcTime,
                code = "FLT",
                payload = "COMPLETED"
            )
        )
    }

    private fun resolveDteFromFirstBLocked(session: SessionBuffer, firstBWallTimeMs: Long) {
        if (session.dteResolvedFromFirstB) return
        val firstBDate = Instant.ofEpochMilli(firstBWallTimeMs).atOffset(ZoneOffset.UTC).toLocalDate()
        val flightNumber = nextFlightNumber(firstBDate.toString())
        session.lines[session.dteHeaderLineIndex] = formatter.formatH(
            IgcRecordFormatter.HeaderRecord(
                source = 'F',
                code = "DTE",
                longName = "DATE",
                value = formatDteValue(firstBDate, flightNumber)
            )
        )
        session.dteResolvedFromFirstB = true
    }

    private fun appendTaskDeclarationLocked(
        session: SessionBuffer,
        declarationSnapshot: IgcTaskDeclarationStartSnapshot
    ) {
        when (declarationSnapshot) {
            IgcTaskDeclarationStartSnapshot.Absent -> Unit
            is IgcTaskDeclarationStartSnapshot.Invalid -> {
                appendTaskDeclarationDiagnosticLocked(session, declarationSnapshot.reason)
            }
            is IgcTaskDeclarationStartSnapshot.Available -> {
                val lines = taskDeclarationMapper.map(declarationSnapshot.snapshot)
                if (lines.isEmpty()) {
                    appendTaskDeclarationDiagnosticLocked(session, "MAPPER_REJECTED")
                } else {
                    session.lines += lines
                }
            }
        }
    }

    private fun appendTaskDeclarationDiagnosticLocked(session: SessionBuffer, reason: String) {
        session.lines += formatter.formatL(
            source = session.lRecordSource,
            text = "DECLARATION_OMITTED:${normalizeDiagnosticReason(reason)}"
        )
    }

    private fun normalizeDiagnosticReason(raw: String): String {
        val collapsed = raw
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()
            .uppercase()
        if (collapsed.isBlank()) return "UNKNOWN"
        return collapsed
            .replace(Regex("[^A-Z0-9]+"), "_")
            .trim('_')
            .ifBlank { "UNKNOWN" }
            .take(MAX_DIAGNOSTIC_REASON_LENGTH)
    }

    private fun formatDteValue(date: LocalDate, flightNumber: Int): String {
        val datePart = DTE_DATE_FORMATTER.format(date)
        val dayFlight = flightNumber.coerceIn(1, 99).toString().padStart(2, '0')
        return "$datePart,$dayFlight"
    }

    private fun nextFlightNumber(dateKey: String): Int {
        val next = (flightCounterByUtcDate[dateKey] ?: 0) + 1
        val clamped = next.coerceAtMost(99)
        flightCounterByUtcDate[dateKey] = clamped
        return clamped
    }

    private fun SessionBuffer.toRecoveryMetadata(): IgcRecoveryMetadata {
        return IgcRecoveryMetadata(
            manufacturerId = manufacturerId,
            sessionSerial = sessionSerial,
            sessionStartWallTimeMs = sessionStartWallTimeMs,
            firstValidFixWallTimeMs = firstValidBWallTimeMs,
            signatureProfile = signatureProfile
        )
    }

    companion object {
        private val DTE_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("ddMMyy")
        private const val FALLBACK_FLIGHT_NUMBER = 1
        private const val MAX_DIAGNOSTIC_REASON_LENGTH = 40
    }
}
