package com.trust3.xcpro.weglide.ui

import com.trust3.xcpro.weglide.domain.WeGlidePostFlightUploadPrompt

data class WeGlideUploadPromptUiState(
    val fileName: String,
    val profileName: String?,
    val aircraftName: String
)

fun WeGlidePostFlightUploadPrompt.toUiState(): WeGlideUploadPromptUiState =
    WeGlideUploadPromptUiState(
        fileName = fileName,
        profileName = profileName,
        aircraftName = aircraftName
    )
