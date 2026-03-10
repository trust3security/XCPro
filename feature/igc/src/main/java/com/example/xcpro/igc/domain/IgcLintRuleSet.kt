package com.example.xcpro.igc.domain

enum class IgcLintRule {
    FILE_NOT_EMPTY,
    A_RECORD_FIRST,
    B_RECORD_NO_SPACES,
    I_RECORD_NO_SPACES,
    B_RECORD_UTC_MONOTONIC,
    CANONICAL_CRLF_LINE_ENDINGS,
    I_RECORD_EXTENSION_COUNT_MATCHES_DECLARATION,
    I_RECORD_EXTENSION_BYTE_RANGE_VALID,
    I_RECORD_EXTENSION_BYTE_RANGE_NON_OVERLAPPING
}

data class IgcLintRuleSet(
    val name: String,
    val rules: Set<IgcLintRule>
) {
    init {
        require(rules.isNotEmpty()) { "IgcLintRuleSet must declare at least one rule" }
    }

    fun contains(rule: IgcLintRule): Boolean = rule in rules

    companion object {
        val Phase7Strict = IgcLintRuleSet(
            name = "PHASE7_STRICT",
            rules = setOf(
                IgcLintRule.FILE_NOT_EMPTY,
                IgcLintRule.A_RECORD_FIRST,
                IgcLintRule.B_RECORD_NO_SPACES,
                IgcLintRule.I_RECORD_NO_SPACES,
                IgcLintRule.B_RECORD_UTC_MONOTONIC,
                IgcLintRule.CANONICAL_CRLF_LINE_ENDINGS,
                IgcLintRule.I_RECORD_EXTENSION_COUNT_MATCHES_DECLARATION,
                IgcLintRule.I_RECORD_EXTENSION_BYTE_RANGE_VALID,
                IgcLintRule.I_RECORD_EXTENSION_BYTE_RANGE_NON_OVERLAPPING
            )
        )
    }
}
