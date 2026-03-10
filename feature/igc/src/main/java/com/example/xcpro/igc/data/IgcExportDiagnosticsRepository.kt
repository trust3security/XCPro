package com.example.xcpro.igc.data

import com.example.xcpro.igc.domain.IgcLintIssue
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class IgcExportDiagnosticSource {
    FINALIZE,
    REPLAY_OPEN,
    COPY_TO,
    SHARE
}

enum class IgcExportDiagnosticCode {
    WRITE_FAILED,
    NAME_SPACE_EXHAUSTED,
    EMPTY_PAYLOAD,
    LINT_VALIDATION_FAILED,
    DOCUMENT_READ_FAILED,
    REPLAY_OPEN_FAILED,
    COPY_FAILED,
    SHARE_FAILED,
    UNKNOWN
}

data class IgcExportDiagnostic(
    val source: IgcExportDiagnosticSource,
    val code: IgcExportDiagnosticCode,
    val message: String,
    val sessionId: Long? = null,
    val fileName: String? = null,
    val lintIssues: List<IgcLintIssue> = emptyList()
)

interface IgcExportDiagnosticsRepository {
    val latest: StateFlow<IgcExportDiagnostic?>

    fun publish(diagnostic: IgcExportDiagnostic)

    fun clear()
}

object NoOpIgcExportDiagnosticsRepository : IgcExportDiagnosticsRepository {
    private val latestState = MutableStateFlow<IgcExportDiagnostic?>(null)
    override val latest: StateFlow<IgcExportDiagnostic?> = latestState.asStateFlow()

    override fun publish(diagnostic: IgcExportDiagnostic) = Unit

    override fun clear() = Unit
}

@Singleton
class InMemoryIgcExportDiagnosticsRepository @Inject constructor() : IgcExportDiagnosticsRepository {
    private val _latest = MutableStateFlow<IgcExportDiagnostic?>(null)
    override val latest: StateFlow<IgcExportDiagnostic?> = _latest.asStateFlow()

    override fun publish(diagnostic: IgcExportDiagnostic) {
        _latest.value = diagnostic
    }

    override fun clear() {
        _latest.value = null
    }
}

internal fun IgcFinalizeResult.Failure.toExportDiagnostic(sessionId: Long): IgcExportDiagnostic {
    return IgcExportDiagnostic(
        source = IgcExportDiagnosticSource.FINALIZE,
        code = code.toDiagnosticCode(),
        message = message,
        sessionId = sessionId,
        lintIssues = lintIssues
    )
}

private fun IgcFinalizeResult.ErrorCode.toDiagnosticCode(): IgcExportDiagnosticCode = when (this) {
    IgcFinalizeResult.ErrorCode.WRITE_FAILED -> IgcExportDiagnosticCode.WRITE_FAILED
    IgcFinalizeResult.ErrorCode.NAME_SPACE_EXHAUSTED -> IgcExportDiagnosticCode.NAME_SPACE_EXHAUSTED
    IgcFinalizeResult.ErrorCode.EMPTY_PAYLOAD -> IgcExportDiagnosticCode.EMPTY_PAYLOAD
    IgcFinalizeResult.ErrorCode.LINT_VALIDATION_FAILED -> IgcExportDiagnosticCode.LINT_VALIDATION_FAILED
}
