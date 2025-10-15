package com.example.dfcards

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ✅ NEW: Flight Mode enum to match your existing FlightMode
enum class FlightModeSelection(
    val displayName: String,
    val icon: ImageVector,
    val color: androidx.compose.ui.graphics.Color
) {
    CRUISE("SCruise", Icons.Filled.Flight, androidx.compose.ui.graphics.Color(0xFF2196F3)),
    THERMAL("SThermal", Icons.Filled.TrendingUp, androidx.compose.ui.graphics.Color(0xFF9C27B0)),
    FINAL_GLIDE("SFinal Glide", Icons.Filled.Terrain, androidx.compose.ui.graphics.Color(0xFFF44336))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardLibraryModal(
    isVisible: Boolean,
    selectedCardIds: Set<String>,
    suggestedCards: List<CardDefinition>,
    allTemplates: List<FlightTemplate>,
    liveFlightData: RealTimeFlightData? = null,
    currentFlightMode: FlightModeSelection,
    onCardToggle: (CardDefinition) -> Unit,
    onTemplateApply: (FlightTemplate) -> Unit,
    onFlightModeChanged: (FlightModeSelection) -> Unit,
    onEditTemplate: (FlightTemplate, String, List<String>) -> Unit,
    onCreateNewTemplate: (String, List<String>) -> Unit,
    onDeleteTemplate: (FlightTemplate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cardPreferences = remember { CardPreferences(context) }
    val coroutineScope = rememberCoroutineScope()

    var selectedCategory by remember { mutableStateOf(CardCategory.ESSENTIAL) }
    var showTemplateCreator by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<FlightTemplate?>(null) }

    var selectedFlightMode by remember(currentFlightMode) { mutableStateOf(currentFlightMode) }
    var selectedTemplate by remember { mutableStateOf<FlightTemplate?>(null) }

    // ✅ Load saved template when modal opens or flight mode changes
    LaunchedEffect(selectedFlightMode, allTemplates) {
        if (allTemplates.isNotEmpty()) {
            cardPreferences.getFlightModeTemplate(selectedFlightMode.name).collect { savedTemplateId ->
                val template = if (savedTemplateId != null) {
                    allTemplates.find { it.id == savedTemplateId }
                } else {
                    allTemplates.find { it.name == "Essential" }
                }

                selectedTemplate = template
                println("DEBUG: Auto-selected template '${template?.name}' for ${selectedFlightMode.name} mode")
            }
        }
    }

    // ✅ NEW: Auto-save when template selection changes
    LaunchedEffect(selectedTemplate, selectedFlightMode) {
        selectedTemplate?.let { template ->
            coroutineScope.launch {
                cardPreferences.saveFlightModeTemplate(
                    flightMode = selectedFlightMode.name,
                    templateId = template.id
                )
                println("DEBUG: Auto-saved ${selectedFlightMode.name} → ${template.name}")
            }
        }
    }

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    val categoryCards = remember(selectedCategory) {
        CardLibrary.getCardsByCategory(selectedCategory)
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(0.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Spacer(
                        modifier = Modifier.height(
                            WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                        )
                    )

                    // ✅ UPDATED: Header with immediate apply and better messaging
                    FlightModeSelectionHeader(
                        selectedFlightMode = selectedFlightMode,
                        selectedTemplate = selectedTemplate,
                        onConfirm = {
                            // ✅ CHANGED: Just apply template and dismiss (already saved)
                            selectedTemplate?.let { template ->
                                onTemplateApply(template)
                                onDismiss()
                            }
                        },
                        onDismiss = onDismiss
                    )

                    FlightModeSelectionSection(
                        selectedFlightMode = selectedFlightMode,
                        onFlightModeSelected = { mode ->
                            // ✅ FIXED: Don't do anything if same mode selected
                            if (mode == selectedFlightMode) {
                                println("DEBUG: Same flight mode selected, no action needed")
                                return@FlightModeSelectionSection
                            }

                            selectedFlightMode = mode
                            selectedTemplate = null // This triggers LaunchedEffect to load new template
                            onFlightModeChanged(mode)
                            println("DEBUG: Flight mode changed to ${mode.name}")
                        }
                    )

                    TemplateSelectionSection(
                        templates = allTemplates,
                        selectedTemplate = selectedTemplate,
                        selectedFlightMode = selectedFlightMode,
                        onTemplateSelected = { template ->
                            // ✅ CHANGED: Auto-save happens via LaunchedEffect
                            selectedTemplate = template
                            println("DEBUG: User selected template: ${template.name}")
                        },
                        onEditTemplate = { template ->
                            editingTemplate = template
                            showTemplateCreator = true
                        },
                        onDeleteTemplate = { templateToDelete ->
                            if (selectedTemplate?.id == templateToDelete.id) {
                                selectedTemplate = null
                            }
                            onDeleteTemplate(templateToDelete)
                        }
                    )

                    CategoryTabs(
                        selectedCategory = selectedCategory,
                        onCategorySelected = { selectedCategory = it }
                    )

                    if (selectedTemplate != null) {
                        ResponsiveCardsGridWithLiveData(
                            cards = categoryCards,
                            selectedTemplate = selectedTemplate!!,
                            allTemplates = allTemplates,
                            liveFlightData = liveFlightData,
                            onTemplateCardToggle = { cardId, isSelected ->
                                val updatedCardIds = if (isSelected) {
                                    selectedTemplate!!.cardIds + cardId
                                } else {
                                    selectedTemplate!!.cardIds - cardId
                                }

                                // ✅ CHANGED: Update template and auto-save via onEditTemplate
                                onEditTemplate(selectedTemplate!!, selectedTemplate!!.name, updatedCardIds)

                                // Update local state
                                val updatedTemplate = selectedTemplate!!.copy(cardIds = updatedCardIds)
                                selectedTemplate = updatedTemplate
                            },
                            screenWidth = screenWidth
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Text(
                                    text = "Loading template for ${selectedFlightMode.displayName} mode...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(
                        modifier = Modifier.height(
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        )
                    )
                }
            }

            if (!showTemplateCreator) {
                FloatingActionButton(
                    onClick = {
                        editingTemplate = null
                        showTemplateCreator = true
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .padding(
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        ),
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create Template"
                    )
                }
            }

            if (showTemplateCreator) {
                TemplateEditorModal(
                    selectedCardIds = emptySet(),
                    existingTemplate = editingTemplate,
                    liveFlightData = liveFlightData,
                    onSaveTemplate = { name, cardIds ->
                        if (editingTemplate != null) {
                            onEditTemplate(editingTemplate!!, name, cardIds)
                            if (selectedTemplate?.id == editingTemplate!!.id) {
                                selectedTemplate = editingTemplate!!.copy(name = name, cardIds = cardIds)
                            }
                        } else {
                            onCreateNewTemplate(name, cardIds)
                        }
                        showTemplateCreator = false
                        editingTemplate = null
                    },
                    onDismiss = {
                        showTemplateCreator = false
                        editingTemplate = null
                    }
                )
            }
        }
    }
}

// Keep all your existing helper functions...
// Rest of your existing functions remain the same...
// ✅ NEW: Flight Mode Selection Header
// ✅ UPDATED: Header with better messaging
@Composable
private fun FlightModeSelectionHeader(
    selectedFlightMode: FlightModeSelection,
    selectedTemplate: FlightTemplate?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Flight Template Setup",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                if (selectedTemplate != null) {
                    Text(
                        text = "${selectedFlightMode.displayName}: ${selectedTemplate.name} (${selectedTemplate.cardIds.size} cards)",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = "Loading template for ${selectedFlightMode.displayName} mode...",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Row {
                // ✅ REMOVED: No more checkmark button
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Cancel",
                        modifier = Modifier.graphicsLayer { rotationZ = 45f },
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // ✅ REMOVED: All status cards - no more "Template saved..." messages
    }
}

// ✅ NEW: Flight Mode Selection Section
// ✅ NEW: Flight Mode Selection Section with dynamic sizing
@Composable
private fun FlightModeSelectionSection(
    selectedFlightMode: FlightModeSelection,
    onFlightModeSelected: (FlightModeSelection) -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    // ✅ Calculate dynamic width for flight mode cards
    val horizontalPadding = 32.dp // 16dp on each side
    val cardSpacing = 8.dp * 2 // spacing between 3 cards
    val availableWidth = screenWidth - horizontalPadding - cardSpacing
    val cardWidth = availableWidth / 3

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(FlightModeSelection.values()) { mode ->
                SelectableFlightModeCard(
                    flightMode = mode,
                    isSelected = selectedFlightMode == mode,
                    onSelect = { onFlightModeSelected(mode) },
                    cardWidth = cardWidth // ✅ Pass dynamic width
                )
            }
        }
    }
}

// ✅ NEW: Selectable Flight Mode Card
// ✅ UPDATED: Flight Mode Card with dynamic width
@Composable
private fun SelectableFlightModeCard(
    flightMode: FlightModeSelection,
    isSelected: Boolean,
    onSelect: () -> Unit,
    cardWidth: androidx.compose.ui.unit.Dp // ✅ NEW: Dynamic width parameter
) {
    Surface(
        onClick = onSelect,
        modifier = Modifier
            .width(cardWidth) // ✅ CHANGED: Use dynamic width
            .height(80.dp)
            .clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surface,
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
                    imageVector = flightMode.icon,
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
                    text = flightMode.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (isSelected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(16.dp)
                )
            }
        }
    }
}

// ✅ UPDATED: Template Selection Section with Flight Mode context
@Composable
private fun TemplateSelectionSection(
    templates: List<FlightTemplate>,
    selectedTemplate: FlightTemplate?,
    selectedFlightMode: FlightModeSelection,
    onTemplateSelected: (FlightTemplate) -> Unit,
    onEditTemplate: (FlightTemplate) -> Unit,
    onDeleteTemplate: (FlightTemplate) -> Unit
) {
    // ✅ NEW: Create scroll state for auto-scrolling
    val scrollState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // ✅ NEW: Auto-scroll to selected template when flight mode changes
    LaunchedEffect(selectedTemplate, selectedFlightMode) {
        selectedTemplate?.let { template ->
            val templateIndex = templates.indexOfFirst { it.id == template.id }
            if (templateIndex >= 0) {
                coroutineScope.launch {
                    // Scroll to center the selected template
                    scrollState.animateScrollToItem(
                        index = templateIndex,
                        scrollOffset = -100 // Offset to center it better
                    )
                    println("DEBUG: Auto-scrolled to template '${template.name}' at index $templateIndex")
                }
            }
        }
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "Templates for ${selectedFlightMode.displayName}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // ✅ CHANGED: Use the scroll state for auto-scrolling
        LazyRow(
            state = scrollState, // ✅ ADD: Connect scroll state
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(templates) { template ->
                SelectableTemplateCard(
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

@Composable
private fun ResponsiveCardsGridWithLiveData(
    cards: List<CardDefinition>,
    selectedTemplate: FlightTemplate,
    allTemplates: List<FlightTemplate>,
    liveFlightData: RealTimeFlightData?,
    onTemplateCardToggle: (String, Boolean) -> Unit,
    screenWidth: androidx.compose.ui.unit.Dp
) {
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
                onToggle = {
                    val isCurrentlySelected = selectedTemplate.cardIds.contains(card.id)
                    onTemplateCardToggle(card.id, !isCurrentlySelected)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ✅ Keep all other existing components (TemplateEditorModal, CategoryTabs, etc.)
// ... (rest of the existing code remains unchanged)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateEditorModal(
    selectedCardIds: Set<String>,
    existingTemplate: FlightTemplate? = null,
    liveFlightData: RealTimeFlightData? = null,
    onSaveTemplate: (String, List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var templateName by remember {
        mutableStateOf(existingTemplate?.name ?: "")
    }
    var selectedCards by remember {
        mutableStateOf(
            existingTemplate?.cardIds?.toSet() ?: selectedCardIds.toSet()
        )
    }

    val isValid = templateName.isNotBlank() && selectedCards.isNotEmpty()
    val isEditing = existingTemplate != null

    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(0.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEditing) "Edit Template" else "Create Template",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Row {
                    IconButton(
                        onClick = {
                            if (isValid) {
                                onSaveTemplate(templateName, selectedCards.toList())
                            }
                        },
                        enabled = isValid
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = if (isEditing) "Save Changes" else "Save Template",
                            tint = if (isValid)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel"
                        )
                    }
                }
            }

            HorizontalDivider()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                Text(
                    text = "Name",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                OutlinedTextField(
                    value = templateName,
                    onValueChange = { templateName = it },
                    placeholder = { Text("e.g., My Competition Setup") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(6.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Cards (${selectedCards.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CardCategory.values().forEach { category ->
                        val categoryCards = CardLibrary.getCardsByCategory(category)

                        if (categoryCards.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = category.icon,
                                        contentDescription = null,
                                        tint = category.color,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = category.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = category.color
                                    )
                                }
                            }

                            items(categoryCards.chunked(3)) { cardRow ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    cardRow.forEach { card ->
                                        CompactCardItem(
                                            card = card,
                                            isSelected = selectedCards.contains(card.id),
                                            liveFlightData = liveFlightData,
                                            onToggle = {
                                                selectedCards = if (selectedCards.contains(card.id)) {
                                                    selectedCards - card.id
                                                } else {
                                                    selectedCards + card.id
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(3 - cardRow.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactCardItem(
    card: CardDefinition,
    isSelected: Boolean,
    liveFlightData: RealTimeFlightData? = null,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayData = if (liveFlightData != null) {
        mapCardToModalDisplay(card, liveFlightData)
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
            .height(85.dp), // ✅ NEW: Fixed height for all cards
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
                verticalArrangement = Arrangement.SpaceBetween // ✅ CHANGED: Space between elements
            ) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.bodyMedium,
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

                // ✅ NEW: Always show secondary line (even if empty) for consistent sizing
                Text(
                    text = displayData.secondaryValue ?: " ", // ✅ Use space if no secondary value
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.height(16.dp) // ✅ Fixed height for secondary line
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
private fun SelectableTemplateCard(
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
            .clip(RoundedCornerShape(12.dp))
            .scale(if (isSelected) 1.05f else 1f),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = if (isSelected) 8.dp else 2.dp,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
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

            // ✅ REMOVED: No more check icon when selected

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp) // ✅ LOWERED: Added top padding to lower 3-dots
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

@Composable
private fun CategoryTabs(
    selectedCategory: CardCategory,
    onCategorySelected: (CardCategory) -> Unit
) {
    ScrollableTabRow(
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

// ✅ RENAMED: Avoid conflict with CardPreview.kt
private data class ModalDisplayData(
    val primaryValue: String,
    val secondaryValue: String?,
    val isLive: Boolean
)

// ✅ FIXED: Renamed function with correct parameters - NO SAMPLES, only real data or placeholders
private fun mapCardToModalDisplay(
    card: CardDefinition, // ✅ Correct parameter name
    liveData: RealTimeFlightData
): ModalDisplayData {
    // ✅ SIMPLIFIED: Just use centralized card library mapping
    val (primaryValue, secondaryValue) = CardLibrary.mapLiveDataToCard(card.id, liveData)

    return ModalDisplayData(
        primaryValue = primaryValue,
        secondaryValue = secondaryValue,
        isLive = true  // ✅ Always true - no filtering
    )
}

// ✅ Helper functions for calculations
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