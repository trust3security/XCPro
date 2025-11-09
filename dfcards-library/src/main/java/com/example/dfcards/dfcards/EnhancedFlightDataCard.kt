package com.example.dfcards.dfcards

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

@Composable
fun EnhancedFlightDataCard(
    flightData: FlightData,
    cardWidth: Float,
    cardHeight: Float,
    isEditMode: Boolean = false,
    isLiveData: Boolean = false,
    modifier: Modifier = Modifier,
    visualStyle: CardVisualStyle
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

    val backgroundBrush = if (isEditMode) {
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
            )
        )
    } else {
        visualStyle.backgroundBrush
    }

    val borderWidth = if (isEditMode) 1.dp else visualStyle.borderWidth
    val borderColor = if (isEditMode) Color.Red else visualStyle.borderColor

    val baseModifier = modifier
        .fillMaxSize()
        .clip(RectangleShape)
        .background(brush = backgroundBrush)

    val borderedModifier = if (borderWidth > 0.dp) {
        baseModifier.border(
            width = borderWidth,
            color = borderColor,
            shape = RectangleShape
        )
    } else {
        baseModifier
    }

    Box(
        modifier = borderedModifier
    ) {
        Text(
            text = flightData.label,
            fontSize = stableFontSizes.labelSize.sp,
            fontWeight = FontWeight.Bold,
            color = visualStyle.labelColor.copy(alpha = 0.7f * editModeAlpha),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp)
                .align(Alignment.TopCenter)
        )

        Text(
            text = buildAnnotatedString {
                val primaryNumber = flightData.primaryValueNumber
                val primaryUnit = flightData.primaryValueUnit
                if (primaryNumber != null) {
                    withStyle(
                        style = SpanStyle(
                            fontSize = stableFontSizes.primarySize.sp,
                            fontWeight = FontWeight.Bold,
                            color = visualStyle.primaryColor
                        )
                    ) {
                        append(primaryNumber)
                    }
                    primaryUnit?.let { unit ->
                        append(" ")
                        withStyle(
                            style = SpanStyle(
                                fontSize = (stableFontSizes.primarySize * 0.55f).sp,
                                fontWeight = FontWeight.Medium,
                                color = visualStyle.unitColor
                            )
                        ) {
                            append(unit)
                        }
                    }
                } else {
                    withStyle(
                        style = SpanStyle(
                            fontSize = stableFontSizes.primarySize.sp,
                            fontWeight = FontWeight.Bold,
                            color = visualStyle.primaryColor
                        )
                    ) {
                        append(flightData.primaryValue)
                    }
                }
            },
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

        flightData.secondaryValue?.let {
            Text(
                text = it,
                fontSize = stableFontSizes.secondarySize.sp,
                fontWeight = FontWeight.Bold,
                color = visualStyle.secondaryColor.copy(alpha = 0.6f * editModeAlpha),
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
    val heightRatio = cardHeight / 80f
    val widthRatio = cardWidth / 120f
    val scale = min(heightRatio, widthRatio).coerceAtLeast(0.4f)

    return StableFontSizes(
        labelSize = (5f * scale).coerceAtLeast(3f),
        primarySize = (10f * scale).coerceAtLeast(6f),
        secondarySize = ((5f * scale) - 2f).coerceAtLeast(2.5f)
    )
}
