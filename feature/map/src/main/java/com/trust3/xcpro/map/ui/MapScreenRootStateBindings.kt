package com.trust3.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.dfcards.FlightDataViewModel
import com.trust3.xcpro.hawk.HAWK_VARIO_CARD_ID
import com.trust3.xcpro.airspace.AirspaceUiState
import com.trust3.xcpro.airspace.AirspaceViewModel
import com.trust3.xcpro.map.MapUiState
import com.trust3.xcpro.map.MapScreenViewModel
import com.trust3.xcpro.map.WindArrowUiState
import com.trust3.xcpro.map.toCardFlightModeSelection
import com.trust3.xcpro.profiles.ProfileIdResolver
import com.trust3.xcpro.profiles.ProfileUiState
import com.trust3.xcpro.profiles.ProfileViewModel
import com.trust3.xcpro.screens.navdrawer.lookandfeel.CardStyle
import com.trust3.xcpro.screens.navdrawer.lookandfeel.LookAndFeelViewModel
import com.trust3.xcpro.weglide.ui.WeGlideUploadPromptUiState

internal data class MapScreenFlightCardsBinding(
    val flightViewModel: FlightDataViewModel,
    val profileModeCards: Map<String, Map<FlightModeSelection, List<String>>>,
    val profileModeTemplates: Map<String, Map<FlightModeSelection, String>>,
    val activeTemplateId: String?
)

internal data class MapScreenProfileLookAndFeelBinding(
    val profileUiState: ProfileUiState,
    val activeProfileId: String,
    val cardStyle: CardStyle
)

internal data class MapScreenRootUiBinding(
    val mapUiState: MapUiState,
    val weGlideUploadPrompt: WeGlideUploadPromptUiState?,
    val windArrowState: WindArrowUiState,
    val showWindSpeedOnVario: Boolean,
    val hiddenCardIds: Set<String>,
    val currentFlightModeSelection: FlightModeSelection
)

@Composable
internal fun rememberMapScreenFlightCardsBinding(
    navController: NavHostController,
    mapViewModel: MapScreenViewModel
): MapScreenFlightCardsBinding {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val mapEntry = remember(navBackStackEntry) { navController.getBackStackEntry("map") }
    val flightViewModel: FlightDataViewModel = hiltViewModel(mapEntry)
    val profileModeCards by flightViewModel.profileModeCards.collectAsStateWithLifecycle()
    val profileModeTemplates by flightViewModel.profileModeTemplates.collectAsStateWithLifecycle()
    val activeTemplateId by flightViewModel.activeTemplateId.collectAsStateWithLifecycle()
    LaunchedEffect(flightViewModel) {
        mapViewModel.cardIngestionCoordinator.bindCards(flightViewModel)
    }
    return MapScreenFlightCardsBinding(
        flightViewModel = flightViewModel,
        profileModeCards = profileModeCards,
        profileModeTemplates = profileModeTemplates,
        activeTemplateId = activeTemplateId
    )
}

@Composable
internal fun rememberMapScreenProfileLookAndFeelBinding(): MapScreenProfileLookAndFeelBinding {
    val profileViewModel: ProfileViewModel = hiltViewModel()
    val lookAndFeelViewModel: LookAndFeelViewModel = hiltViewModel()
    val profileUiState by profileViewModel.uiState.collectAsStateWithLifecycle()
    val activeProfileId = ProfileIdResolver.canonicalOrDefault(profileUiState.activeProfile?.id)
    LaunchedEffect(activeProfileId) { lookAndFeelViewModel.setProfileId(activeProfileId) }
    val lookAndFeelUiState by lookAndFeelViewModel.uiState.collectAsStateWithLifecycle()
    val cardStyle = remember(lookAndFeelUiState.cardStyleId) {
        CardStyle.values().find { it.id == lookAndFeelUiState.cardStyleId } ?: CardStyle.default
    }
    return MapScreenProfileLookAndFeelBinding(
        profileUiState = profileUiState,
        activeProfileId = activeProfileId,
        cardStyle = cardStyle
    )
}

@Composable
internal fun rememberMapScreenAirspaceState(): AirspaceUiState {
    val airspaceViewModel: AirspaceViewModel = hiltViewModel()
    val airspaceState by airspaceViewModel.uiState.collectAsStateWithLifecycle()
    return airspaceState
}

@Composable
internal fun rememberMapScreenRootUiBinding(
    mapViewModel: MapScreenViewModel
): MapScreenRootUiBinding {
    val mapUiState by mapViewModel.uiState.collectAsStateWithLifecycle()
    val weGlideUploadPrompt by mapViewModel.weGlideUploadPrompt.collectAsStateWithLifecycle()
    val windArrowState by mapViewModel.windArrowState.collectAsStateWithLifecycle()
    val showWindSpeedOnVario by mapViewModel.showWindSpeedOnVario.collectAsStateWithLifecycle()
    val showHawkCard by mapViewModel.showHawkCard.collectAsStateWithLifecycle()
    val effectiveFlightMode by mapViewModel.effectiveFlightMode.collectAsStateWithLifecycle()
    val hiddenCardIds = remember(showHawkCard) {
        if (showHawkCard) emptySet() else setOf(HAWK_VARIO_CARD_ID)
    }

    return MapScreenRootUiBinding(
        mapUiState = mapUiState,
        weGlideUploadPrompt = weGlideUploadPrompt,
        windArrowState = windArrowState,
        showWindSpeedOnVario = showWindSpeedOnVario,
        hiddenCardIds = hiddenCardIds,
        currentFlightModeSelection = effectiveFlightMode.toCardFlightModeSelection()
    )
}
