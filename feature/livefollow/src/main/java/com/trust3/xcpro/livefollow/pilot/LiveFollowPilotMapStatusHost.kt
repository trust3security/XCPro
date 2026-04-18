package com.trust3.xcpro.livefollow.pilot

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lens
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon

private val LiveFollowPilotIndicatorGreen = Color(0xFF16A34A)
private val LiveFollowPilotIndicatorRed = Color(0xFFDC2626)
private val LiveFollowIndicatorPadding = 0.9.dp
private val LiveFollowIndicatorDotSize = 10.8.dp

@Composable
fun BoxScope.LiveFollowPilotMapStatusHost(
    visible: Boolean,
    topEndAdditionalOffset: Dp = 0.dp,
    uiState: LiveFollowPilotUiState,
    onStartSharing: () -> Unit,
    onStopSharing: () -> Unit
) {
    if (!visible) return

    val infiniteTransition = rememberInfiniteTransition(label = "pilot_status_indicator")
    val pulsingScale = infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pilot_status_pulse"
    )
    val flashingAlpha = infiniteTransition.animateFloat(
        initialValue = 0.22f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 480),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pilot_status_flash"
    )
    var expanded by remember { mutableStateOf(false) }
    val visuals = pilotIndicatorVisuals(uiState.shareIndicatorState)
    val supportingMessage = remember(
        uiState.shareIndicatorState,
        uiState.statusMessage,
        uiState.lastError
    ) {
        pilotStatusSurfaceMessage(uiState)
    }
    val iconScale = if (uiState.shareIndicatorState == LiveFollowPilotShareIndicatorState.STARTING) {
        pulsingScale.value
    } else {
        1f
    }
    val iconAlpha = if (uiState.shareIndicatorState == LiveFollowPilotShareIndicatorState.FAILED) {
        flashingAlpha.value
    } else {
        1f
    }

    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
                    12.dp +
                    topEndAdditionalOffset,
                end = 16.dp
            )
    ) {
        Box(
            modifier = Modifier
                .semantics { contentDescription = visuals.contentDescription }
                .clickable(role = Role.Button) { expanded = true }
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                tonalElevation = 4.dp,
                shadowElevation = 4.dp
            ) {
                Box(
                    modifier = Modifier.padding(LiveFollowIndicatorPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = visuals.icon,
                        contentDescription = null,
                        tint = visuals.color,
                        modifier = Modifier
                            .size(LiveFollowIndicatorDotSize)
                            .scale(iconScale)
                            .alpha(iconAlpha)
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 220.dp, max = 260.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = visuals.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = supportingMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (uiState.shareIndicatorState == LiveFollowPilotShareIndicatorState.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Visibility",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = uiState.currentVisibilityLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (uiState.shareIndicatorState == LiveFollowPilotShareIndicatorState.LIVE) {
                    HorizontalDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Share code",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = uiState.shareCode ?: "Unavailable",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                when (uiState.shareIndicatorState) {
                    LiveFollowPilotShareIndicatorState.LIVE -> {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    expanded = false
                                    onStopSharing()
                                },
                                enabled = !uiState.isBusy
                            ) {
                                Text("Stop sharing")
                            }
                        }
                    }

                    LiveFollowPilotShareIndicatorState.FAILED -> {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    expanded = false
                                    onStartSharing()
                                },
                                enabled = !uiState.isBusy
                            ) {
                                Text("Retry")
                            }
                        }
                    }

                    LiveFollowPilotShareIndicatorState.STOPPED -> {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    expanded = false
                                    onStartSharing()
                                },
                                enabled = uiState.canStartSharing && !uiState.isBusy
                            ) {
                                Text("Start sharing")
                            }
                        }
                    }

                    LiveFollowPilotShareIndicatorState.STARTING -> Unit
                }
            }
        }
    }
}

private data class PilotIndicatorVisuals(
    val icon: ImageVector,
    val color: Color,
    val contentDescription: String,
    val title: String
)

private fun pilotStatusSurfaceMessage(
    uiState: LiveFollowPilotUiState
): String {
    return when (uiState.shareIndicatorState) {
        LiveFollowPilotShareIndicatorState.STARTING -> uiState.statusMessage
        LiveFollowPilotShareIndicatorState.LIVE -> "LiveFollow sharing is active."
        LiveFollowPilotShareIndicatorState.FAILED -> uiState.lastError ?: uiState.statusMessage
        LiveFollowPilotShareIndicatorState.STOPPED -> {
            if (uiState.canStartSharing) {
                "LiveFollow sharing is stopped."
            } else {
                uiState.statusMessage
            }
        }
    }
}

private fun pilotIndicatorVisuals(
    state: LiveFollowPilotShareIndicatorState
): PilotIndicatorVisuals {
    return when (state) {
        LiveFollowPilotShareIndicatorState.STARTING -> PilotIndicatorVisuals(
            icon = Icons.Filled.Lens,
            color = LiveFollowPilotIndicatorGreen,
            contentDescription = "LiveFollow sharing starting",
            title = "Starting sharing"
        )

        LiveFollowPilotShareIndicatorState.LIVE -> PilotIndicatorVisuals(
            icon = Icons.Filled.Lens,
            color = LiveFollowPilotIndicatorGreen,
            contentDescription = "LiveFollow sharing live",
            title = "Sharing live"
        )

        LiveFollowPilotShareIndicatorState.FAILED -> PilotIndicatorVisuals(
            icon = Icons.Filled.Lens,
            color = LiveFollowPilotIndicatorRed,
            contentDescription = "LiveFollow sharing failed",
            title = "Sharing failed"
        )

        LiveFollowPilotShareIndicatorState.STOPPED -> PilotIndicatorVisuals(
            icon = Icons.Filled.Lens,
            color = LiveFollowPilotIndicatorRed,
            contentDescription = "LiveFollow sharing stopped",
            title = "Not sharing"
        )
    }
}
