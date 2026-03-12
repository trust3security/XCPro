package com.example.ui1.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.xcpro.map.R
import com.example.xcpro.screens.navdrawer.OrientationSettingsContent
import com.example.xcpro.screens.navdrawer.OrientationSettingsSheet
import com.example.xcpro.screens.navdrawer.OrientationSettingsViewModel
import com.example.xcpro.screens.navdrawer.SettingsTopAppBar
import com.example.xcpro.screens.navdrawer.ThermallingSettingsContent
import com.example.xcpro.screens.navdrawer.ThermallingSettingsViewModel
import com.example.xcpro.screens.navdrawer.WeatherSettingsSheet


/**
 * Settings screen sub-sheet composables extracted for global file-size compliance.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WeatherSettingsSubSheet(
    onDismiss: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    WeatherSettingsSheet(
        onDismissRequest = onDismiss,
        onNavigateUp = onDismiss,
        onSecondaryNavigate = null,
        onNavigateToMap = onNavigateToMap
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThermallingSettingsSubSheet(
    onDismiss: () -> Unit,
    onNavigateToDrawer: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    val viewModel: ThermallingSettingsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            SettingsTopAppBar(
                title = stringResource(R.string.thermalling_title),
                onNavigateUp = onDismiss,
                onSecondaryNavigate = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                ThermallingSettingsContent(
                    uiState = uiState,
                    onSetEnabled = viewModel::setEnabled,
                    onSetSwitchToThermalMode = viewModel::setSwitchToThermalMode,
                    onSetZoomOnlyFallbackWhenThermalHidden = viewModel::setZoomOnlyFallbackWhenThermalHidden,
                    onSetEnterDelaySeconds = viewModel::setEnterDelaySeconds,
                    onSetExitDelaySeconds = viewModel::setExitDelaySeconds,
                    onSetApplyZoomOnEnter = viewModel::setApplyZoomOnEnter,
                    onSetThermalZoomLevel = viewModel::setThermalZoomLevel,
                    onSetRememberManualThermalZoomInSession = viewModel::setRememberManualThermalZoomInSession,
                    onSetRestorePreviousModeOnExit = viewModel::setRestorePreviousModeOnExit,
                    onSetRestorePreviousZoomOnExit = viewModel::setRestorePreviousZoomOnExit
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OrientationSettingsSubSheet(
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

