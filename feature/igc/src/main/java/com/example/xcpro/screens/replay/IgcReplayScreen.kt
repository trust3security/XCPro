package com.example.xcpro.screens.replay

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.replay.IgcReplayUiState
import com.example.xcpro.replay.IgcReplayViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IgcReplayScreen(
    navController: NavHostController,
    viewModel: IgcReplayViewModel = hiltViewModel(),
    onNavigateBack: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navigateBackAction = onNavigateBack ?: {
        val popped = navController.popBackStack(route = "map", inclusive = false)
        if (!popped) {
            navController.navigate("map") {
                launchSingleTop = true
            }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                IgcReplayViewModel.IgcReplayUiEvent.NavigateBackToMap -> navigateBackAction()
            }
        }
    }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onFileSelected(documentRefForUri(context, uri))
        } else {
            viewModel.onFileSelected(null)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("IGC Replay") },
                navigationIcon = {
                    IconButton(onClick = navigateBackAction) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FileSelectionCard(uiState, onSelect = { filePickerLauncher.launch(arrayOf("*/*")) })

            SpeedSelector(uiState = uiState, onSpeedChanged = viewModel::setSpeed)

            TimelineCard(
                uiState = uiState,
                onSeek = { fraction -> viewModel.seekTo(fraction) }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = viewModel::startReplay,
                    enabled = uiState.selectedDocument != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Start Replay")
                }
                Button(
                    onClick = viewModel::stopReplay,
                    enabled = uiState.isReplayLoaded,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Stop")
                }
            }

            StatusCard(uiState)
        }
    }
}

@Composable
private fun FileSelectionCard(
    uiState: IgcReplayUiState,
    onSelect: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Selected file", fontWeight = FontWeight.Bold)
            Text(uiState.selectedFileName ?: "No file selected")
            Button(onClick = onSelect) {
                Icon(Icons.Default.Folder, contentDescription = null)
                Text("  Choose IGC File")
            }
        }
    }
}

@Composable
private fun SpeedSelector(
    uiState: IgcReplayUiState,
    onSpeedChanged: (Double) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Replay speed", fontWeight = FontWeight.Bold)
            Text("${"%.1f".format(uiState.speedMultiplier)} x")
            Slider(
                value = uiState.speedMultiplier.toFloat().coerceIn(1f, 20f),
                onValueChange = { onSpeedChanged(it.toDouble()) },
                valueRange = 1f..20f
            )
        }
    }
}

@Composable
private fun TimelineCard(
    uiState: IgcReplayUiState,
    onSeek: (Float) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val elapsed = formatDuration(uiState.elapsedMillis)
            val total = formatDuration(uiState.durationMillis)
            var localProgress by remember { mutableStateOf(uiState.progressFraction.coerceIn(0f, 1f)) }
            LaunchedEffect(uiState.progressFraction) {
                localProgress = uiState.progressFraction.coerceIn(0f, 1f)
            }
            Text("Timeline", fontWeight = FontWeight.Bold)
            Text("$elapsed / $total")
            Slider(
                value = localProgress,
                onValueChange = { localProgress = it.coerceIn(0f, 1f) },
                onValueChangeFinished = { onSeek(localProgress) },
                enabled = uiState.selectedDocument != null
            )
        }
    }
}

private fun formatDuration(millis: Long): String {
    if (millis <= 0L) return "00:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

private fun resolveDisplayName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
    }
}

private fun documentRefForUri(context: Context, uri: Uri): DocumentRef =
    DocumentRef(uri = uri.toString(), displayName = resolveDisplayName(context, uri))

@Composable
private fun StatusCard(uiState: IgcReplayUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(uiState.statusMessage)
            uiState.errorMessage?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
