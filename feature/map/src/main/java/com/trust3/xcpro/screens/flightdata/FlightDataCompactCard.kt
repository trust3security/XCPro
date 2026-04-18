package com.example.ui1.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dfcards.CardDefinition
import com.trust3.xcpro.core.flight.RealTimeFlightData
import kotlin.math.max

@Composable
fun CompactCardItem(
    card: CardDefinition,
    isSelected: Boolean,
    liveFlightData: RealTimeFlightData? = null,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val titleStyle = MaterialTheme.typography.bodyMedium
    val titleFontSize = titleStyle.fontSize.takeIf { it != TextUnit.Unspecified } ?: 16.sp
    val secondaryFontSize = max(titleFontSize.value - 2f, 8f).sp
    val secondaryStyle = MaterialTheme.typography.bodySmall.copy(
        fontSize = secondaryFontSize,
        fontWeight = FontWeight.Medium
    )

    Surface(
        onClick = onToggle,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .height(85.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected)
                card.category.color
            else
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = card.title,
                    style = titleStyle,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "--",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) card.category.color else Color.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )

                Text(
                    text = " ",
                    style = secondaryStyle,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.height(16.dp)
                )
            }

            androidx.compose.material3.Icon(
                imageVector = if (isSelected) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                contentDescription = if (isSelected) "Card selected" else "Card not selected",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(12.dp),
                tint = if (isSelected)
                    card.category.color
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
