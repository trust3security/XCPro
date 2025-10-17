package com.example.dfcards.dfcards

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun EnhancedFlightDataCard(
    flightData: FlightData,
    cardWidth: Float,
    cardHeight: Float,
    isEditMode: Boolean = false,
    isLiveData: Boolean = false,
    modifier: Modifier = Modifier
) {
    val stableFontSizes = remember(cardWidth, cardHeight) {
        calculateStableFontSizes(cardWidth, cardHeight)
    }

    val editModeAlpha by animateFloatAsState(
        targetValue = if (isEditMode) 0.9f else 1f,
        animationSpec = tween(300),
        label = "edit_alpha"
    )

    val primaryScale by animateFloatAsState(
        targetValue = if (isEditMode) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "primary_scale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RectangleShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isEditMode) {
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                        )
                    } else {
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surface
                        )
                    }
                )
            )
            .border(
                width = 1.dp,
                color = if (isEditMode) Color.Red else Color.Black,
                shape = RectangleShape
            )
    ) {
        // Top label
        Text(
            text = flightData.label,
            fontSize = stableFontSizes.labelSize.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f * editModeAlpha),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp)
                .align(Alignment.TopCenter)
        )

        // ✅ ENHANCED: Centered primary value with formatted text for number + unit
        Text(
            text = buildAnnotatedString {
                val parts = flightData.primaryValue.split(" ")
                if (parts.size >= 2) {
                    // Large number part (1250, 85, +2.4, etc.)
                    withStyle(style = SpanStyle(
                        fontSize = stableFontSizes.primarySize.sp,
                        fontWeight = FontWeight.Bold
                    )) {
                        append(parts[0])  // ✅ "1250" in large font
                    }
                    append(" ")
                    // Smaller unit part (ft, kt, m/s, etc.)
                    withStyle(style = SpanStyle(
                        fontSize = (stableFontSizes.primarySize * 0.55f).sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black.copy(alpha = 0.6f)
                    )) {
                        append(parts.drop(1).joinToString(" "))  // ✅ "ft" in smaller font
                    }
                } else {
                    // No space found, show as-is (for values like "25:1", "14:23", etc.)
                    withStyle(style = SpanStyle(
                        fontSize = stableFontSizes.primarySize.sp,
                        fontWeight = FontWeight.Bold
                    )) {
                        append(flightData.primaryValue)
                    }
                }
            },
            color = Color.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .graphicsLayer {
                    scaleX = primaryScale
                    scaleY = primaryScale
                }
        )

        // Bottom secondary value (GPS, QNH, m/s, etc.)
        flightData.secondaryValue?.let {
            Text(
                text = it,
                fontSize = stableFontSizes.secondarySize.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f * editModeAlpha),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp)
                    .align(Alignment.BottomCenter)
            )
        }
    }
}

private data class StableFontSizes(
    val labelSize: Float,
    val primarySize: Float,
    val secondarySize: Float
)

private fun calculateStableFontSizes(
    cardWidth: Float,
    cardHeight: Float
): StableFontSizes {
    // Compute a scale factor from current size vs. a base size.
    // Keep a reasonable lower bound so tiny cards remain readable,
    // but remove the restrictive upper clamp so text can grow with the card.
    val heightRatio = cardHeight / 80f
    val widthRatio = cardWidth / 120f
    val scale = min(heightRatio, widthRatio).coerceAtLeast(0.4f)

    return StableFontSizes(
        labelSize = (5f * scale).coerceAtLeast(3f),
        primarySize = (10f * scale).coerceAtLeast(6f),
        secondarySize = (6f * scale).coerceAtLeast(4f)
    )
}
