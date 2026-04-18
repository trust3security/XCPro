package com.trust3.xcpro.screens.replay
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.trust3.xcpro.igc.data.IgcLogEntry
import com.trust3.xcpro.igc.ui.IgcFilesEvent
import com.trust3.xcpro.igc.ui.IgcFilesViewModel
import com.trust3.xcpro.igc.ui.IGC_FILES_LABEL
import com.trust3.xcpro.igc.usecase.IgcFilesSort
import com.trust3.xcpro.igc.usecase.IgcShareMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IgcFilesScreen(
    navController: NavHostController,
    viewModel: IgcFilesViewModel = hiltViewModel(),
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
    val copyToLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.fai.igc")
    ) { uri ->
        viewModel.onCopyDestinationSelected(uri?.toString())
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is IgcFilesEvent.ShowMessage -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is IgcFilesEvent.Share -> {
                    val result = launchIgcShareChooser(context, event.request)
                    if (result.isFailure) {
                        viewModel.onShareLaunchFailed(
                            displayName = event.displayName,
                            error = result.exceptionOrNull()
                        )
                    }
                }
                is IgcFilesEvent.LaunchCopyTo -> copyToLauncher.launch(event.suggestedFileName)
                is IgcFilesEvent.CopyMetadata -> {
                    copyMetadataToClipboard(context, event.text)
                    Toast.makeText(context, "Metadata copied", Toast.LENGTH_SHORT).show()
                }
                IgcFilesEvent.NavigateBackToMap -> navigateBackAction()
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(IGC_FILES_LABEL) },
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChanged,
                label = { Text("Search IGC files") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            SortRow(
                selected = uiState.sort,
                onSortSelected = viewModel::onSortChanged
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::refresh) {
                    Text("Refresh")
                }
            }

            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error
                )
            }

            uiState.latestDiagnostic?.let { diagnostic ->
                Text(
                    text = diagnostic.message,
                    color = MaterialTheme.colorScheme.error
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.visibleEntries, key = { it.document.uri }) { entry ->
                    IgcFileRow(
                        entry = entry,
                        onReplayOpen = { viewModel.replayOpen(entry) },
                        onShare = { viewModel.share(entry, IgcShareMode.SHARE) },
                        onEmail = { viewModel.share(entry, IgcShareMode.EMAIL) },
                        onUpload = { viewModel.share(entry, IgcShareMode.UPLOAD) },
                        onCopyTo = { viewModel.copyTo(entry) },
                        onCopyMetadata = { viewModel.copyMetadata(entry) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SortRow(
    selected: IgcFilesSort,
    onSortSelected: (IgcFilesSort) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = { onSortSelected(IgcFilesSort.DATE_DESC) }) {
            Text(if (selected == IgcFilesSort.DATE_DESC) "Date*" else "Date")
        }
        Button(onClick = { onSortSelected(IgcFilesSort.NAME_ASC) }) {
            Text(if (selected == IgcFilesSort.NAME_ASC) "Name*" else "Name")
        }
        Button(onClick = { onSortSelected(IgcFilesSort.SIZE_DESC) }) {
            Text(if (selected == IgcFilesSort.SIZE_DESC) "Size*" else "Size")
        }
        Button(onClick = { onSortSelected(IgcFilesSort.DURATION_DESC) }) {
            Text(if (selected == IgcFilesSort.DURATION_DESC) "Dur*" else "Dur")
        }
    }
}

@Composable
private fun IgcFileRow(
    entry: IgcLogEntry,
    onReplayOpen: () -> Unit,
    onShare: () -> Unit,
    onEmail: () -> Unit,
    onUpload: () -> Unit,
    onCopyTo: () -> Unit,
    onCopyMetadata: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(entry.displayName, fontWeight = FontWeight.SemiBold)
        Text(
            text = "Size ${entry.sizeBytes} B | UTC ${entry.utcDate ?: "unknown"} | Duration ${entry.durationSeconds ?: "unknown"}",
            style = MaterialTheme.typography.bodySmall
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onReplayOpen) { Text("Replay") }
            Button(onClick = onShare) { Text("Share") }
            Button(onClick = onEmail) { Text("Email") }
            Button(onClick = onUpload) { Text("Upload") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onCopyTo) { Text("Copy To") }
            Button(onClick = onCopyMetadata) { Text("Copy Meta") }
        }
    }
}

private fun copyMetadataToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(
        ClipData.newPlainText("IGC metadata", text)
    )
}
