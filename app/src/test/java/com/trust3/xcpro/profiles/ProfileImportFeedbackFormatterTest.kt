package com.trust3.xcpro.profiles

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileImportFeedbackFormatterTest {

    @Test
    fun formatProfileImportFeedback_successUsesPluralizedSummary() {
        val result = ProfileImportResult(
            requestedCount = 2,
            importedCount = 2,
            skippedCount = 0,
            failures = emptyList(),
            activeProfileBefore = "before",
            activeProfileAfter = "after"
        )

        val message = formatProfileImportFeedback(result)

        assertEquals("Imported 2 profiles", message)
    }

    @Test
    fun formatProfileImportFeedback_failureSummaryIncludesPreviewAndRemainder() {
        val failures = listOf(
            ProfileImportFailure(
                sourceName = "A",
                reason = ProfileImportFailureReason.INVALID_PROFILE,
                detail = "A invalid"
            ),
            ProfileImportFailure(
                sourceName = "B",
                reason = ProfileImportFailureReason.INVALID_PROFILE,
                detail = "B invalid"
            ),
            ProfileImportFailure(
                sourceName = "C",
                reason = ProfileImportFailureReason.INVALID_PROFILE,
                detail = "C invalid"
            )
        )
        val result = ProfileImportResult(
            requestedCount = 4,
            importedCount = 1,
            skippedCount = 3,
            failures = failures,
            activeProfileBefore = null,
            activeProfileAfter = null
        )

        val message = formatProfileImportFeedback(result)

        assertEquals(
            "Imported 1/4 profiles, skipped 3: A invalid; B invalid (+1 more)",
            message
        )
    }
}
