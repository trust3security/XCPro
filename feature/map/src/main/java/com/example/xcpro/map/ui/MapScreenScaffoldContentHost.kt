package com.example.xcpro.map.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import com.example.xcpro.weglide.ui.WeGlideUploadPromptUiState

@Composable
internal fun BoxScope.MapScreenScaffoldContentHost(
    inputs: MapScreenScaffoldInputs,
    weGlideUploadPrompt: WeGlideUploadPromptUiState?,
    onConfirmWeGlideUploadPrompt: () -> Unit,
    onDismissWeGlideUploadPrompt: () -> Unit
) {
    MapScreenContent(
        inputs = inputs.content,
        weGlideUploadPrompt = weGlideUploadPrompt,
        onConfirmWeGlideUploadPrompt = onConfirmWeGlideUploadPrompt,
        onDismissWeGlideUploadPrompt = onDismissWeGlideUploadPrompt,
    )
}
