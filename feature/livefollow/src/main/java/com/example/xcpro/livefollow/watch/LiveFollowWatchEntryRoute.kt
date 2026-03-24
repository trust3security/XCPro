package com.example.xcpro.livefollow.watch

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

private val WatchTelemetryStripBottomPadding = 92.dp

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

@Composable
fun LiveFollowWatchShareEntryRoute(
    navController: NavHostController,
    rawShareCode: String?,
    onNavigateToMap: (() -> Unit)? = null
) {
    val mapEntry = remember(navController) {
        navController.getBackStackEntry(LiveFollowRoutes.MAP_ROUTE)
    }
    val viewModel: LiveFollowWatchViewModel = hiltViewModel(mapEntry)

    LaunchedEffect(rawShareCode) {
        viewModel.handleWatchShareEntry(rawShareCode)
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
        Text("Opening LiveFollow share-code watch...")
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
    uiState: LiveFollowWatchUiState
) {
    if (!uiState.visible) return

    if (shouldShowTelemetryStrip(uiState)) {
        WatchTelemetryStrip(
            uiState = uiState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues()
                        .calculateBottomPadding() + WatchTelemetryStripBottomPadding
                )
                .widthIn(max = 520.dp)
        )
    }
}

@Composable
private fun WatchTelemetryStrip(
    uiState: LiveFollowWatchUiState,
    modifier: Modifier = Modifier
) {
    val telemetryFields = buildLiveFollowWatchTelemetryFields(uiState)
    val title = uiState.aircraftLabel
        ?: uiState.shareCode
        ?: uiState.sessionId
        ?: return
    if (telemetryFields.isEmpty()) return

    Card(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                telemetryFields.forEach { field ->
                    WatchTelemetryChip(
                        label = field.label,
                        value = field.value
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchTelemetryChip(
    label: String,
    value: String
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 92.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

private fun shouldShowTelemetryStrip(
    uiState: LiveFollowWatchUiState
): Boolean {
    if (!uiState.visible) return false
    val hasTitle = uiState.aircraftLabel != null ||
        uiState.shareCode != null ||
        uiState.sessionId != null
    val hasTelemetry = buildLiveFollowWatchTelemetryFields(uiState).isNotEmpty()
    return hasTitle && hasTelemetry
}
