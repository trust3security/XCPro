package com.example.xcpro.screens.navdrawer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.xcpro.map.R
import com.example.xcpro.thermalling.THERMALLING_DELAY_MAX_SECONDS
import com.example.xcpro.thermalling.THERMALLING_DELAY_MIN_SECONDS
import com.example.xcpro.thermalling.THERMALLING_ZOOM_LEVEL_MAX
import com.example.xcpro.thermalling.THERMALLING_ZOOM_LEVEL_MIN
import com.example.xcpro.thermalling.clampThermallingDelaySeconds
import com.example.xcpro.thermalling.clampThermallingZoomLevel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private const val THERMALLING_ZOOM_STEP = 0.1f
internal const val THERMALLING_TAG_SWITCH_ENABLED = "thermalling_switch_enabled"
internal const val THERMALLING_TAG_SWITCH_THERMAL_MODE = "thermalling_switch_thermal_mode"
internal const val THERMALLING_TAG_SWITCH_APPLY_ZOOM = "thermalling_switch_apply_zoom"
internal const val THERMALLING_TAG_SWITCH_REMEMBER_ZOOM = "thermalling_switch_remember_zoom"
internal const val THERMALLING_TAG_ENTER_DELAY_SLIDER = "thermalling_enter_delay_slider"
internal const val THERMALLING_TAG_EXIT_DELAY_SLIDER = "thermalling_exit_delay_slider"
internal const val THERMALLING_TAG_ZOOM_SLIDER = "thermalling_zoom_slider"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThermallingSettingsScreen(
    navController: NavHostController,
    drawerState: DrawerState
) {
    val viewModel: ThermallingSettingsViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = { navController.navigateUp() },
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            SettingsTopAppBar(
                title = stringResource(R.string.thermalling_title),
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

@Composable
internal fun ThermallingSettingsContent(
    uiState: ThermallingSettingsUiState,
    onSetEnabled: (Boolean) -> Unit,
    onSetSwitchToThermalMode: (Boolean) -> Unit,
    onSetZoomOnlyFallbackWhenThermalHidden: (Boolean) -> Unit,
    onSetEnterDelaySeconds: (Int) -> Unit,
    onSetExitDelaySeconds: (Int) -> Unit,
    onSetApplyZoomOnEnter: (Boolean) -> Unit,
    onSetThermalZoomLevel: (Float) -> Unit,
    onSetRememberManualThermalZoomInSession: (Boolean) -> Unit,
    onSetRestorePreviousModeOnExit: (Boolean) -> Unit,
    onSetRestorePreviousZoomOnExit: (Boolean) -> Unit
) {
    var enterDelaySliderValue by remember { mutableFloatStateOf(uiState.enterDelaySeconds.toFloat()) }
    var exitDelaySliderValue by remember { mutableFloatStateOf(uiState.exitDelaySeconds.toFloat()) }
    var thermalZoomSliderValue by remember { mutableFloatStateOf(uiState.thermalZoomLevel) }

    LaunchedEffect(uiState.enterDelaySeconds, uiState.exitDelaySeconds, uiState.thermalZoomLevel) {
        enterDelaySliderValue = uiState.enterDelaySeconds.toFloat()
        exitDelaySliderValue = uiState.exitDelaySeconds.toFloat()
        thermalZoomSliderValue = uiState.thermalZoomLevel
    }

    val enterDelaySliderContentDescription =
        stringResource(R.string.thermalling_enter_delay_slider_cd)
    val exitDelaySliderContentDescription =
        stringResource(R.string.thermalling_exit_delay_slider_cd)
    val zoomSliderContentDescription =
        stringResource(R.string.thermalling_zoom_slider_cd)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.thermalling_card_automation_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.thermalling_card_automation_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                ThermallingSwitchRow(
                    title = stringResource(R.string.thermalling_switch_enable_title),
                    description = stringResource(R.string.thermalling_switch_enable_description),
                    checked = uiState.enabled,
                    switchTestTag = THERMALLING_TAG_SWITCH_ENABLED,
                    onCheckedChange = onSetEnabled
                )
                Spacer(modifier = Modifier.height(8.dp))
                ThermallingSwitchRow(
                    title = stringResource(R.string.thermalling_switch_mode_title),
                    description = stringResource(R.string.thermalling_switch_mode_description),
                    checked = uiState.switchToThermalMode,
                    enabled = uiState.enabled,
                    switchTestTag = THERMALLING_TAG_SWITCH_THERMAL_MODE,
                    onCheckedChange = onSetSwitchToThermalMode
                )
                Spacer(modifier = Modifier.height(8.dp))
                ThermallingSwitchRow(
                    title = stringResource(R.string.thermalling_switch_zoom_fallback_title),
                    description = stringResource(R.string.thermalling_switch_zoom_fallback_description),
                    checked = uiState.zoomOnlyFallbackWhenThermalHidden,
                    enabled = uiState.enabled,
                    onCheckedChange = onSetZoomOnlyFallbackWhenThermalHidden
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.thermalling_card_timers_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.thermalling_card_timers_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(
                        R.string.thermalling_enter_delay_value,
                        enterDelaySliderValue.roundToInt()
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(THERMALLING_TAG_ENTER_DELAY_SLIDER)
                        .semantics { contentDescription = enterDelaySliderContentDescription },
                    value = enterDelaySliderValue,
                    enabled = uiState.enabled,
                    onValueChange = { value ->
                        enterDelaySliderValue =
                            clampThermallingDelaySeconds(value.roundToInt()).toFloat()
                    },
                    onValueChangeFinished = {
                        val snapped = clampThermallingDelaySeconds(
                            enterDelaySliderValue.roundToInt()
                        )
                        if (snapped != uiState.enterDelaySeconds) {
                            onSetEnterDelaySeconds(snapped)
                        }
                    },
                    valueRange = THERMALLING_DELAY_MIN_SECONDS.toFloat()..THERMALLING_DELAY_MAX_SECONDS.toFloat(),
                    steps = THERMALLING_DELAY_MAX_SECONDS - THERMALLING_DELAY_MIN_SECONDS - 1
                )
                Text(
                    text = stringResource(R.string.thermalling_enter_delay_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(
                        R.string.thermalling_exit_delay_value,
                        exitDelaySliderValue.roundToInt()
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(THERMALLING_TAG_EXIT_DELAY_SLIDER)
                        .semantics { contentDescription = exitDelaySliderContentDescription },
                    value = exitDelaySliderValue,
                    enabled = uiState.enabled,
                    onValueChange = { value ->
                        exitDelaySliderValue =
                            clampThermallingDelaySeconds(value.roundToInt()).toFloat()
                    },
                    onValueChangeFinished = {
                        val snapped = clampThermallingDelaySeconds(
                            exitDelaySliderValue.roundToInt()
                        )
                        if (snapped != uiState.exitDelaySeconds) {
                            onSetExitDelaySeconds(snapped)
                        }
                    },
                    valueRange = THERMALLING_DELAY_MIN_SECONDS.toFloat()..THERMALLING_DELAY_MAX_SECONDS.toFloat(),
                    steps = THERMALLING_DELAY_MAX_SECONDS - THERMALLING_DELAY_MIN_SECONDS - 1
                )
                Text(
                    text = stringResource(R.string.thermalling_exit_delay_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.thermalling_card_zoom_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.thermalling_card_zoom_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                ThermallingSwitchRow(
                    title = stringResource(R.string.thermalling_switch_apply_zoom_title),
                    description = stringResource(R.string.thermalling_switch_apply_zoom_description),
                    checked = uiState.applyZoomOnEnter,
                    enabled = uiState.enabled,
                    switchTestTag = THERMALLING_TAG_SWITCH_APPLY_ZOOM,
                    onCheckedChange = onSetApplyZoomOnEnter
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(
                        R.string.thermalling_zoom_value,
                        thermalZoomSliderValue.toDouble()
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(THERMALLING_TAG_ZOOM_SLIDER)
                        .semantics { contentDescription = zoomSliderContentDescription },
                    value = thermalZoomSliderValue,
                    enabled = uiState.enabled && uiState.applyZoomOnEnter,
                    onValueChange = { value ->
                        thermalZoomSliderValue = snapThermallingZoom(value)
                    },
                    onValueChangeFinished = {
                        val snapped = snapThermallingZoom(thermalZoomSliderValue)
                        if (snapped != uiState.thermalZoomLevel) {
                            onSetThermalZoomLevel(snapped)
                        }
                    },
                    valueRange = THERMALLING_ZOOM_LEVEL_MIN..THERMALLING_ZOOM_LEVEL_MAX
                )
                Text(
                    text = stringResource(
                        R.string.thermalling_zoom_range,
                        THERMALLING_ZOOM_LEVEL_MIN.toDouble(),
                        THERMALLING_ZOOM_LEVEL_MAX.toDouble()
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                ThermallingSwitchRow(
                    title = stringResource(R.string.thermalling_switch_remember_zoom_title),
                    description = stringResource(R.string.thermalling_switch_remember_zoom_description),
                    checked = uiState.rememberManualThermalZoomInSession,
                    enabled = uiState.enabled && uiState.applyZoomOnEnter,
                    switchTestTag = THERMALLING_TAG_SWITCH_REMEMBER_ZOOM,
                    onCheckedChange = onSetRememberManualThermalZoomInSession
                )
                Spacer(modifier = Modifier.height(8.dp))
                ThermallingSwitchRow(
                    title = stringResource(R.string.thermalling_switch_restore_mode_title),
                    description = stringResource(R.string.thermalling_switch_restore_mode_description),
                    checked = uiState.restorePreviousModeOnExit,
                    enabled = uiState.enabled,
                    onCheckedChange = onSetRestorePreviousModeOnExit
                )
                Spacer(modifier = Modifier.height(8.dp))
                ThermallingSwitchRow(
                    title = stringResource(R.string.thermalling_switch_restore_zoom_title),
                    description = stringResource(R.string.thermalling_switch_restore_zoom_description),
                    checked = uiState.restorePreviousZoomOnExit,
                    enabled = uiState.enabled,
                    onCheckedChange = onSetRestorePreviousZoomOnExit
                )
            }
        }
    }
}

@Composable
private fun ThermallingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    switchTestTag: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val switchModifier = if (switchTestTag == null) {
            Modifier.semantics { contentDescription = title }
        } else {
            Modifier
                .testTag(switchTestTag)
                .semantics { contentDescription = title }
        }
        Switch(
            modifier = switchModifier,
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

private fun snapThermallingZoom(value: Float): Float {
    val snapped = (value / THERMALLING_ZOOM_STEP).roundToInt() * THERMALLING_ZOOM_STEP
    return clampThermallingZoomLevel(snapped)
}
