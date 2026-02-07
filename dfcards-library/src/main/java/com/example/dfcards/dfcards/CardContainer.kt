package com.example.dfcards.dfcards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.util.Log
import com.example.dfcards.rememberCardStrings
import com.example.dfcards.rememberCardTimeFormatter

/**
 * Hosts all dashboard cards inside the nav drawer and exposes their bounds/size upstream so
 * overlays can avoid them.
 */
@Composable
fun CardContainer(
    onContainerSizeChanged: (IntSize) -> Unit = {},
    onCardBoundsChanged: (Rect) -> Unit = {},
    statusBarOffset: Float = 0f,
    onFlightTemplateClick: () -> Unit = {},
    isEditMode: Boolean = false,
    onEditModeChanged: (Boolean) -> Unit = {},
    hiddenCardIds: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
    cardVisualStyle: CardVisualStyle,
    viewModel: com.example.dfcards.dfcards.FlightDataViewModel = viewModel()
) {
    val density = LocalDensity.current
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var autoResizeEnabled by remember { mutableStateOf(false) }
    var showEditOptions by remember { mutableStateOf(false) }

    val selectedCardIds by viewModel.selectedCardIds.collectAsStateWithLifecycle()
    val visibleCardIds = remember(selectedCardIds, hiddenCardIds) {
        if (hiddenCardIds.isEmpty()) {
            selectedCardIds
        } else {
            selectedCardIds.filterNot { it in hiddenCardIds }.toSet()
        }
    }
    // Recompose when cards are created so we re-read the backing flow map.
    val activeCards by viewModel.activeCards.collectAsStateWithLifecycle()
    val cardStateFlows = remember(selectedCardIds, activeCards) { viewModel.cardStateFlows }
    val cardStrings = rememberCardStrings()
    val cardTimeFormatter = rememberCardTimeFormatter()

    val navigationBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navigationBarHeightPx = with(density) { navigationBarHeight.toPx() }

    val safeContainerSize = remember(containerSize, statusBarOffset, navigationBarHeightPx) {
        if (containerSize == IntSize.Zero) {
            containerSize
        } else {
            val safeHeight = (containerSize.height - statusBarOffset - navigationBarHeightPx)
                .toInt()
                .coerceAtLeast(100)
            IntSize(width = containerSize.width, height = safeHeight)
        }
    }

    LaunchedEffect(safeContainerSize, visibleCardIds, activeCards) {
        if (safeContainerSize != IntSize.Zero) {
            viewModel.initializeCards(safeContainerSize.toIntSizePx(), density.toDensityScale())
            if (visibleCardIds.isNotEmpty()) {
                viewModel.ensureCardsExist(visibleCardIds)
            }
            Log.d(
                "CardContainer",
                "size=${safeContainerSize.width}x${safeContainerSize.height} selected=${visibleCardIds.size} " +
                    "active=${activeCards.size} flows=${cardStateFlows.size}"
            )
        }
    }

    LaunchedEffect(cardStrings) {
        viewModel.updateCardStrings(cardStrings)
    }

    LaunchedEffect(cardTimeFormatter) {
        viewModel.updateCardTimeFormatter(cardTimeFormatter)
    }

    LaunchedEffect(safeContainerSize) {
        if (safeContainerSize != IntSize.Zero) {
            onContainerSizeChanged(safeContainerSize)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .background(if (isEditMode) Color.Black.copy(alpha = 0.05f) else Color.Transparent)
            .padding(top = with(density) { statusBarOffset.toDp() })
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { safeContainerSize.height.toDp() })
                .clipToBounds()
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInRoot()
                    onCardBoundsChanged(bounds)
                    val width = bounds.width
                    val height = bounds.height
                    if (width > 0f && height > 0f) {
                        onContainerSizeChanged(IntSize(width.toInt(), height.toInt()))
                    }
                }
        ) {
            cardStateFlows.forEach { (cardId, stateFlow) ->
                if (cardId !in visibleCardIds) return@forEach
                val cardState by stateFlow.collectAsStateWithLifecycle()

                key(cardId) {
                    Box(modifier = Modifier.zIndex(if (isEditMode) 10f else 2f)) {
                        EnhancedGestureCard(
                            cardState = cardState,
                            containerSize = safeContainerSize,
                            isEditMode = isEditMode,
                            allCards = viewModel.getAllCardStates(),
                            onCardUpdated = { updatedState ->
                                val boundedState = updatedState.copy(
                                    x = updatedState.x.coerceIn(
                                        0f,
                                        (safeContainerSize.width - updatedState.width).coerceAtLeast(0f)
                                    ),
                                    y = updatedState.y.coerceIn(
                                        0f,
                                        (safeContainerSize.height - updatedState.height).coerceAtLeast(0f)
                                    )
                                )
                                viewModel.updateCardState(boundedState)
                            },
                            onCardSelected = { },
                            onLongPress = { onEditModeChanged(!isEditMode) },
                            onDoubleClick = { },
                            enableSnapToGrid = true
                        ) { currentWidth, currentHeight ->
                            EnhancedFlightDataCard(
                                flightData = cardState.flightData,
                                cardWidth = currentWidth,
                                cardHeight = currentHeight,
                                isEditMode = isEditMode,
                                isLiveData = true,
                                modifier = Modifier.fillMaxSize(),
                                visualStyle = cardVisualStyle,
                                cardStrings = cardStrings
                            )
                        }
                    }
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose { onCardBoundsChanged(Rect.Zero) }
        }

        if (isEditMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .zIndex(50f)
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
                    modifier = Modifier.zIndex(51f)
                ) {
                    DropdownMenuItem(
                        text = { Text("Flight Template") },
                        onClick = {
                            onFlightTemplateClick()
                            showEditOptions = false
                        },
                        leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) }
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
                                    },
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        },
                        onClick = {
                            autoResizeEnabled = !autoResizeEnabled
                            showEditOptions = false
                        },
                        leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) }
                    )
                }
            }
        }
    }
}
