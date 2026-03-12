package com.example.xcpro.weglide.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WeGlideUploadPromptDialogHostTest {

    @Test
    fun buildPromptMessage_includesProfileWhenPresent() {
        val prompt = WeGlideUploadPromptUiState(
            fileName = "flight.igc",
            profileName = "Club Profile",
            aircraftName = "LS8"
        )

        val message = buildWeGlideUploadPromptMessage(prompt)

        assertEquals(
            "Upload flight.igc to WeGlide using LS8?\n\nProfile: Club Profile",
            message
        )
    }

    @Test
    fun buildPromptMessage_omitsBlankProfile() {
        val prompt = WeGlideUploadPromptUiState(
            fileName = "flight.igc",
            profileName = " ",
            aircraftName = "Discus"
        )

        val message = buildWeGlideUploadPromptMessage(prompt)

        assertEquals(
            "Upload flight.igc to WeGlide using Discus?",
            message
        )
    }
}
