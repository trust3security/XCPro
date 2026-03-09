package com.example.xcpro.igc.domain

enum class IgcSharePrivacyMode {
    ORIGINAL,
    REDACT_PERSONAL_HEADERS,
    REDACT_PERSONAL_AND_DEVICE
}

enum class IgcSharePrivacyErrorCode {
    REDACTION_PARSE_FAILED,
    REDACTION_WRITE_FAILED,
    REDACTION_UNSUPPORTED_RECORD,
    SHARE_TARGET_UNAVAILABLE
}

sealed interface IgcShareRequestResult {
    data class Success(val shareMode: IgcSharePrivacyMode, val documentName: String) : IgcShareRequestResult
    data class Failure(val code: IgcSharePrivacyErrorCode, val message: String) : IgcShareRequestResult
}

internal const val IGC_REDACTION_PLACEHOLDER = "REDACTED"
