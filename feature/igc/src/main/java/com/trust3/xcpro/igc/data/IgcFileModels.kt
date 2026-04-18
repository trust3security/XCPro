package com.trust3.xcpro.igc.data

import com.trust3.xcpro.common.documents.DocumentRef
import com.trust3.xcpro.igc.domain.IgcLintIssue
import com.trust3.xcpro.igc.domain.IgcSecuritySignatureProfile
import java.time.LocalDate

data class IgcLogEntry(
    val document: DocumentRef,
    val displayName: String,
    val sizeBytes: Long,
    val lastModifiedEpochMillis: Long,
    val utcDate: LocalDate?,
    val durationSeconds: Long?
)

data class IgcFinalizeRequest(
    val sessionId: Long,
    val sessionStartWallTimeMs: Long,
    val firstValidFixWallTimeMs: Long?,
    val manufacturerId: String,
    val sessionSerial: String,
    val signatureProfile: IgcSecuritySignatureProfile,
    val lines: List<String>
)

sealed interface IgcFinalizeResult {
    data class Published(
        val entry: IgcLogEntry,
        val fileName: String
    ) : IgcFinalizeResult

    data class AlreadyPublished(
        val entry: IgcLogEntry
    ) : IgcFinalizeResult

    data class Failure(
        val code: ErrorCode,
        val message: String,
        val lintIssues: List<IgcLintIssue> = emptyList()
    ) : IgcFinalizeResult

    enum class ErrorCode {
        WRITE_FAILED,
        NAME_SPACE_EXHAUSTED,
        EMPTY_PAYLOAD,
        LINT_VALIDATION_FAILED
    }
}
