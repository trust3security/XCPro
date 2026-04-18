package com.trust3.xcpro.igc.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class IgcLintRuleSetTest {

    @Test
    fun phase7Strict_containsAllPhase7Rules() {
        val ruleSet = IgcLintRuleSet.Phase7Strict

        assertTrue(ruleSet.contains(IgcLintRule.FILE_NOT_EMPTY))
        assertTrue(ruleSet.contains(IgcLintRule.A_RECORD_FIRST))
        assertTrue(ruleSet.contains(IgcLintRule.B_RECORD_NO_SPACES))
        assertTrue(ruleSet.contains(IgcLintRule.I_RECORD_NO_SPACES))
        assertTrue(ruleSet.contains(IgcLintRule.B_RECORD_UTC_MONOTONIC))
        assertTrue(ruleSet.contains(IgcLintRule.CANONICAL_CRLF_LINE_ENDINGS))
        assertTrue(ruleSet.contains(IgcLintRule.I_RECORD_EXTENSION_COUNT_MATCHES_DECLARATION))
        assertTrue(ruleSet.contains(IgcLintRule.I_RECORD_EXTENSION_BYTE_RANGE_VALID))
        assertTrue(ruleSet.contains(IgcLintRule.I_RECORD_EXTENSION_BYTE_RANGE_NON_OVERLAPPING))
    }
}
