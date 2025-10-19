package com.example.dfcards

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.with
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.xcpro.common.units.UnitsPreferences

/**
 * Shared component for selecting flight modes (Cruise, Thermal, Final Glide)
 */
@Composable
fun FlightModeSelectionSection(
    selectedFlightMode: FlightModeSelection,
    onFlightModeSelected: (FlightModeSelection) -> Unit,
    modifier: Modifier = Modifier,
    // ✅ NEW: Flight mode visibility controls
    flightModeVisibilities: Map<FlightModeSelection, Boolean> = emptyMap(),
    onFlightModeVisibilityToggle: (FlightModeSelection) -> Unit = {},
    onFlightModeOptionsClick: (FlightModeSelection) -> Unit = {}
) {
    Column(modifier = modifier) { // ✅ No padding here
        
        // ✅ NEW: Flight Mode header text
        Text(
            text = "Flight Mode Screens",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // ✅ CHANGED: Use Row directly, no Box wrapper, left-aligned like top tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp) // ✅ Match top tabs spacing
        ) {
            FlightModeSelection.values().forEach { mode ->
                val isVisible = flightModeVisibilities[mode] ?: true
                FlightModeCard(
                    mode = mode,
                    isSelected = selectedFlightMode == mode,
                    onSelect = {
                        if (mode == FlightModeSelection.CRUISE || isVisible) {
                            onFlightModeSelected(mode)
                        }
                    },
                    modifier = Modifier.weight(1f), // ✅ Use weight for equal distribution
                    isVisible = isVisible, // ✅ Default to visible
                    onVisibilityToggle = { onFlightModeVisibilityToggle(mode) },
                    onOptionsClick = { onFlightModeOptionsClick(mode) }
                )
            }
        }
    }
}

/**
 * Individual flight mode card (Cruise, Thermal, Final Glide)
 * ✅ UPDATED to accept modifier parameter and visibility controls
 */
@Composable
private fun FlightModeCard(
    mode: FlightModeSelection,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier, // ✅ Accept modifier parameter
    isVisible: Boolean = true, // ✅ NEW: Flight mode visibility
    onVisibilityToggle: () -> Unit = {}, // ✅ NEW: Toggle visibility callback
    onOptionsClick: () -> Unit = {} // ✅ NEW: 3-dot menu callback
) {
    Surface(
        onClick = onSelect,
        modifier = modifier // ✅ Use the passed modifier
            .height(80.dp)
            .clip(RoundedCornerShape(12.dp)),
        enabled = isVisible || mode == FlightModeSelection.CRUISE,
        color = if (!isVisible && mode != FlightModeSelection.CRUISE) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f) // Slightly dimmed for disabled modes
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = mode.icon,
                    contentDescription = null,
                    tint = if (isSelected && (isVisible || mode == FlightModeSelection.CRUISE)) {
                        MaterialTheme.colorScheme.primary
                    } else if (!isVisible && mode != FlightModeSelection.CRUISE) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) // Dimmed for disabled
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = mode.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (isSelected && (isVisible || mode == FlightModeSelection.CRUISE)) {
                        MaterialTheme.colorScheme.primary
                    } else if (!isVisible && mode != FlightModeSelection.CRUISE) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) // Dimmed for disabled
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            
            // ✅ NEW: Eye icon for visibility toggle (bottom right)
            // SCruise is always visible, others can be toggled
            IconButton(
                onClick = if (mode == FlightModeSelection.CRUISE) {
                    {} // No action for Cruise - always visible
                } else {
                    onVisibilityToggle
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .size(24.dp)
            ) {
                Icon(
                    imageVector = if (mode == FlightModeSelection.CRUISE || isVisible) {
                        Icons.Default.Visibility
                    } else {
                        Icons.Default.VisibilityOff
                    },
                    contentDescription = if (mode == FlightModeSelection.CRUISE) {
                        "${mode.displayName} always visible"
                    } else if (isVisible) {
                        "Hide ${mode.displayName}"
                    } else {
                        "Show ${mode.displayName}"
                    },
                    tint = if (mode == FlightModeSelection.CRUISE || isVisible) { 
                        mode.color 
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    },
                    modifier = Modifier.size(16.dp)
                )
            }
            
        }
    }
}

/**
 * Templates section showing available templates for the selected flight mode
 */
@Composable
fun TemplatesForModeSection(
    selectedFlightMode: FlightModeSelection,
    allTemplates: List<FlightTemplate>,
    selectedTemplate: FlightTemplate?,
    onTemplateSelected: (FlightTemplate) -> Unit,
    onEditTemplate: (FlightTemplate) -> Unit,
    onDeleteTemplate: (FlightTemplate) -> Unit,
    modifier: Modifier = Modifier
) {
    // ✅ ADD: Scroll state for auto-scrolling
    val scrollState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // ✅ ADD: Auto-scroll to selected template when it changes
    LaunchedEffect(selectedTemplate) {
        selectedTemplate?.let { template ->
            val templateIndex = allTemplates.indexOfFirst { it.id == template.id }
            if (templateIndex >= 0) {
                coroutineScope.launch {
                    // Scroll to center the selected template
                    scrollState.animateScrollToItem(
                        index = templateIndex,
                        scrollOffset = -100 // Offset to center it better
                    )
                    Log.d("TemplatesForMode", "✅ Auto-scrolled to template '${template.name}' at index $templateIndex")
                }
            }
        }
    }

    Column(modifier = modifier) {
        Text(
            text = "Templates for ${selectedFlightMode.displayName}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (allTemplates.isEmpty()) {
            // Loading state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading templates...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            // ✅ CHANGED: Connect scroll state for auto-scrolling
            LazyRow(
                state = scrollState, // ✅ ADD: Connect scroll state
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
            ) {
                items(allTemplates) { template ->
                    TemplateCard(
                        template = template,
                        isSelected = selectedTemplate?.id == template.id,
                        onSelect = { onTemplateSelected(template) },
                        onEdit = { onEditTemplate(template) },
                        onDelete = { onDeleteTemplate(template) }
                    )
                }
            }
        }
    }
}
/**
 * Individual template card (Essential, Thermal, Cross Country, etc.)
 */
@Composable
private fun TemplateCard(
    template: FlightTemplate,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showOptions by remember { mutableStateOf(false) }

    Surface(
        onClick = onSelect,
        modifier = Modifier
            .width(120.dp)
            .height(80.dp)
            .clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = if (isSelected) 8.dp else 2.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = template.icon,
                    contentDescription = null,
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = template.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (isSelected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${template.cardIds.size} cards",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .size(24.dp)
            ) {
                IconButton(
                    onClick = { showOptions = !showOptions },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Template options",
                        tint = if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = showOptions,
                    onDismissRequest = { showOptions = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Edit",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            onEdit()
                            showOptions = false
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Delete",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            onDelete()
                            showOptions = false
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * Category tabs section (Essential, Navigation, Performance, etc.)
 */
@Composable
fun CategoryTabsSection(
    selectedCategory: CardCategory,
    onCategorySelected: (CardCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) { // ✅ No padding here
        Text(
            text = "Card Categories",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp) // ✅ REMOVED start = 16.dp
        )

        ScrollableTabRow(
            selectedTabIndex = CardCategory.values().indexOf(selectedCategory),
            modifier = Modifier.fillMaxWidth(),
            edgePadding = 0.dp, // ✅ Changed from 16.dp to 0.dp for alignment
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
                            tint = if (selectedCategory == category)
                                category.color
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = category.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selectedCategory == category)
                                category.color
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Cards grid section showing cards for the selected category
 */
@Composable
fun CardsGridSection(
    selectedCategory: CardCategory,
    selectedTemplate: FlightTemplate?,
    onCardToggle: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    liveFlightData: RealTimeFlightData? = null,
    units: UnitsPreferences = UnitsPreferences()
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    // Get cards for the selected category
    val categoryCards = remember(selectedCategory) {
        CardLibrary.getCardsByCategory(selectedCategory)
    }

    Column(modifier = modifier) { // ✅ No padding here

        if (categoryCards.isEmpty()) {
            // Empty state
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
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp), // ✅ Changed from 16.dp to 0.dp
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(300.dp)
            ) {
                items(categoryCards) { card ->
                    CardGridItem(
                        card = card,
                        isSelected = selectedTemplate?.cardIds?.contains(card.id) ?: false,
                        liveFlightData = liveFlightData,  // ✅ NEW: Pass live data
                        units = units,
                        onToggle = {
                            val isCurrentlySelected = selectedTemplate?.cardIds?.contains(card.id) ?: false
                            onCardToggle(card.id, !isCurrentlySelected)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Individual card item in the grid
 */
@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
private fun CardGridItem(
    card: CardDefinition,
    isSelected: Boolean,
    onToggle: () -> Unit,
    liveFlightData: RealTimeFlightData? = null,
    units: UnitsPreferences = UnitsPreferences()
) {
    // ✅ FIXED: Remove remember() dependency on liveFlightData to prevent flickering
    // Compose will automatically skip recomposition if primaryValue/secondaryValue haven't changed
    val (primaryValue, secondaryValue) = if (liveFlightData != null) {
        CardLibrary.mapLiveDataToCard(card.id, liveFlightData, units)
    } else {
        Pair("--", card.unit.ifEmpty { card.description.take(10) })
    }

    // ✅ FIXED: Always show data in black - GPS is available
    // The pilot needs to see their flight data - no quality filtering
    val textColor = MaterialTheme.colorScheme.onSurface  // Always black
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
                // Card title
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = textColor,  // ✅ Use dynamic color
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // ✅ SMOOTH TRANSITION: Fade between value changes
                AnimatedContent(
                    targetState = primaryValue,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(200)) with
                        fadeOut(animationSpec = tween(200))
                    },
                    label = "primary_value_transition"
                ) { value ->
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) card.category.color else textColor,  // ✅ Use dynamic color
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }

                // ✅ SMOOTH TRANSITION: Fade between secondary value changes
                AnimatedContent(
                    targetState = secondaryValue ?: card.unit.ifEmpty { card.description.take(10) },
                    transitionSpec = {
                        fadeIn(animationSpec = tween(200)) with
                        fadeOut(animationSpec = tween(200))
                    },
                    label = "secondary_value_transition"
                ) { value ->
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.6f),  // ✅ Use dynamic color with reduced opacity
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Selection indicator
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
