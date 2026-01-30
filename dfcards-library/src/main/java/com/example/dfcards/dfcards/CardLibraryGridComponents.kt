package com.example.dfcards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import kotlin.math.max
import kotlin.math.roundToInt

internal data class ModalDisplayData(
    val primaryValue: String,
    val secondaryValue: String?,
    val isLive: Boolean
)

@Composable
internal fun ResponsiveCardsGridWithLiveData(
    cards: List<CardDefinition>,
    selectedTemplate: FlightTemplate,
    allTemplates: List<FlightTemplate>,
    liveFlightData: RealTimeFlightData?,
    onTemplateCardToggle: (String, Boolean) -> Unit,
    screenWidth: androidx.compose.ui.unit.Dp
) {
    val cardStrings = rememberCardStrings()
    val cardTimeFormatter = rememberCardTimeFormatter()
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(cards) { card ->
            CompactCardItem(
                card = card,
                isSelected = selectedTemplate.cardIds.contains(card.id),
                liveFlightData = liveFlightData,
                cardStrings = cardStrings,
                cardTimeFormatter = cardTimeFormatter,
                onToggle = {
                    val isCurrentlySelected = selectedTemplate.cardIds.contains(card.id)
                    onTemplateCardToggle(card.id, !isCurrentlySelected)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun CompactCardItem(
    card: CardDefinition,
    isSelected: Boolean,
    liveFlightData: RealTimeFlightData? = null,
    cardStrings: CardStrings,
    cardTimeFormatter: CardTimeFormatter,
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

    val displayData = if (liveFlightData != null) {
        mapCardToModalDisplay(card, liveFlightData, cardStrings, cardTimeFormatter)
    } else {
        ModalDisplayData(
            primaryValue = "--",
            secondaryValue = "",
            isLive = false
        )
    }

    Surface(
        onClick = onToggle,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .height(85.dp), //  NEW: Fixed height for all cards
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
                verticalArrangement = Arrangement.SpaceBetween //  CHANGED: Space between elements
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
                    text = displayData.primaryValue,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) card.category.color else Color.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )

                //  NEW: Always show secondary line (even if empty) for consistent sizing
                Text(
                    text = displayData.secondaryValue ?: " ", //  Use space if no secondary value
                    style = secondaryStyle,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.height(16.dp) //  Fixed height for secondary line
                )
            }

            Icon(
                imageVector = if (isSelected) Icons.Default.Visibility else Icons.Default.VisibilityOff,
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

@Composable
internal fun CategoryTabs(
    selectedCategory: CardCategory,
    onCategorySelected: (CardCategory) -> Unit
) {
    PrimaryScrollableTabRow(
        selectedTabIndex = CardCategory.values().indexOf(selectedCategory),
        modifier = Modifier.fillMaxWidth(),
        edgePadding = 16.dp
    ) {
        CardCategory.values().forEach { category ->
            Tab(
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                modifier = Modifier.padding(horizontal = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = if (selectedCategory == category)
                            category.color
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = category.displayName,
                        fontSize = 13.sp,
                        fontWeight = if (selectedCategory == category)
                            FontWeight.Bold
                        else
                            FontWeight.Normal
                    )
                }
            }
        }
    }
}

private fun mapCardToModalDisplay(
    card: CardDefinition, //  Correct parameter name
    liveData: RealTimeFlightData,
    cardStrings: CardStrings,
    timeFormatter: CardTimeFormatter
): ModalDisplayData {
    //  SIMPLIFIED: Just use centralized card library mapping
    val (primaryValue, secondaryValue) =
        CardLibrary.mapLiveDataToCard(
            card.id,
            liveData,
            strings = cardStrings,
            timeFormatter = timeFormatter
        )

    return ModalDisplayData(
        primaryValue = primaryValue,
        secondaryValue = secondaryValue,
        isLive = true  //  Always true - no filtering
    )
}

private fun parseFlightTimeToHours(flightTime: String): Float {
    return try {
        val parts = flightTime.split(":")
        val hours = parts[0].toFloat()
        val minutes = parts[1].toFloat()
        hours + (minutes / 60f)
    } catch (e: Exception) {
        0f
    }
}

private fun calculateGForce(verticalSpeed: Double): Float {
    val baseG = 1.0f
    val additionalG = (kotlin.math.abs(verticalSpeed) / 10.0).toFloat() * 0.1f
    return (baseG + additionalG).coerceIn(0.5f, 3.0f)
}

private fun calculateQNH(baroAltitude: Double, gpsAltitude: Double): Int {
    val standardPressure = 1013.25
    val altitudeDiff = gpsAltitude - baroAltitude
    val pressureAdjustment = altitudeDiff / 8.5

    val qnh = (standardPressure + pressureAdjustment).roundToInt()
    return qnh.coerceIn(950, 1050)
}
