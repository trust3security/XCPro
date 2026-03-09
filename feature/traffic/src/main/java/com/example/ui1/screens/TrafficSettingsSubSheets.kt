package com.example.ui1.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.xcpro.screens.navdrawer.AdsbSettingsScreen
import com.example.xcpro.screens.navdrawer.HotspotsSettingsContent
import com.example.xcpro.screens.navdrawer.HotspotsSettingsViewModel
import com.example.xcpro.screens.navdrawer.OgnSettingsContent
import com.example.xcpro.screens.navdrawer.OgnSettingsViewModel
import com.example.xcpro.screens.navdrawer.TrafficSettingsTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrafficRouteSubSheetContainer(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        content()
    }
}

@Composable
fun AdsbSettingsSubSheet(
    navController: NavHostController,
    drawerState: DrawerState,
    onDismiss: () -> Unit,
    onNavigateToDrawer: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    TrafficRouteSubSheetContainer(onDismiss = onDismiss) {
        AdsbSettingsScreen(
            navController = navController,
            drawerState = drawerState,
            onNavigateUp = onDismiss,
            onSecondaryNavigate = onNavigateToDrawer,
            onNavigateToMap = onNavigateToMap
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OgnSettingsSubSheet(
    onDismiss: () -> Unit,
    onNavigateToDrawer: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    val viewModel: OgnSettingsViewModel = hiltViewModel()
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
            TrafficSettingsTopAppBar(
                title = "OGN",
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
                OgnSettingsContent(
                    uiState = uiState,
                    onSetOgnOverlayEnabled = viewModel::setOgnOverlayEnabled,
                    onSetIconSizePx = viewModel::setIconSizePx,
                    onSetReceiveRadiusKm = viewModel::setReceiveRadiusKm,
                    onSetAutoReceiveRadiusEnabled = viewModel::setAutoReceiveRadiusEnabled,
                    onSetDisplayUpdateMode = viewModel::setDisplayUpdateMode,
                    onOwnFlarmDraftChanged = viewModel::onOwnFlarmDraftChanged,
                    onCommitOwnFlarmDraft = viewModel::commitOwnFlarmDraft,
                    onOwnIcaoDraftChanged = viewModel::onOwnIcaoDraftChanged,
                    onCommitOwnIcaoDraft = viewModel::commitOwnIcaoDraft
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotspotsSettingsSubSheet(
    onDismiss: () -> Unit,
    onNavigateToDrawer: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    val viewModel: HotspotsSettingsViewModel = hiltViewModel()
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
            TrafficSettingsTopAppBar(
                title = "Hotspots",
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
                HotspotsSettingsContent(
                    uiState = uiState,
                    onSetRetentionHours = viewModel::setRetentionHours,
                    onSetDisplayPercent = viewModel::setDisplayPercent
                )
            }
        }
    }
}
