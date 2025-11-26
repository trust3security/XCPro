@file:Suppress("DEPRECATION")

package com.example.xcpro.map.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.rememberSwipeableState
import androidx.compose.material.swipeable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.xcpro.replay.IgcReplayController
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Suppress("DEPRECATION") // Material swipeable APIs are deprecated; migrate to anchoredDraggable later.
@Composable
internal fun BoxScope.ReplayControlsSheet(
    session: IgcReplayController.SessionState,
    modifier: Modifier = Modifier,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSpeedChanged: (Double) -> Unit,
    onSeek: (Float) -> Unit
) {
    val scope = rememberCoroutineScope()
    var sheetHeightPx by remember { mutableStateOf(1f) }
    val swipeableState = rememberSwipeableState(initialValue = ReplaySheetValue.Hidden)
    val anchors = remember(sheetHeightPx) {
        if (sheetHeightPx <= 0f) emptyMap()
        else mapOf(
            0f to ReplaySheetValue.Visible,
            sheetHeightPx to ReplaySheetValue.Hidden
        )
    }

    LaunchedEffect(session.selection) {
        if (session.hasSelection && anchors.isNotEmpty()) {
            swipeableState.animateTo(ReplaySheetValue.Visible)
        } else {
            swipeableState.snapTo(ReplaySheetValue.Hidden)
        }
    }

    val rawOffset = swipeableState.offset.value
    val offsetPx = if (rawOffset.isNaN()) {
        if (session.hasSelection) 0f else sheetHeightPx
    } else {
        rawOffset
    }

    val cardModifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 24.dp)
        .offset {
            IntOffset(0, offsetPx.coerceIn(0f, sheetHeightPx).roundToInt())
        }
        .let { base ->
            if (anchors.isEmpty()) {
                base
            } else {
                base
                    .swipeable(
                        state = swipeableState,
                        anchors = anchors,
                        thresholds = { _, _ -> FractionalThreshold(0.3f) },
                        orientation = Orientation.Vertical
                    )
                    .onGloballyPositioned { coords ->
                        sheetHeightPx = coords.size.height.toFloat()
                    }
            }
        }

    if (session.hasSelection || swipeableState.currentValue == ReplaySheetValue.Visible) {
        Card(
            modifier = cardModifier,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            ReplayControlsContent(
                state = session,
                onPlayPause = onPlayPause,
                onStop = onStop,
                onSpeedChanged = onSpeedChanged,
                onSeek = onSeek
            )
        }
    }

    if (session.hasSelection && swipeableState.currentValue == ReplaySheetValue.Hidden) {
        AssistChip(
            onClick = { scope.launch { swipeableState.animateTo(ReplaySheetValue.Visible) } },
            label = { Text("Replay controls") },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}

@Composable
internal fun ReplayControlsContent(
    state: IgcReplayController.SessionState,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSpeedChanged: (Double) -> Unit,
    onSeek: (Float) -> Unit
) {
    if (!state.hasSelection) return
    val isPlaying = state.status == IgcReplayController.SessionStatus.PLAYING
    val title = state.selection?.displayName ?: "IGC Replay"
    val elapsed = state.elapsedMillis
    val duration = state.durationMillis
    val speed = state.speedMultiplier
    var localProgress by remember { mutableStateOf(state.progressFraction) }

    // Keep slider thumb in sync with playback updates
    LaunchedEffect(state.progressFraction) { localProgress = state.progressFraction }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "${formatDuration(elapsed)} / ${formatDuration(duration)} · ${"%.1f".format(speed)}x",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(text = "Timeline", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = localProgress,
            onValueChange = { localProgress = it },
            onValueChangeFinished = { onSeek(localProgress.coerceIn(0f, 1f)) },
            modifier = Modifier.fillMaxWidth()
        )
        Text(text = "Speed", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = speed.toFloat(),
            onValueChange = { onSpeedChanged(it.toDouble()) },
            valueRange = 1f..10f,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(onClick = onPlayPause) {
                if (isPlaying) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = "Pause replay"
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play replay"
                    )
                }
            }
            IconButton(onClick = onStop) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop replay"
                )
            }
        }
    }
}

private enum class ReplaySheetValue {
    Hidden,
    Visible
}

internal fun formatDuration(millis: Long): String {
    if (millis <= 0L) return "00:00"
    val totalSeconds = millis / 1000
    val seconds = (totalSeconds % 60).toInt()
    val minutes = ((totalSeconds / 60) % 60).toInt()
    val hours = (totalSeconds / 3600).toInt()
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

