package com.trust3.xcpro.map.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import com.trust3.xcpro.weglide.ui.WeGlideUploadPromptUiState

@Composable
internal fun BoxScope.MapScreenScaffoldContentHost(
    inputs: MapScreenContentInputs,
    weGlideUploadPrompt: WeGlideUploadPromptUiState?,
    onConfirmWeGlideUploadPrompt: () -> Unit,
    onDismissWeGlideUploadPrompt: () -> Unit
) {
    MapScreenContent(
        inputs = inputs,
        weGlideUploadPrompt = weGlideUploadPrompt,
        onConfirmWeGlideUploadPrompt = onConfirmWeGlideUploadPrompt,
        onDismissWeGlideUploadPrompt = onDismissWeGlideUploadPrompt,
    )
}
