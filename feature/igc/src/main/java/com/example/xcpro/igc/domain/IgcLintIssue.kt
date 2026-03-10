package com.example.xcpro.igc.domain

enum class IgcLintSeverity {
    ERROR
}

enum class IgcLintIssueCode {
    FILE_EMPTY,
    A_RECORD_NOT_FIRST,
    B_RECORD_CONTAINS_SPACE,
    I_RECORD_CONTAINS_SPACE,
    B_RECORD_UTC_NON_MONOTONIC,
    LINE_ENDING_NOT_CRLF,
    I_RECORD_EXTENSION_COUNT_INVALID,
    I_RECORD_EXTENSION_RANGE_INVALID,
    I_RECORD_EXTENSION_RANGE_OVERLAP
}

data class IgcLintLocation(
    val lineNumber: Int? = null,
    val recordType: Char? = null,
    val byteStart: Int? = null,
    val byteEnd: Int? = null
)

data class IgcLintIssue(
    val code: IgcLintIssueCode,
    val severity: IgcLintSeverity = IgcLintSeverity.ERROR,
    val location: IgcLintLocation? = null,
    val detail: String? = null
)
