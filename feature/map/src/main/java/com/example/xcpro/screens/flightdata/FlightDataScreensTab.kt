package com.example.ui1.screens.flightmgmt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dfcards.CardCategory
import com.example.dfcards.CategoryTabsSection
import com.example.dfcards.CardsGridSection
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.FlightModeSelectionSection
import com.example.dfcards.FlightTemplate
import com.example.dfcards.RealTimeFlightData
import com.example.dfcards.TemplatesForModeSection
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.screens.navdrawer.UnitsSettingsViewModel
import com.example.xcpro.profiles.UserProfile
import kotlinx.coroutines.launch
import android.util.Log

@Composable
fun FlightDataScreensTab(
    activeProfile: UserProfile?,
    selectedFlightMode: FlightModeSelection,
    onFlightModeSelected: (FlightModeSelection) -> Unit,
    selectedCategory: CardCategory,
    onCategorySelected: (CardCategory) -> Unit,
    flightViewModel: FlightDataViewModel,
    onEditTemplate: (FlightTemplate) -> Unit,
    onDeleteTemplate: (FlightTemplate) -> Unit,
    liveFlightData: RealTimeFlightData? = null
) {
    val unitsViewModel: UnitsSettingsViewModel = hiltViewModel()
    val unitsPreferences by unitsViewModel.units.collectAsStateWithLifecycle(
        initialValue = UnitsPreferences()
    )
    val scope = rememberCoroutineScope()

    val availableTemplates by flightViewModel.availableTemplates.collectAsStateWithLifecycle()
    val profileModeTemplates by flightViewModel.profileModeTemplates.collectAsStateWithLifecycle()
    val profileModeCards by flightViewModel.profileModeCards.collectAsStateWithLifecycle()
    val profileModeVisibilities by flightViewModel.profileModeVisibilities.collectAsStateWithLifecycle()
    val profileId = activeProfile?.id

    val modeVisibilities = remember(profileModeVisibilities, profileId) {
        flightViewModel.flightModeVisibilitiesFor(profileId)
    }
    val resolvedTemplate = remember(
        profileId,
        selectedFlightMode,
        availableTemplates,
        profileModeTemplates,
        profileModeCards
    ) {
        flightViewModel.currentTemplateFor(profileId, selectedFlightMode)
    }
    val currentCardIds = remember(profileId, selectedFlightMode, profileModeCards) {
        flightViewModel.getProfileCards(profileId, selectedFlightMode)
    }
    val templateCardCounts = remember(profileId, selectedFlightMode, profileModeTemplates, profileModeCards) {
        flightViewModel.allTemplateCardCounts(profileId)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FlightModeSelectionSection(
                selectedFlightMode = selectedFlightMode,
                onFlightModeSelected = onFlightModeSelected,
                flightModeVisibilities = modeVisibilities,
                onFlightModeVisibilityToggle = { mode ->
                    flightViewModel.toggleProfileFlightModeVisibility(profileId, mode)
                }
            )
        }

        item {
            TemplatesForModeSection(
                selectedFlightMode = selectedFlightMode,
                allTemplates = availableTemplates,
                selectedTemplate = resolvedTemplate,
                templateCardCounts = templateCardCounts,
                onTemplateSelected = { template ->
                    if (resolvedTemplate?.id == template.id) return@TemplatesForModeSection
                    Log.d("FlightDataScreensTab", "templateSelected profile=${profileId ?: "null"} mode=$selectedFlightMode template=${template.id}")
                    scope.launch {
                        flightViewModel.selectProfileTemplate(profileId, selectedFlightMode, template)
                    }
                },
                onEditTemplate = onEditTemplate,
                onDeleteTemplate = { template ->
                    scope.launch {
                        flightViewModel.deleteTemplate(template.id)
                        onDeleteTemplate(template)
                    }
                },
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            CategoryTabsSection(
                selectedCategory = selectedCategory,
                onCategorySelected = onCategorySelected
            )
        }

        item {
            CardsGridSection(
                selectedCategory = selectedCategory,
                selectedTemplate = resolvedTemplate,
                liveFlightData = liveFlightData,
                units = unitsPreferences,
                selectedCardIds = currentCardIds,
                onCardToggle = { cardId, isSelected ->
                    val updated = if (isSelected) {
                        if (cardId in currentCardIds) currentCardIds else currentCardIds + cardId
                    } else {
                        currentCardIds.filterNot { it == cardId }
                    }
                    Log.d(
                        "FlightDataScreensTab",
                        "cardToggle profile=${profileId ?: "null"} mode=$selectedFlightMode card=$cardId " +
                            "selected=${isSelected} total=${updated.size}"
                    )
                    resolvedTemplate?.let { template ->
                        flightViewModel.setProfileTemplate(profileId, selectedFlightMode, template.id)
                    }
                    flightViewModel.setProfileCards(profileId, selectedFlightMode, updated)
                }
            )
        }
    }
}
