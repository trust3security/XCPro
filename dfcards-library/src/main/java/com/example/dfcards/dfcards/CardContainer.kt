package com.example.dfcards.dfcards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.zIndex
import com.example.dfcards.dfcards.CardVisualStyle

// ✅ REFACTORED: Removed flightDataList parameter - data comes from viewModel now
@Composable
fun CardContainer(
    onContainerSizeChanged: (IntSize) -> Unit = {},
    onCardBoundsChanged: (Rect) -> Unit = {},
    statusBarOffset: Float = 0f,
    onFlightTemplateClick: () -> Unit = {},
    isEditMode: Boolean = false,
    onEditModeChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    cardVisualStyle: CardVisualStyle,
    viewModel: com.example.dfcards.dfcards.FlightDataViewModel = viewModel()
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var autoResizeEnabled by remember { mutableStateOf(false) }
    var showEditOptions by remember { mutableStateOf(false) }

    // ✅ FIXED: Observe selectedCardIds to trigger recomposition when cards change
    val selectedCardIds by viewModel.selectedCardIds.collectAsState()

    // ✅ Re-read cardStateFlows when selectedCardIds changes
    val cardStateFlows = remember(selectedCardIds) { viewModel.cardStateFlows }

    // Get system navigation bar height
    val navigationBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navigationBarHeightPx = with(density) { navigationBarHeight.toPx() }

    // Calculate safe area
    val safeContainerSize = remember(containerSize, statusBarOffset, navigationBarHeightPx) {
        if (containerSize != IntSize.Zero) {
            val safeHeight = (containerSize.height - statusBarOffset - navigationBarHeightPx).toInt().coerceAtLeast(100)
            IntSize(
                width = containerSize.width,
                height = safeHeight
            )
        } else {
            containerSize
        }
    }

    // ✅ REFACTORED: Initialize cards using map
    LaunchedEffect(safeContainerSize) {
        if (safeContainerSize != IntSize.Zero && cardStateFlows.isEmpty()) {
            viewModel.initializeCards(safeContainerSize, density)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                containerSize = size
                onContainerSizeChanged(safeContainerSize)
            }
            .background(
                if (isEditMode) Color.Black.copy(alpha = 0.05f) else Color.Transparent
            )
            .padding(top = with(density) { statusBarOffset.toDp() })
    ) {

        // ✅ REVOLUTIONARY: Card rendering with independent StateFlows
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { safeContainerSize.height.toDp() })
                .clipToBounds()
                .onGloballyPositioned { coordinates ->
                    onCardBoundsChanged(coordinates.boundsInRoot())
                }
        ) {
            // ✅ SSOT: Only display cards that are in selectedCardIds
            // All cards exist in memory and receive live data, but only selected ones render
            cardStateFlows.forEach { (cardId, stateFlow) ->
                // ✅ SSOT: Skip cards not in current template
                if (cardId !in selectedCardIds) {
                    return@forEach  // Card exists in memory but isn't visible
                }

                // ✅ Collect THIS card's state (only THIS card recomposes when its StateFlow changes)
                val cardState by stateFlow.collectAsState()

                // ✅ CRITICAL: key() tells Compose this is the SAME card instance
                key(cardId) {
                    Box(
                        modifier = Modifier.zIndex(if (isEditMode) 10f else 2f)
                    ) {
                        EnhancedGestureCard(
                            cardState = cardState,
                            containerSize = safeContainerSize,
                            isEditMode = isEditMode,
                            allCards = viewModel.getAllCardStates(), // Use helper for collision detection
                            onCardUpdated = { updatedState ->
                                val boundedState = updatedState.copy(
                                    x = updatedState.x.coerceIn(0f, (safeContainerSize.width - updatedState.width).coerceAtLeast(0f)),
                                    y = updatedState.y.coerceIn(0f, (safeContainerSize.height - updatedState.height).coerceAtLeast(0f))
                                )
                                viewModel.updateCardState(boundedState)
                            },
                            onCardSelected = { },
                            onLongPress = { onEditModeChanged(!isEditMode) },
                            onDoubleClick = {},
                            enableSnapToGrid = true
                        ) { currentWidth, currentHeight ->
                            EnhancedFlightDataCard(
                                flightData = cardState.flightData,
                                cardWidth = currentWidth,
                                cardHeight = currentHeight,
                                isEditMode = isEditMode,
                                isLiveData = true,
                                modifier = Modifier.fillMaxSize(),
                                visualStyle = cardVisualStyle
                            )
                        }
                    }
                }
                // ✅ RESULT: When vario updates, ONLY vario card recomposes. GPS card stays stable!
            }
        }

        DisposableEffect(Unit) {
            onDispose { onCardBoundsChanged(Rect.Zero) }
        }

        // Edit Mode Options Menu - Lower z-index when cards are in edit mode
        if (isEditMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .zIndex(if (isEditMode) 50f else 10f) // Lower than cards when in edit mode
            ) {
                IconButton(
                    onClick = { showEditOptions = !showEditOptions },
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            shape = CircleShape
                        )
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Edit Options",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }

                DropdownMenu(
                    expanded = showEditOptions,
                    onDismissRequest = { showEditOptions = false },
                    modifier = Modifier.zIndex(if (isEditMode) 51f else 11f) // Slightly higher than button
                ) {
                    DropdownMenuItem(
                        text = { Text("Flight Template") },
                        onClick = {
                            onFlightTemplateClick()
                            showEditOptions = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Star, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Auto Resize All")
                                Switch(
                                    checked = autoResizeEnabled,
                                    onCheckedChange = {
                                        autoResizeEnabled = it
                                        showEditOptions = false
                                        // Toggle remains but does nothing
                                    },
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        },
                        onClick = {
                            autoResizeEnabled = !autoResizeEnabled
                            showEditOptions = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Star, contentDescription = null)
                        }
                    )
                }
            }
        }
    }
}
