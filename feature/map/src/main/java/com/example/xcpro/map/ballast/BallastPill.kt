package com.example.xcpro.map.ballast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun BallastPill(
    state: BallastUiState,
    onCommand: (BallastCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when (state.mode) {
        BallastMode.Filling -> Color(0xFF2E7D32).copy(alpha = 0.9f)
        BallastMode.Draining -> Color(0xFFC62828).copy(alpha = 0.9f)
        BallastMode.Idle -> Color(0xFF0A84FF).copy(alpha = 0.85f)
    }
    val fillFraction by animateFloatAsState(
        targetValue = state.snapshot.ratio.coerceIn(0f, 1f),
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 500,
            easing = FastOutSlowInEasing
        ),
        label = "ballastFill"
    )

    val statusText = when (state.mode) {
        BallastMode.Filling -> "filling"
        BallastMode.Draining -> "draining"
        BallastMode.Idle -> "steady"
    }
    val percent = (state.snapshot.ratio * 100).roundToInt().coerceIn(0, 100)
    val contentDescription =
        "Water ballast $percent percent, $statusText"

    val pillShape = RoundedCornerShape(18.dp)
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    val backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)

    Column(
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(120.dp)
                .clip(pillShape)
                .background(backgroundColor)
                .border(width = 1.dp, color = borderColor, shape = pillShape),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (state.snapshot.hasBallast) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fillFraction)
                        .background(fillColor)
                )
            }

            val kgText = if (state.snapshot.hasBallast) {
                "${state.snapshot.currentKg.roundToInt()} kg"
            } else {
                "N/A"
            }
            Text(
                text = kgText,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 4.dp),
                textAlign = TextAlign.Center
            )
        }

        AnimatedVisibility(
            visible = state.isAnimating,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val remainingSeconds = (state.remainingMillis / 1000).coerceAtLeast(0)
            val minutes = remainingSeconds / 60
            val seconds = remainingSeconds % 60
            Text(
                text = String.format(Locale.getDefault(), "%d:%02d", minutes, seconds),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

