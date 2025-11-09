package com.example.dfcards.dfcards

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class CardVisualStyle(
    val backgroundBrush: Brush,
    val borderColor: Color,
    val borderWidth: Dp,
    val labelColor: Color,
    val primaryColor: Color,
    val unitColor: Color,
    val secondaryColor: Color
)

object CardVisualStyles {
    @Composable
    fun standard(): CardVisualStyle {
        val surface = MaterialTheme.colorScheme.surface
        val primaryContainer = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
        val labelColor = MaterialTheme.colorScheme.onSurface
        val secondaryColor = MaterialTheme.colorScheme.onSurface
        return CardVisualStyle(
            backgroundBrush = Brush.verticalGradient(listOf(surface, surface)),
            borderColor = Color.Black,
            borderWidth = 1.dp,
            labelColor = labelColor,
            primaryColor = Color.Black,
            unitColor = Color.Black.copy(alpha = 0.6f),
            secondaryColor = secondaryColor
        )
    }

    @Composable
    fun transparent(): CardVisualStyle {
        val labelColor = MaterialTheme.colorScheme.onSurface
        return CardVisualStyle(
            backgroundBrush = Brush.verticalGradient(
                listOf(Color.Transparent, Color.Transparent)
            ),
            borderColor = Color.Transparent,
            borderWidth = 0.dp,
            labelColor = labelColor,
            primaryColor = Color.Black,
            unitColor = Color.Black.copy(alpha = 0.6f),
            secondaryColor = labelColor
        )
    }
}
