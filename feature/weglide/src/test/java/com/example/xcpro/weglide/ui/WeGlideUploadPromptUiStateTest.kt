package com.example.xcpro.weglide.ui

import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.weglide.domain.WeGlideFinalizedFlightUploadRequest
import com.example.xcpro.weglide.domain.WeGlidePostFlightUploadPrompt
import org.junit.Assert.assertEquals
import org.junit.Test

class WeGlideUploadPromptUiStateTest {

    @Test
    fun toUiState_keepsOnlyDialogRenderFields() {
        val prompt = WeGlidePostFlightUploadPrompt(
            request = WeGlideFinalizedFlightUploadRequest(
                localFlightId = "flight-123",
                document = DocumentRef(uri = "content://igc/flight.igc")
            ),
            profileId = "profile-1",
            profileName = "Club",
            aircraftName = "ASW 27",
            fileName = "flight.igc"
        )

        assertEquals(
            WeGlideUploadPromptUiState(
                fileName = "flight.igc",
                profileName = "Club",
                aircraftName = "ASW 27"
            ),
            prompt.toUiState()
        )
    }
}
