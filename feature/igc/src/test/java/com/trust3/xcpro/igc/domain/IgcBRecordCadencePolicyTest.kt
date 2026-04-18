package com.trust3.xcpro.igc.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IgcBRecordCadencePolicyTest {

    @Test
    fun shouldEmit_firstSample_true() {
        val policy = IgcBRecordCadencePolicy()

        assertTrue(policy.shouldEmit(sampleWallTimeMs = 1_000L, lastEmissionWallTimeMs = null))
    }

    @Test
    fun shouldEmit_withinCadenceWindow_false() {
        val policy = IgcBRecordCadencePolicy(
            IgcBRecordCadencePolicy.Config(intervalSeconds = 2)
        )

        assertFalse(policy.shouldEmit(sampleWallTimeMs = 2_999L, lastEmissionWallTimeMs = 1_000L))
    }

    @Test
    fun shouldEmit_onCadenceBoundary_true() {
        val policy = IgcBRecordCadencePolicy(
            IgcBRecordCadencePolicy.Config(intervalSeconds = 2)
        )

        assertTrue(policy.shouldEmit(sampleWallTimeMs = 3_000L, lastEmissionWallTimeMs = 1_000L))
    }

    @Test
    fun shouldEmit_nonMonotonicSample_false() {
        val policy = IgcBRecordCadencePolicy()

        assertFalse(policy.shouldEmit(sampleWallTimeMs = 999L, lastEmissionWallTimeMs = 1_000L))
    }
}
