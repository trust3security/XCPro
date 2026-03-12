package com.example.ui1.screens

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcpro.screens.navdrawer.WeGlideSettingsContent
import com.example.xcpro.screens.navdrawer.WeGlideSettingsTopAppBar
import com.example.xcpro.screens.navdrawer.WeGlideSettingsViewModel
import kotlinx.coroutines.flow.collect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeGlideSettingsSubSheet(
    onDismiss: () -> Unit,
    onNavigateToDrawer: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    val viewModel: WeGlideSettingsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is WeGlideSettingsViewModel.Event.LaunchAuthorization -> {
                    context.startActivity(Intent(Intent.ACTION_VIEW, event.uri))
                }
            }
        }
    }

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
            WeGlideSettingsTopAppBar(
                title = "WeGlide",
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
                WeGlideSettingsContent(
                    uiState = uiState,
                    onSetAutoUploadFinishedFlights = viewModel::setAutoUploadFinishedFlights,
                    onSetUploadOnWifiOnly = viewModel::setUploadOnWifiOnly,
                    onSetRetryOnMobileData = viewModel::setRetryOnMobileData,
                    onSetShowCompletionNotification = viewModel::setShowCompletionNotification,
                    onSetDebugEnabled = viewModel::setDebugEnabled,
                    onConnect = viewModel::connect,
                    onDisconnect = viewModel::disconnect,
                    onRefreshAircraft = viewModel::refreshAircraft,
                    onSetProfileAircraftMapping = viewModel::setProfileAircraftMapping,
                    onClearProfileAircraftMapping = viewModel::clearProfileAircraftMapping
                )
            }
        }
    }
}
