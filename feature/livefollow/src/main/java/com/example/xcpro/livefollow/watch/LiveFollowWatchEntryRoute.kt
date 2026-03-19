package com.example.xcpro.livefollow.watch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.xcpro.livefollow.LiveFollowRoutes

@Composable
fun LiveFollowWatchEntryRoute(
    navController: NavHostController,
    rawSessionId: String?,
    onNavigateToMap: (() -> Unit)? = null
) {
    val mapEntry = remember(navController) {
        navController.getBackStackEntry(LiveFollowRoutes.MAP_ROUTE)
    }
    val viewModel: LiveFollowWatchViewModel = hiltViewModel(mapEntry)

    LaunchedEffect(rawSessionId) {
        viewModel.handleWatchEntry(rawSessionId)
        if (onNavigateToMap != null) {
            onNavigateToMap()
        } else {
            handoffLiveFollowWatchToMap(navController)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Text("Opening LiveFollow watch...")
    }
}

suspend fun handoffLiveFollowWatchToMap(
    navController: NavHostController
) {
    val poppedToMap = navController.popBackStack(LiveFollowRoutes.MAP_ROUTE, inclusive = false)
    if (!poppedToMap) {
        navController.navigate(LiveFollowRoutes.MAP_ROUTE) {
            launchSingleTop = true
        }
    }
}

@Composable
fun BoxScope.LiveFollowWatchMapHost(
    uiState: LiveFollowWatchUiState,
    taskAttachmentMessage: String?,
    onStopWatching: () -> Unit,
    onDismissMessage: () -> Unit
) {
    if (!uiState.visible) return

    Card(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = uiState.headline,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = uiState.detail,
                style = MaterialTheme.typography.bodyMedium
            )
            WatchField(label = "Session", value = uiState.sessionId ?: "Unavailable")
            WatchField(label = "Lifecycle", value = uiState.lifecycleLabel)
            WatchField(label = "Session transport", value = uiState.sessionTransportLabel)
            WatchField(label = "Source", value = uiState.sourceLabel)
            WatchField(label = "State", value = uiState.stateLabel)
            WatchField(label = "Direct transport", value = uiState.directTransportLabel)
            uiState.aircraftLabel?.let { label ->
                WatchField(label = "Target", value = label)
            }
            uiState.aircraftIdentityLabel?.let { identity ->
                WatchField(label = "Identity", value = identity)
            }
            uiState.fixAgeLabel?.let { age ->
                WatchField(label = "Fix age", value = age)
            }
            taskAttachmentMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            uiState.directTransportMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            uiState.feedbackMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            when {
                uiState.canStopWatching -> {
                    Button(
                        onClick = onStopWatching,
                        enabled = !uiState.isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Stop watching")
                    }
                }

                uiState.canDismissMessage -> {
                    OutlinedButton(
                        onClick = onDismissMessage,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Dismiss")
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchField(
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
