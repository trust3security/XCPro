package com.example.xcpro.screens.navdrawer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastSettingsScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    onNavigateUp: (() -> Unit)? = null,
    onSecondaryNavigate: (() -> Unit)? = null,
    onNavigateToMap: (() -> Unit)? = null
) {
    val viewModel: ForecastSettingsViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    val opacity by viewModel.opacity.collectAsStateWithLifecycle()
    val windOverlayScale by viewModel.windOverlayScale.collectAsStateWithLifecycle()
    val windDisplayMode by viewModel.windDisplayMode.collectAsStateWithLifecycle()
    val selectedRegion by viewModel.selectedRegion.collectAsStateWithLifecycle()
    val authConfirmation by viewModel.authConfirmation.collectAsStateWithLifecycle()
    val authReturnCode by viewModel.authReturnCode.collectAsStateWithLifecycle()
    val authChecking by viewModel.authChecking.collectAsStateWithLifecycle()
    val savedCredentials by viewModel.savedCredentials.collectAsStateWithLifecycle()
    val credentialsStatus by viewModel.credentialsStatus.collectAsStateWithLifecycle()
    val credentialStorageMode by viewModel.credentialStorageMode.collectAsStateWithLifecycle()
    val volatileFallbackAllowed by viewModel.volatileFallbackAllowed.collectAsStateWithLifecycle()
    val navigateUpAction: () -> Unit = onNavigateUp ?: {
        navController.navigateUp()
        Unit
    }
    val secondaryNavigateAction: () -> Unit = onSecondaryNavigate ?: {
        scope.launch {
            navController.popBackStack("map", inclusive = false)
            drawerState.open()
        }
        Unit
    }
    val navigateToMapAction: () -> Unit = onNavigateToMap ?: {
        scope.launch {
            drawerState.close()
            navController.popBackStack("map", inclusive = false)
        }
        Unit
    }

    Scaffold(
        topBar = {
            SettingsTopAppBar(
                title = "SkySight",
                onNavigateUp = navigateUpAction,
                onSecondaryNavigate = secondaryNavigateAction,
                onNavigateToMap = navigateToMapAction
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            ForecastSettingsContent(
                state = ForecastSettingsContentState(
                    opacity = opacity,
                    windOverlayScale = windOverlayScale,
                    windDisplayMode = windDisplayMode,
                    windDisplayModes = viewModel.windDisplayModes,
                    selectedRegion = selectedRegion,
                    regionOptions = viewModel.regionOptions,
                    authConfirmation = authConfirmation,
                    authReturnCode = authReturnCode,
                    authChecking = authChecking,
                    savedCredentials = savedCredentials,
                    credentialsStatus = credentialsStatus,
                    credentialStorageMode = credentialStorageMode,
                    volatileFallbackAllowed = volatileFallbackAllowed
                ),
                onOpacityCommitted = viewModel::setOpacity,
                onWindDisplayModeSelected = viewModel::setWindDisplayMode,
                onWindOverlayScaleCommitted = viewModel::setWindOverlayScale,
                onSelectedRegion = viewModel::setSelectedRegion,
                onVolatileFallbackAllowedChanged = viewModel::setVolatileFallbackAllowed,
                onSaveCredentials = viewModel::saveCredentials,
                onClearCredentials = viewModel::clearCredentials,
                onVerifyCredentials = viewModel::verifyCredentials,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }
    }
}
