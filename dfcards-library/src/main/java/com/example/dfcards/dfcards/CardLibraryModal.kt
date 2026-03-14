package com.example.dfcards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

//  NEW: Flight Mode enum to match your existing FlightMode
enum class FlightModeSelection(
    val displayName: String,
    val icon: ImageVector,
    val color: androidx.compose.ui.graphics.Color
) {
    CRUISE("SCruise", Icons.Filled.Flight, androidx.compose.ui.graphics.Color(0xFF2196F3)),
    THERMAL("SThermal", Icons.AutoMirrored.Filled.TrendingUp, androidx.compose.ui.graphics.Color(0xFF9C27B0)),
    FINAL_GLIDE("SFinal Glide", Icons.Filled.Terrain, androidx.compose.ui.graphics.Color(0xFFF44336))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardLibraryModal(
    isVisible: Boolean,
    selectedCardIds: Set<String>,
    suggestedCards: List<CardDefinition>,
    allTemplates: List<FlightTemplate>,
    savedTemplateIdsByFlightMode: Map<FlightModeSelection, String?> = emptyMap(),
    liveFlightData: RealTimeFlightData? = null,
    currentFlightMode: FlightModeSelection,
    onCardToggle: (CardDefinition) -> Unit,
    onTemplateApply: (FlightTemplate) -> Unit,
    onFlightModeChanged: (FlightModeSelection) -> Unit,
    onPersistFlightModeTemplateSelection: (FlightModeSelection, String) -> Unit,
    onEditTemplate: (FlightTemplate, String, List<String>) -> Unit,
    onCreateNewTemplate: (String, List<String>) -> Unit,
    onDeleteTemplate: (FlightTemplate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    hiddenCardIds: Set<String> = emptySet()
) {
    var selectedCategory by remember { mutableStateOf(CardCategory.ESSENTIAL) }
    var showTemplateCreator by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<FlightTemplate?>(null) }

    var selectedFlightMode by remember(currentFlightMode) { mutableStateOf(currentFlightMode) }
    var selectedTemplate by remember { mutableStateOf<FlightTemplate?>(null) }

    //  Keep selection derived from the owner-provided saved template mapping.
    LaunchedEffect(selectedFlightMode, allTemplates, savedTemplateIdsByFlightMode) {
        if (allTemplates.isNotEmpty()) {
            val savedTemplateId = savedTemplateIdsByFlightMode[selectedFlightMode]
            selectedTemplate = if (savedTemplateId != null) {
                allTemplates.find { it.id == savedTemplateId }
            } else {
                allTemplates.find { it.name == "Essential" }
            }
        }
    }

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    val categoryCards = remember(selectedCategory, hiddenCardIds) {
        CardLibrary.getCardsByCategory(selectedCategory, hiddenCardIds)
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

                    //  UPDATED: Header with immediate apply and better messaging
                    FlightModeSelectionHeader(
                        selectedFlightMode = selectedFlightMode,
                        selectedTemplate = selectedTemplate,
                        onConfirm = {
                            //  CHANGED: Just apply template and dismiss (already saved)
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
                            //  FIXED: Don't do anything if same mode selected
                            if (mode == selectedFlightMode) {
                                return@FlightModeSelectionSection
                            }

                            selectedFlightMode = mode
                            selectedTemplate = null // This triggers LaunchedEffect to load new template
                            onFlightModeChanged(mode)
                        }
                    )

                    TemplateSelectionSection(
                        templates = allTemplates,
                        selectedTemplate = selectedTemplate,
                        selectedFlightMode = selectedFlightMode,
                        onTemplateSelected = { template ->
                            selectedTemplate = template
                            onPersistFlightModeTemplateSelection(selectedFlightMode, template.id)
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

                                //  CHANGED: Update template and auto-save via onEditTemplate
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
                    hiddenCardIds = hiddenCardIds,
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
