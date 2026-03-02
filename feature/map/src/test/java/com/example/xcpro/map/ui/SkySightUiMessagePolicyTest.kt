package com.example.xcpro.map.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SkySightUiMessagePolicyTest {

    @Test
    fun resolveSkySightUiMessages_filtersWarningsDuplicatedInErrors() {
        val resolved = resolveSkySightUiMessages(
            repositoryWarningMessage = "wind tile failed",
            regionCoverageWarningMessage = "Outside coverage",
            runtimeWarningMessage = null,
            repositoryErrorMessage = "wind tile failed",
            runtimeErrorMessage = null
        )

        assertEquals("Outside coverage", resolved.warningMessage)
        assertEquals("wind tile failed", resolved.errorMessage)
    }

    @Test
    fun resolveSkySightUiMessages_splitsPipeSeparatedAndDeduplicatesCaseInsensitively() {
        val resolved = resolveSkySightUiMessages(
            repositoryWarningMessage = "Fallback engaged | fallback engaged",
            regionCoverageWarningMessage = null,
            runtimeWarningMessage = " Map center warning ",
            repositoryErrorMessage = "SATELLITE APPLY FAILED | satellite apply failed",
            runtimeErrorMessage = null
        )

        assertEquals("Fallback engaged | Map center warning", resolved.warningMessage)
        assertEquals("SATELLITE APPLY FAILED", resolved.errorMessage)
    }

    @Test
    fun resolveSkySightUiMessages_returnsNullWhenNoMessages() {
        val resolved = resolveSkySightUiMessages(
            repositoryWarningMessage = null,
            regionCoverageWarningMessage = null,
            runtimeWarningMessage = null,
            repositoryErrorMessage = null,
            runtimeErrorMessage = null
        )

        assertNull(resolved.warningMessage)
        assertNull(resolved.errorMessage)
    }
}
