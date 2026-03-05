package com.example.ui1.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.xcpro.map.R
import com.example.xcpro.navigation.SettingsRoutes
import com.example.xcpro.screens.navdrawer.HotspotsSettingsContent
import com.example.xcpro.screens.navdrawer.HotspotsSettingsViewModel
import com.example.xcpro.screens.navdrawer.OgnSettingsContent
import com.example.xcpro.screens.navdrawer.OgnSettingsViewModel
import com.example.xcpro.screens.navdrawer.OrientationSettingsContent
import com.example.xcpro.screens.navdrawer.OrientationSettingsSheet
import com.example.xcpro.screens.navdrawer.OrientationSettingsViewModel
import com.example.xcpro.screens.navdrawer.SettingsTopAppBar
import com.example.xcpro.screens.navdrawer.ThermallingSettingsContent
import com.example.xcpro.screens.navdrawer.ThermallingSettingsViewModel
import com.example.xcpro.screens.navdrawer.WeatherSettingsSheet
import kotlinx.coroutines.launch


/**
 * Settings screen sub-sheet composables extracted for global file-size compliance.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProximitySettingsSheet(
    onDismiss: () -> Unit,
    onNavigateToDrawer: () -> Unit,
    onNavigateToMap: () -> Unit,
    onOpenOgn: () -> Unit,
    onOpenHotspots: () -> Unit,
    onOpenLookAndFeel: () -> Unit,
    onOpenColors: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SettingsTopAppBar(
                title = "Proximity",
                onNavigateUp = onDismiss,
                onSecondaryNavigate = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Proximity Settings",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Manage traffic behavior, icon style, and color visibility.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ProximitySheetAction(
                    title = "OGN Traffic",
                    description = "OGN overlay, size, and receive controls.",
                    icon = Icons.Default.Flight,
                    onClick = onOpenOgn
                )
                ProximitySheetAction(
                    title = "Hotspots",
                    description = "Thermal hotspot visibility and retention.",
                    icon = Icons.Default.Speed,
                    onClick = onOpenHotspots
                )
                ProximitySheetAction(
                    title = "Look & Feel",
                    description = "Global style for map and cards.",
                    icon = Icons.Outlined.Style,
                    onClick = onOpenLookAndFeel
                )
                ProximitySheetAction(
                    title = "Colors",
                    description = "Fine tune color themes and contrast.",
                    icon = Icons.Default.GridView,
                    onClick = onOpenColors
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

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
internal fun OgnSettingsSubSheet(
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
            SettingsTopAppBar(
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
internal fun HotspotsSettingsSubSheet(
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
            SettingsTopAppBar(
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

@Composable
internal fun ProximitySheetAction(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
