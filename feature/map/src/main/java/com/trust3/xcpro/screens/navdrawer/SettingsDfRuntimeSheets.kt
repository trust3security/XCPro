package com.example.ui1.screens

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trust3.xcpro.screens.navdrawer.OrientationSettingsContent
import com.trust3.xcpro.screens.navdrawer.OrientationSettingsSheet
import com.trust3.xcpro.screens.navdrawer.OrientationSettingsViewModel


/**
 * Settings screen sub-sheet composables extracted for global file-size compliance.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrientationSettingsSubSheet(
    onDismiss: () -> Unit,
    onNavigateToDrawer: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    val viewModel: OrientationSettingsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    OrientationSettingsSheet(
        onDismissRequest = onDismiss,
        onNavigateUp = onDismiss,
        onSecondaryNavigate = onNavigateToDrawer,
        onNavigateToMap = onNavigateToMap
    ) {
        OrientationSettingsContent(
            uiState = uiState,
            onSetCruiseMode = viewModel::setCruiseMode,
            onSetCirclingMode = viewModel::setCirclingMode,
            onSetGliderScreenPercent = viewModel::setGliderScreenPercent,
            onSetMapShiftBiasMode = viewModel::setMapShiftBiasMode,
            onSetMapShiftBiasStrength = viewModel::setMapShiftBiasStrength
        )
    }
}

