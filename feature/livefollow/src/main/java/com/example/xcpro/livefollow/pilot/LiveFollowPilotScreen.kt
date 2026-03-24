package com.example.xcpro.livefollow.pilot

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcpro.livefollow.data.session.LiveFollowSessionVisibility
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveFollowPilotScreen(
    onNavigateBack: () -> Unit,
    viewModel: LiveFollowPilotViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel, context) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is LiveFollowPilotEvent.CopyShareCode -> {
                    val clipboard = context.getSystemService(
                        Context.CLIPBOARD_SERVICE
                    ) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText(
                            "LiveFollow share code",
                            event.shareCode
                        )
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("LiveFollow Pilot") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = viewModel::startSharing,
                    enabled = uiState.canStartSharing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Start sharing")
                }
                Button(
                    onClick = viewModel::stopSharing,
                    enabled = uiState.canStopSharing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Stop sharing")
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Session status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(uiState.statusMessage)
                    StatusField(
                        label = "Signed-in live lane",
                        value = if (uiState.isSignedIn) "Available" else "Public-only fallback"
                    )
                    StatusField(
                        label = "Current visibility",
                        value = uiState.currentVisibilityLabel
                    )
                    Text(
                        text = uiState.currentVisibilitySummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    VisibilityOptionGroup(
                        selected = uiState.selectedVisibility,
                        canUsePrivateVisibility = uiState.canUsePrivateVisibility,
                        onSelected = viewModel::selectVisibility
                    )
                    if (!uiState.isSignedIn) {
                        Text(
                            text = "Sign in from Manage Account to use Off or Followers visibility. Signed-out sharing stays public.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (uiState.canUpdateVisibility) {
                        Button(
                            onClick = viewModel::updateVisibility,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Apply visibility")
                        }
                    }
                    StatusField(label = "Lifecycle", value = uiState.lifecycleLabel)
                    StatusField(label = "Session ID", value = uiState.sessionId ?: "Unavailable")
                    uiState.shareCode?.let { shareCode ->
                        StatusField(label = "Share code", value = shareCode)
                        Button(
                            onClick = viewModel::copyShareCode,
                            enabled = uiState.canCopyShareCode,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Copy share code")
                        }
                    }
                    StatusField(
                        label = "Session transport",
                        value = uiState.sessionTransportLabel
                    )
                    StatusField(
                        label = "Replay block reason",
                        value = uiState.replayBlockReasonLabel
                    )
                    StatusField(
                        label = "Ownship identity",
                        value = uiState.ownshipIdentityLabel
                    )
                    StatusField(
                        label = "Ownship source",
                        value = uiState.ownshipSourceLabel
                    )
                    StatusField(
                        label = "Ownship quality",
                        value = uiState.ownshipQualityLabel
                    )
                    uiState.lastError?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VisibilityOptionGroup(
    selected: LiveFollowSessionVisibility,
    canUsePrivateVisibility: Boolean,
    onSelected: (LiveFollowSessionVisibility) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Live visibility",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        LiveFollowSessionVisibility.entries.forEach { option ->
            val enabled = when (option) {
                LiveFollowSessionVisibility.PUBLIC -> true
                LiveFollowSessionVisibility.FOLLOWERS,
                LiveFollowSessionVisibility.OFF -> canUsePrivateVisibility
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RadioButton(
                    selected = option == selected,
                    onClick = if (enabled) {
                        { onSelected(option) }
                    } else {
                        null
                    },
                    enabled = enabled
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = option.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = option.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusField(
    label: String,
    value: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
