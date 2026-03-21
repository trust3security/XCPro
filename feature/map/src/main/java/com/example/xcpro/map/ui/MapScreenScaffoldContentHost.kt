package com.example.xcpro.map.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.xcpro.livefollow.LiveFollowRoutes
import com.example.xcpro.map.BuildConfig
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
    // Temporary debug launcher to keep MapScreen as a thin entry point while pilot flow testing is active.
    if (BuildConfig.DEBUG) {
        TemporaryLiveFollowPilotLauncher(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            onOpen = {
                inputs.scaffold.navController.navigate(LiveFollowRoutes.PILOT)
            }
        )
    }
}

@Composable
private fun TemporaryLiveFollowPilotLauncher(
    modifier: Modifier = Modifier,
    onOpen: () -> Unit
) {
    Card(
        modifier = modifier.widthIn(max = 220.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = "LiveFollow Pilot",
                style = MaterialTheme.typography.titleMedium
            )
            Button(onClick = onOpen) {
                Text(text = "Open")
            }
        }
    }
}
