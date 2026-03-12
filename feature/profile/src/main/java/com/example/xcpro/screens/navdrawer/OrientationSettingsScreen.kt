package com.example.xcpro.screens.navdrawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.map.domain.MapShiftBiasMode
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Dedicated Orientation settings screen accessed from General settings.
 * Mirrors the Navboxes top bar (back arrow, drawer shortcut, map icon) per UX request.
 */
const val ORIENTATION_SETTINGS_SHEET_TAG = "orientation_settings_sheet"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrientationSettingsScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    viewModel: OrientationSettingsViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    OrientationSettingsSheet(
        onDismissRequest = { navController.navigateUp() },
        onNavigateUp = { navController.navigateUp() },
        onSecondaryNavigate = {
            scope.launch {
                navController.popBackStack("map", inclusive = false)
                drawerState.open()
            }
        },
        onNavigateToMap = {
            scope.launch {
                drawerState.close()
                navController.popBackStack("map", inclusive = false)
            }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrientationSettingsSheet(
    onDismissRequest: () -> Unit,
    onNavigateUp: (() -> Unit)?,
    onSecondaryNavigate: (() -> Unit)?,
    onNavigateToMap: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.testTag(ORIENTATION_SETTINGS_SHEET_TAG)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            SettingsTopAppBar(
                title = "Orientation",
                onNavigateUp = onNavigateUp,
                onSecondaryNavigate = onSecondaryNavigate,
                onNavigateToMap = onNavigateToMap
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                content()
            }
        }
    }
}

@Composable
fun OrientationSettingsContent(
    uiState: OrientationSettingsUiState,
    onSetCruiseMode: (MapOrientationMode) -> Unit,
    onSetCirclingMode: (MapOrientationMode) -> Unit,
    onSetGliderScreenPercent: (Int) -> Unit,
    onSetMapShiftBiasMode: (MapShiftBiasMode) -> Unit,
    onSetMapShiftBiasStrength: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OrientationModeCard(
                title = "Cruise / Final Glide",
                description = "Applies when flying straight, final glide, or navigating menus.",
                selectedMode = uiState.cruiseMode,
                onModeSelected = onSetCruiseMode
            )
        }
        item {
            OrientationModeCard(
                title = "Thermal / Circling",
                description = "Used while thermalling or whenever flight mode switches to Thermal.",
                selectedMode = uiState.circlingMode,
                onModeSelected = onSetCirclingMode
            )
        }
        item {
            GliderPositionCard(
                percentFromBottom = uiState.gliderScreenPercent,
                onPercentChanged = onSetGliderScreenPercent
            )
        }
        item {
            MapShiftBiasCard(
                mode = uiState.mapShiftBiasMode,
                strength = uiState.mapShiftBiasStrength,
                onModeChanged = onSetMapShiftBiasMode,
                onStrengthChanged = onSetMapShiftBiasStrength
            )
        }
    }
}

@Composable
private fun OrientationModeCard(
    title: String,
    description: String,
    selectedMode: MapOrientationMode,
    onModeSelected: (MapOrientationMode) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            listOf(
                MapOrientationMode.NORTH_UP,
                MapOrientationMode.TRACK_UP,
                MapOrientationMode.HEADING_UP
            ).forEach { mode ->
                OrientationModeRow(
                    title = when (mode) {
                        MapOrientationMode.NORTH_UP -> "North Up"
                        MapOrientationMode.TRACK_UP -> "Track Up"
                        MapOrientationMode.HEADING_UP -> "Heading Up"
                    },
                    description = when (mode) {
                        MapOrientationMode.NORTH_UP -> "Never rotate the map."
                        MapOrientationMode.TRACK_UP -> "Rotate map to match GPS course."
                        MapOrientationMode.HEADING_UP -> "Rotate map to match sensor heading."
                    },
                    selected = selectedMode == mode,
                    onSelect = { onModeSelected(mode) }
                )
            }
        }
    }
}

@Composable
private fun GliderPositionCard(
    percentFromBottom: Int,
    onPercentChanged: (Int) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Glider vertical position",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val percentFromTop = 100 - percentFromBottom
            Text(
                text = "Offsets the aircraft icon while auto-centering. " +
                    "$percentFromBottom% from bottom - $percentFromTop% from top.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Slider(
                value = percentFromBottom.toFloat(),
                onValueChange = {
                    val snapped = it.roundToInt().coerceIn(10, 50)
                    if (snapped != percentFromBottom) {
                        onPercentChanged(snapped)
                    }
                },
                valueRange = 10f..50f
            )
        }
    }
}

@Composable
private fun MapShiftBiasCard(
    mode: MapShiftBiasMode,
    strength: Double,
    onModeChanged: (MapShiftBiasMode) -> Unit,
    onStrengthChanged: (Double) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Directional look-ahead",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Shifts the map forward in North Up to show more ahead. " +
                    "Disabled in Thermal/Circling and when Track Up or Heading Up is selected.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            listOf(
                MapShiftBiasMode.NONE to "Off",
                MapShiftBiasMode.TRACK to "Track"
            ).forEach { (itemMode, label) ->
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = mode == itemMode, onClick = { onModeChanged(itemMode) })
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val strengthPercent = (strength * 100.0).roundToInt().coerceIn(0, 100)
            Text(
                text = "Strength: $strengthPercent%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Slider(
                value = strengthPercent.toFloat(),
                onValueChange = {
                    val snapped = it.roundToInt().coerceIn(0, 100)
                    val newStrength = snapped / 100.0
                    if (newStrength != strength) {
                        onStrengthChanged(newStrength)
                    }
                },
                valueRange = 0f..100f,
                enabled = mode != MapShiftBiasMode.NONE
            )
        }
    }
}

@Composable
private fun OrientationModeRow(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
