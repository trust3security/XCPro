package com.example.xcpro.screens.replay

import android.content.Context
import android.net.Uri
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.xcpro.replay.IgcReplayUiState
import com.example.xcpro.replay.IgcReplayViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IgcReplayScreen(
    navController: NavHostController,
    viewModel: IgcReplayViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                IgcReplayViewModel.IgcReplayUiEvent.NavigateBackToMap -> {
                    val popped = navController.popBackStack(route = "map", inclusive = false)
                    if (!popped) {
                        navController.navigate("map") {
                            launchSingleTop = true
                        }
                    }
                }
            }
        }
    }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = resolveDisplayName(context, uri)
            viewModel.onFileSelected(uri, name)
        } else {
            viewModel.onFileSelected(null, null)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("IGC Replay") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = viewModel::startReplay,
                    enabled = uiState.selectedUri != null,
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
                value = uiState.speedMultiplier.toFloat(),
                onValueChange = { onSpeedChanged(it.toDouble()) },
                valueRange = 1f..10f
            )
        }
    }
}

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

private fun resolveDisplayName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex >= 0) {
            cursor.getString(nameIndex)
        } else {
            null
        }
    }
}
