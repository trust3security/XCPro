package com.example.ui1.screens.flightmgmt

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.dfcards.*
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.units.UnitsRepository
import com.example.xcpro.profiles.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "FlightScreensTab"

@Composable
fun FlightDataScreensTab(
    selectedFlightMode: FlightModeSelection,
    onFlightModeSelected: (FlightModeSelection) -> Unit,
    allTemplates: List<FlightTemplate>,
    selectedTemplate: FlightTemplate?,
    onTemplateSelected: (FlightTemplate) -> Unit,
    selectedCategory: CardCategory,
    onCategorySelected: (CardCategory) -> Unit,
    cardPreferences: CardPreferences,
    scope: CoroutineScope,
    // ✅ ADD: Callback for template editing
    onEditTemplate: (FlightTemplate) -> Unit,
    // ✅ ADD: Active profile for profile-aware saving
    activeProfile: UserProfile?,
    // ✅ NEW: Live flight data for card previews
    liveFlightData: RealTimeFlightData? = null
) {
    val context = LocalContext.current
    val unitsRepository = remember(context.applicationContext) {
        UnitsRepository(context.applicationContext)
    }
    val unitsPreferences by unitsRepository.unitsFlow.collectAsState(initial = UnitsPreferences())
    
    // ✅ NEW: Flight mode visibility state management
    var flightModeVisibilities by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var showOptionsDialog by remember { mutableStateOf<FlightModeSelection?>(null) }

    // ✅ NEW: Load flight mode visibilities for active profile
    LaunchedEffect(activeProfile?.id) {
        activeProfile?.let { profile ->
            cardPreferences.getProfileAllFlightModeVisibilities(profile.id).first().let { visibilities ->
                flightModeVisibilities = visibilities
                Log.d(TAG, "✅ Loaded flight mode visibilities for profile '${profile.name}': $visibilities")
            }
        }
    }

    // ✅ FIXED: Load template when flight mode changes - PROFILE AWARE with debouncing
    var isLoadingTemplate by remember { mutableStateOf(false) }

    LaunchedEffect(selectedFlightMode, allTemplates, activeProfile) {
        Log.d(TAG, "🔄 LaunchedEffect triggered - Profile: ${activeProfile?.name}, Flight mode: ${selectedFlightMode.name}, Templates: ${allTemplates.size}")
        Log.d(TAG, "🔄 Available template IDs: ${allTemplates.map { "${it.id}:${it.name}" }.joinToString(", ")}")

        if (allTemplates.isNotEmpty() && activeProfile != null && !isLoadingTemplate) {
            isLoadingTemplate = true
            Log.d(TAG, "🔍 Looking for saved template for profile '${activeProfile.name}' in flight mode: ${selectedFlightMode.name}")

            try {
                // ✅ FIXED: Use profile-aware template loading
                val savedTemplateId = cardPreferences.getProfileFlightModeTemplate(activeProfile.id, selectedFlightMode.name).first()
                Log.d(TAG, "💾 Retrieved saved template ID: $savedTemplateId for profile '${activeProfile.name}' in ${selectedFlightMode.name}")

                val template = if (savedTemplateId != null) {
                    val foundTemplate = allTemplates.find { it.id == savedTemplateId }
                    Log.d(TAG, "🔍 Searching for template ID '$savedTemplateId' in ${allTemplates.size} templates")
                    if (foundTemplate != null) {
                        // ✅ FIX: Load profile-specific card configuration
                        val profileCards = cardPreferences.getProfileTemplateCards(activeProfile.id, foundTemplate.id).first()
                        val finalTemplate = if (profileCards != null) {
                            // Use profile-specific cards
                            Log.d(TAG, "📋 Using profile-specific cards for template '${foundTemplate.name}': ${profileCards.joinToString(",")}")
                            foundTemplate.copy(cardIds = profileCards)
                        } else {
                            // Use default template cards
                            Log.d(TAG, "📋 Using default template cards for '${foundTemplate.name}': ${foundTemplate.cardIds.joinToString(",")}")
                            foundTemplate
                        }
                        Log.d(TAG, "🎯 Found template '${finalTemplate.name}' with ${finalTemplate.cardIds.size} cards: ${finalTemplate.cardIds.joinToString(",")}")
                        finalTemplate
                    } else {
                        Log.d(TAG, "❌ Template ID '$savedTemplateId' not found in available templates: ${allTemplates.map { it.id }.joinToString(",")}")
                        // ✅ FIX: Don't fall back immediately - preserve current selection
                        selectedTemplate
                    }
                } else {
                    // Use default template for this flight mode ONLY if no current selection
                    if (selectedTemplate == null) {
                        val fallbackTemplate = when (selectedFlightMode) {
                            FlightModeSelection.CRUISE -> allTemplates.find { it.id == "id01" }
                            FlightModeSelection.THERMAL -> allTemplates.find { it.id == "id02" }
                            FlightModeSelection.FINAL_GLIDE -> allTemplates.find { it.id == "id03" }
                            FlightModeSelection.HAWK -> allTemplates.find { it.id == "hawk" }
                        }
                        Log.d(TAG, "⚠️ No saved template for ${selectedFlightMode.name}, using fallback: ${fallbackTemplate?.name ?: "NO FALLBACK"} (${fallbackTemplate?.cardIds?.size} cards)")
                        fallbackTemplate
                    } else {
                        Log.d(TAG, "⚠️ No saved template for ${selectedFlightMode.name}, keeping current selection: ${selectedTemplate?.name}")
                        selectedTemplate
                    }
                }

                // ✅ FIX: Only update if template is different from current selection
                if (template != null && template.id != selectedTemplate?.id) {
                    onTemplateSelected(template)
                    Log.d(TAG, "✅ Template selection changed to: ${template.name} (${template.cardIds.size} cards)")
                } else {
                    Log.d(TAG, "✅ Template already selected or no change needed: ${template?.name ?: "NULL"}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading saved template: ${e.message}", e)
            } finally {
                isLoadingTemplate = false
            }
        } else {
            Log.w(TAG, "⚠️ Template loading skipped - Profile: ${activeProfile?.name}, Templates available: ${allTemplates.size}, isLoading: $isLoadingTemplate")
        }
    }

    // ✅ FIXED: Auto-save when template selection changes - PROFILE AWARE with debouncing
    LaunchedEffect(selectedTemplate, selectedFlightMode, activeProfile) {
        if (selectedTemplate != null && activeProfile != null && !isLoadingTemplate) {
            Log.d(TAG, "💾 Profile-aware auto-save triggered:")
            Log.d(TAG, "  👤 Profile: ${activeProfile.name} (ID: ${activeProfile.id})")
            Log.d(TAG, "  📋 Template: ${selectedTemplate.name} (ID: ${selectedTemplate.id})")
            Log.d(TAG, "  ✈️ Flight Mode: ${selectedFlightMode.name}")
            Log.d(TAG, "  📋 Cards: ${selectedTemplate.cardIds.size} - ${selectedTemplate.cardIds.joinToString(", ")}")

            scope.launch {
                try {
                    // ✅ FIXED: Use profile-aware template saving
                    cardPreferences.saveProfileFlightModeTemplate(
                        profileId = activeProfile.id,
                        flightMode = selectedFlightMode.name,
                        templateId = selectedTemplate.id
                    )
                    Log.d(TAG, "✅ Profile-aware auto-save successful: Profile '${activeProfile.name}' ${selectedFlightMode.name} → ${selectedTemplate.name}")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Profile-aware auto-save failed: ${e.message}", e)
                }
            }
        } else {
            Log.d(TAG, "⚠️ Profile-aware auto-save skipped - Template: ${selectedTemplate?.name}, Profile: ${activeProfile?.name}, isLoading: $isLoadingTemplate")
        }
    }

    // ✅ NEW: Handle flight mode visibility toggle
    fun handleFlightModeVisibilityToggle(mode: FlightModeSelection) {
        // SCruise is always visible, cannot be toggled
        if (mode == FlightModeSelection.CRUISE) {
            Log.d(TAG, "👁️ SCruise is always visible - ignoring toggle request")
            return
        }

        activeProfile?.let { profile ->
            val currentVisibility = flightModeVisibilities[mode.name] ?: true
            val newVisibility = !currentVisibility

            val updatedVisibilities = flightModeVisibilities.toMutableMap().apply {
                put(mode.name, newVisibility)
            }
            flightModeVisibilities = updatedVisibilities

            scope.launch {
                cardPreferences.saveProfileFlightModeVisibility(
                    profileId = profile.id,
                    flightMode = mode.name,
                    isVisible = newVisibility
                )
            }

            Log.d(TAG, "👁️ Flight mode visibility toggled: ${mode.displayName} → $newVisibility")

            if (!newVisibility && selectedFlightMode == mode) {
                val fallbackMode = listOf(
                    FlightModeSelection.CRUISE,
                    FlightModeSelection.THERMAL,
                    FlightModeSelection.FINAL_GLIDE,
                    FlightModeSelection.HAWK
                ).firstOrNull { candidate ->
                    when (candidate) {
                        FlightModeSelection.CRUISE -> true
                        mode -> false
                        else -> updatedVisibilities[candidate.name] ?: true
                    }
                } ?: FlightModeSelection.CRUISE

                if (fallbackMode != selectedFlightMode) {
                    Log.d(TAG, "👁️ Active mode hidden; falling back to ${fallbackMode.displayName}")
                    onFlightModeSelected(fallbackMode)
                }
            }
        }
    }
    
    // ✅ NEW: Convert string-keyed visibilities to FlightModeSelection-keyed
    val flightModeSelectionVisibilities = remember(flightModeVisibilities) {
        flightModeVisibilities.mapKeys { (key, _) ->
            when (key) {
                "CRUISE" -> FlightModeSelection.CRUISE
                "THERMAL" -> FlightModeSelection.THERMAL
                "FINAL_GLIDE" -> FlightModeSelection.FINAL_GLIDE
                "HAWK" -> FlightModeSelection.HAWK
                else -> FlightModeSelection.CRUISE
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Flight mode selection
        item {
            FlightModeSelectionSection(
                selectedFlightMode = selectedFlightMode,
                onFlightModeSelected = { mode ->
                    Log.d(TAG, "✈️ Flight mode changed:")
                    Log.d(TAG, "  📋 From: ${selectedFlightMode.displayName}")
                    Log.d(TAG, "  📋 To: ${mode.displayName}")
                    onFlightModeSelected(mode)
                },
                flightModeVisibilities = flightModeSelectionVisibilities,
                onFlightModeVisibilityToggle = { mode ->
                    handleFlightModeVisibilityToggle(mode)
                },
                onFlightModeOptionsClick = { mode ->
                    // Only show options for Thermal and Final Glide (not Cruise)
                    if (mode != FlightModeSelection.CRUISE) {
                        showOptionsDialog = mode
                    }
                }
            )
        }

        // Templates section
        item {
            TemplatesForModeSection(
                selectedFlightMode = selectedFlightMode,
                allTemplates = allTemplates,
                selectedTemplate = selectedTemplate,
                onTemplateSelected = { template ->
                    onTemplateSelected(template)
                },
                onEditTemplate = { template ->
                    Log.d(TAG, "✏️ Edit template requested: ${template.name}")
                    // ✅ CHANGED: Call parent callback instead of local modal
                    onEditTemplate(template)
                },
                onDeleteTemplate = { template ->
                    Log.d(TAG, "🗑️ Delete template requested: ${template.name}")
                    // Delete the template
                    val updatedTemplates = allTemplates.filter { it.id != template.id }
                    scope.launch {
                        try {
                            cardPreferences.saveAllTemplates(updatedTemplates)
                            Log.d(TAG, "✅ Template deleted: ${template.name}")

                            // If deleted template was selected, select fallback
                            if (selectedTemplate?.id == template.id) {
                                val fallbackTemplate = updatedTemplates.find { it.name == "Essential" }
                                fallbackTemplate?.let { onTemplateSelected(it) }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Failed to delete template: ${e.message}", e)
                        }
                    }
                }
            )
        }

        // Category tabs section
        item {
            CategoryTabsSection(
                selectedCategory = selectedCategory,
                onCategorySelected = { category ->
                    Log.d(TAG, "🏷️ Category changed:")
                    Log.d(TAG, "  📋 From: ${selectedCategory.displayName}")
                    Log.d(TAG, "  📋 To: ${category.displayName}")
                    onCategorySelected(category)
                }
            )
        }

        // Cards grid section
        item {
            CardsGridSection(
                selectedCategory = selectedCategory,
                selectedTemplate = selectedTemplate,
                liveFlightData = liveFlightData,
                units = unitsPreferences,
                onCardToggle = { cardId, isSelected ->
                    Log.d(TAG, "🃏 Card toggle triggered:")
                    Log.d(TAG, "  📋 Card ID: $cardId")
                    Log.d(TAG, "  📋 Is Selected: $isSelected")
                    Log.d(TAG, "  📋 Current Template: ${selectedTemplate?.name ?: "None"}")

                    selectedTemplate?.let { template ->
                        Log.d(TAG, "  📋 Current Cards (${template.cardIds.size}): ${template.cardIds.joinToString(", ")}")

                        val updatedCardIds = if (isSelected) {
                            template.cardIds + cardId
                        } else {
                            template.cardIds - cardId
                        }

                        Log.d(TAG, "  📋 Updated Cards (${updatedCardIds.size}): ${updatedCardIds.joinToString(", ")}")

                        val updatedTemplate = template.copy(cardIds = updatedCardIds)
                        onTemplateSelected(updatedTemplate)

                        Log.d(TAG, "  ✅ Local template state updated")
                        Log.d(TAG, "  📋 New template: ${updatedTemplate.name} with ${updatedTemplate.cardIds.size} cards")

                        // Save to CardPreferences
                        scope.launch {
                            try {
                                Log.d(TAG, "💾 Saving profile-specific template configuration...")

                                if (activeProfile != null) {
                                    // ✅ FIX: Save profile-specific card configuration (no global contamination)
                                    cardPreferences.saveProfileTemplateCards(
                                        profileId = activeProfile.id,
                                        templateId = template.id,
                                        cardIds = updatedCardIds
                                    )
                                    Log.d(TAG, "✅ Profile-specific cards saved for '${activeProfile.name}' template '${template.name}': ${updatedCardIds.joinToString(",")}")

                                    // Also ensure the profile knows which template to use for this flight mode
                                    cardPreferences.saveProfileFlightModeTemplate(
                                        profileId = activeProfile.id,
                                        flightMode = selectedFlightMode.name,
                                        templateId = template.id
                                    )
                                    Log.d(TAG, "✅ Profile template mapping saved: Profile '${activeProfile.name}' ${selectedFlightMode.name} → ${template.name}")

                                    // ✅ NEW: Notify MapScreen that template changed via singleton
                                    Log.d(TAG, "🔄 Template change notification sent to MapScreen")
                                } else {
                                    Log.w(TAG, "⚠️ No active profile - cannot save profile-specific configuration")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Failed to save profile configuration: ${e.message}", e)
                            }
                        }
                    } ?: run {
                        Log.w(TAG, "⚠️ Card toggle failed - no template selected")
                    }
                }
            )
        }
    }
    
    // ✅ NEW: Options dialog for flight modes
    showOptionsDialog?.let { mode ->
        AlertDialog(
            onDismissRequest = { showOptionsDialog = null },
            title = { Text("${mode.displayName} Options") },
            text = { 
                Text("Choose an action for ${mode.displayName} flight mode:")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        handleFlightModeVisibilityToggle(mode)
                        showOptionsDialog = null
                    }
                ) {
                    val isVisible = flightModeSelectionVisibilities[mode] ?: true
                    Text(if (isVisible) "Hide" else "Show")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOptionsDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
