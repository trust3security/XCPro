# Phase 7 Error Taxonomy Mapping

Purpose:

- map lint issues to finalize error and UI diagnostic code paths.

## Validation to Failure Mapping

| Lint Issue | Finalize Error | Diagnostic Code | Entry point |
| --- | --- | --- | --- |
| `FILE_EMPTY` | `IgcFinalizeResult.ErrorCode.EMPTY_PAYLOAD` (legacy path) | `IgcExportDiagnosticCode.EMPTY_PAYLOAD` | `IgcFlightLogRepository` pre-check / sanitize path |
| Any `IgcLintIssueCode` | `IgcFinalizeResult.ErrorCode.LINT_VALIDATION_FAILED` | `IgcExportDiagnosticCode.LINT_VALIDATION_FAILED` | `IgcExportValidationAdapter` + `IgcFlightLogRepository.finalizeSession` |
| `WRITE_FAILED` / `NAME_SPACE_EXHAUSTED` | same finalize code | `WRITE_FAILED` / `NAME_SPACE_EXHAUSTED` | finalize write path |
| Document read failure while opening staged/final docs | `DOCUMENT_READ_FAILED` | `DOCUMENT_READ_FAILED` | `IgcDownloadsRepository.readDocumentBytes` |
| Replay/share/copy failures | n/a | `REPLAY_OPEN_FAILED` / `SHARE_FAILED` / `COPY_FAILED` | `IgcFilesUseCase`, `IgcFilesViewModel`, `IgcFilesScreen` |

## Messaging Mapping

- User-facing text for lint issues is produced by `IgcLintMessageMapper.messageFor`.
- Failure summary uses `IgcLintMessageMapper.summarize` in `IgcExportValidationAdapter`.
- UI displays diagnostics through `IgcFilesViewModel` via `IgcExportDiagnostic` flow.

## Ownership

- Lint issue definitions: `feature/igc/src/main/java/com/example/xcpro/igc/domain/IgcLintIssue.kt`
- Severity/dispatch: `feature/igc/src/main/java/com/example/xcpro/igc/domain/IgcLintRuleSet.kt`
- Finalize translation: `feature/igc/src/main/java/com/example/xcpro/igc/data/IgcExportValidationAdapter.kt`
- SSOT diagnostic sink: `feature/igc/src/main/java/com/example/xcpro/igc/data/IgcExportDiagnosticsRepository.kt`
