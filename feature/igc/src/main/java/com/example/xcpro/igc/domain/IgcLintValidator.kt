package com.example.xcpro.igc.domain

enum class IgcLintSource {
    FINALIZE_EXPORT,
    EXISTING_DOCUMENT,
    REPLAY_OPEN,
    SHARE_EXPORT
}

data class IgcLintPayload(
    val bytes: ByteArray,
    val source: IgcLintSource = IgcLintSource.FINALIZE_EXPORT
)

interface IgcLintValidator {
    fun validate(
        payload: IgcLintPayload,
        ruleSet: IgcLintRuleSet = IgcLintRuleSet.Phase7Strict
    ): List<IgcLintIssue>
}
