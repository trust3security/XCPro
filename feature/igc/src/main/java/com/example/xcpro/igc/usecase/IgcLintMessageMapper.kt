package com.example.xcpro.igc.usecase

import com.example.xcpro.igc.domain.IgcLintIssue
import com.example.xcpro.igc.domain.IgcLintIssueCode
import javax.inject.Inject

class IgcLintMessageMapper @Inject constructor() {

    fun messageFor(issue: IgcLintIssue): String {
        val suffix = issue.location?.lineNumber?.let { " (line $it)" }.orEmpty()
        return when (issue.code) {
            IgcLintIssueCode.FILE_EMPTY -> "IGC export failed: file is empty"
            IgcLintIssueCode.A_RECORD_NOT_FIRST -> "IGC export failed: A record must be first$suffix"
            IgcLintIssueCode.B_RECORD_CONTAINS_SPACE -> "IGC export failed: B record contains spaces$suffix"
            IgcLintIssueCode.I_RECORD_CONTAINS_SPACE -> "IGC export failed: I record contains spaces$suffix"
            IgcLintIssueCode.B_RECORD_UTC_NON_MONOTONIC ->
                "IGC export failed: B record UTC times must be monotonic$suffix"
            IgcLintIssueCode.LINE_ENDING_NOT_CRLF ->
                "IGC export failed: file must use CRLF line endings$suffix"
            IgcLintIssueCode.I_RECORD_EXTENSION_COUNT_INVALID ->
                "IGC export failed: I record extension count is invalid$suffix"
            IgcLintIssueCode.I_RECORD_EXTENSION_RANGE_INVALID ->
                "IGC export failed: I record extension byte range is invalid$suffix"
            IgcLintIssueCode.I_RECORD_EXTENSION_RANGE_OVERLAP ->
                "IGC export failed: I record extension byte ranges overlap$suffix"
        }
    }

    fun summarize(issues: List<IgcLintIssue>): String {
        return issues.firstOrNull()?.let(::messageFor) ?: "IGC export failed: validation failed"
    }
}
