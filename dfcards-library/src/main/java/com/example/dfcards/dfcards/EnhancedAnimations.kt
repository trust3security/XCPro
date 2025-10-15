package com.example.dfcards.dfcards

import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay

@Composable
fun EnhancedCardAdditionAnimation(
    cardState: CardState,
    isNew: Boolean,
    fromFABPosition: Boolean = true,
    fabPosition: Pair<Float, Float> = Pair(0f, 0f),
    onAnimationComplete: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    var hasAnimated by remember(cardState.id) { mutableStateOf(!isNew) }

    // Calculate start position based on FAB or center
    val startOffset = if (fromFABPosition && isNew) {
        IntOffset(
            fabPosition.first.toInt() - cardState.x.toInt(),
            fabPosition.second.toInt() - cardState.y.toInt()
        )
    } else if (isNew) {
        IntOffset(0, 200) // Slide up from bottom
    } else {
        IntOffset.Zero
    }

    // Multi-stage animation for sophisticated entrance
    val animatedOffset by animateIntOffsetAsState(
        targetValue = if (hasAnimated) IntOffset.Zero else startOffset,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        finishedListener = {
            onAnimationComplete()
        },
        label = "card_entrance_offset"
    )

    val scale by animateFloatAsState(
        targetValue = if (hasAnimated) 1f else if (isNew) 0.3f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "card_entrance_scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (hasAnimated) 1f else if (isNew) 0f else 1f,
        animationSpec = tween(durationMillis = 400),
        label = "card_entrance_alpha"
    )

    val rotation by animateFloatAsState(
        targetValue = if (hasAnimated) 0f else if (isNew) 15f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "card_entrance_rotation"
    )

    // Trigger animation sequence
    LaunchedEffect(isNew, cardState.id) {
        if (isNew && !hasAnimated) {
            delay(50) // Small delay for better staging
            hasAnimated = true
        }
    }

    Box(
        modifier = Modifier
            .offset { animatedOffset }
            .scale(scale)
            .graphicsLayer {
                this.alpha = alpha
                this.rotationZ = rotation
            }
    ) {
        content()
    }
}

@Composable
fun EnhancedCardRemovalAnimation(
    isRemoving: Boolean,
    animationType: RemovalAnimationType = RemovalAnimationType.SCALE_FADE,
    onRemovalComplete: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = when (animationType) {
            RemovalAnimationType.SCALE_FADE -> if (isRemoving) 0f else 1f
            RemovalAnimationType.SLIDE_DOWN -> if (isRemoving) 0.8f else 1f
            RemovalAnimationType.FLIP_OUT -> if (isRemoving) 0f else 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        finishedListener = { if (isRemoving) onRemovalComplete() },
        label = "card_removal_scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isRemoving) 0f else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "card_removal_alpha"
    )

    val rotation by animateFloatAsState(
        targetValue = when (animationType) {
            RemovalAnimationType.SCALE_FADE -> if (isRemoving) 10f else 0f
            RemovalAnimationType.SLIDE_DOWN -> 0f
            RemovalAnimationType.FLIP_OUT -> if (isRemoving) 90f else 0f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "card_removal_rotation"
    )

    val offsetY by animateFloatAsState(
        targetValue = if (animationType == RemovalAnimationType.SLIDE_DOWN && isRemoving) 200f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy, // FIXED: Use correct constant
            stiffness = Spring.StiffnessMedium
        ),
        label = "card_removal_offset"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .graphicsLayer {
                this.alpha = alpha
                this.rotationZ = rotation
                this.translationY = offsetY
            }
    ) {
        content()
    }
}

@Composable
fun StaggeredCardAnimation(
    cards: List<CardState>,
    isVisible: Boolean,
    staggerDelayMs: Long = 100L,
    content: @Composable (CardState, Boolean) -> Unit
) {
    cards.forEachIndexed { index, card ->
        var cardVisible by remember { mutableStateOf(false) }

        LaunchedEffect(isVisible) {
            if (isVisible) {
                delay(index * staggerDelayMs)
                cardVisible = true
            } else {
                cardVisible = false
            }
        }

        content(card, cardVisible)
    }
}

enum class RemovalAnimationType {
    SCALE_FADE,
    SLIDE_DOWN,
    FLIP_OUT
}