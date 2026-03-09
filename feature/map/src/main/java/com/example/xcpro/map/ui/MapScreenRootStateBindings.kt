package com.example.xcpro.map.ui

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
import com.example.xcpro.airspace.AirspaceUiState
import com.example.xcpro.airspace.AirspaceViewModel
import com.example.xcpro.map.MapScreenViewModel
import com.example.xcpro.profiles.ProfileIdResolver
import com.example.xcpro.profiles.ProfileUiState
import com.example.xcpro.profiles.ProfileViewModel
import com.example.xcpro.screens.navdrawer.lookandfeel.CardStyle
import com.example.xcpro.screens.navdrawer.lookandfeel.LookAndFeelViewModel

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
    LaunchedEffect(activeProfileId) {
        lookAndFeelViewModel.setProfileId(activeProfileId)
    }
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
