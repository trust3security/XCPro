package com.example.dfcards

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trust3.xcpro.common.units.UnitsPreferences
import com.trust3.xcpro.core.flight.RealTimeFlightData
import kotlin.math.max

/**
 * Category tabs section (Essential, Navigation, Performance, etc.).
 */
@Composable
fun CategoryTabsSection(
    selectedCategory: CardCategory,
    onCategorySelected: (CardCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Card Categories",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        PrimaryScrollableTabRow(
            selectedTabIndex = CardCategory.values().indexOf(selectedCategory),
            modifier = Modifier.fillMaxWidth(),
            edgePadding = 0.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            CardCategory.values().forEach { category ->
                Tab(
                    selected = selectedCategory == category,
                    onClick = { onCategorySelected(category) },
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = category.displayName,
                            tint = if (selectedCategory == category) {
                                category.color
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = category.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selectedCategory == category) {
                                category.color
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            },
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Cards grid section showing cards for the selected category.
 */
@Composable
fun CardsGridSection(
    selectedCategory: CardCategory,
    selectedTemplate: FlightTemplate?,
    onCardToggle: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    liveFlightData: RealTimeFlightData? = null,
    units: UnitsPreferences = UnitsPreferences(),
    selectedCardIds: List<String>? = null,
    hiddenCardIds: Set<String> = emptySet()
) {
    val cardStrings = rememberCardStrings()
    val cardTimeFormatter = rememberCardTimeFormatter()
    val categoryCards = remember(selectedCategory, hiddenCardIds) {
        CardLibrary.getCardsByCategory(selectedCategory, hiddenCardIds)
    }

    Column(modifier = modifier) {
        if (categoryCards.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No cards available in this category",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(300.dp)
            ) {
                val activeCardIds = selectedCardIds ?: selectedTemplate?.cardIds.orEmpty()
                items(categoryCards) { card ->
                    CardGridItem(
                        card = card,
                        isSelected = activeCardIds.contains(card.id),
                        liveFlightData = liveFlightData,
                        units = units,
                        cardStrings = cardStrings,
                        timeFormatter = cardTimeFormatter,
                        onToggle = {
                            val isCurrentlySelected = activeCardIds.contains(card.id)
                            onCardToggle(card.id, !isCurrentlySelected)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Individual card item in the grid.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun CardGridItem(
    card: CardDefinition,
    isSelected: Boolean,
    onToggle: () -> Unit,
    liveFlightData: RealTimeFlightData? = null,
    units: UnitsPreferences = UnitsPreferences(),
    cardStrings: CardStrings,
    timeFormatter: CardTimeFormatter
) {
    val titleStyle = MaterialTheme.typography.bodyMedium
    val titleFontSize = titleStyle.fontSize.takeIf { it != TextUnit.Unspecified } ?: 16.sp
    val secondaryFontSize = max(titleFontSize.value - 2f, 8f).sp
    val secondaryStyle = MaterialTheme.typography.bodySmall.copy(
        fontSize = secondaryFontSize
    )

    val (primaryValue, secondaryValue) = if (liveFlightData != null) {
        CardLibrary.mapLiveDataToCard(
            card.id,
            liveFlightData,
            units,
            cardStrings,
            timeFormatter
        )
    } else {
        Pair("--", card.unit.ifEmpty { card.description.take(10) })
    }

    val textColor = MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.2f)
            .clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) {
                card.category.color
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            }
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
                    color = textColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                AnimatedContent(
                    targetState = primaryValue,
                    transitionSpec = {
                        fadeIn(animationSpec = androidx.compose.animation.core.tween(200)) togetherWith
                        fadeOut(animationSpec = androidx.compose.animation.core.tween(200))
                    },
                    label = "primary_value_transition"
                ) { value ->
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) card.category.color else textColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }

                AnimatedContent(
                    targetState = secondaryValue ?: card.unit.ifEmpty { card.description.take(10) },
                    transitionSpec = {
                        fadeIn(animationSpec = androidx.compose.animation.core.tween(200)) togetherWith
                        fadeOut(animationSpec = androidx.compose.animation.core.tween(200))
                    },
                    label = "secondary_value_transition"
                ) { value ->
                    Text(
                        text = value,
                        style = secondaryStyle,
                        color = textColor.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = if (isSelected) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = if (isSelected) "Card selected" else "Card not selected",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(12.dp),
                tint = if (isSelected) {
                    card.category.color
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                }
            )
        }
    }
}

