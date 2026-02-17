package com.example.xcpro.tasks.aat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AnimatedTargetPointIndicator(
    isVisible: Boolean,
    hasUnsavedChanges: Boolean,
    modifier: Modifier = Modifier
) {
    val pulseAnimation by rememberInfiniteTransition().animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        )
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .scale(if (hasUnsavedChanges) pulseAnimation else 1f)
                .background(
                    color = if (hasUnsavedChanges) {
                        Color(0xFF388E3C).copy(alpha = 0.2f)
                    } else {
                        Color(0xFF4CAF50).copy(alpha = 0.1f)
                    },
                    shape = CircleShape
                )
                .border(
                    width = 2.dp,
                    color = if (hasUnsavedChanges) Color(0xFF388E3C) else Color(0xFF4CAF50),
                    shape = CircleShape
                )
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .align(Alignment.Center)
                    .background(Color.White, CircleShape)
            )
        }
    }
}

@Composable
fun AreaBoundaryHighlight(
    isEditMode: Boolean,
    modifier: Modifier = Modifier
) {
    val highlightAnimation by rememberInfiniteTransition().animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        )
    )

    AnimatedVisibility(
        visible = isEditMode,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(highlightAnimation)
                .border(
                    width = 3.dp,
                    color = Color(0xFF388E3C),
                    shape = CircleShape
                )
        )
    }
}

@Composable
fun EditModeStatusIndicator(
    isEditMode: Boolean,
    areaName: String,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isEditMode,
        enter = slideInHorizontally(
            initialOffsetX = { -it },
            animationSpec = spring()
        ) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF388E3C).copy(alpha = 0.9f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val pulseScale by rememberInfiniteTransition().animateFloat(
                    initialValue = 0.8f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000),
                        repeatMode = RepeatMode.Reverse
                    )
                )

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .scale(pulseScale)
                        .background(Color.White, CircleShape)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "EDITING: $areaName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            }
        }
    }
}
