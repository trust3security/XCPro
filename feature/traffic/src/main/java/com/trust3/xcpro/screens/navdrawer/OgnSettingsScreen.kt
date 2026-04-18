package com.trust3.xcpro.screens.navdrawer

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.trust3.xcpro.ogn.OGN_ICON_SIZE_MAX_PX
import com.trust3.xcpro.ogn.OGN_ICON_SIZE_MIN_PX
import com.trust3.xcpro.ogn.OGN_RECEIVE_RADIUS_MAX_KM
import com.trust3.xcpro.ogn.OGN_RECEIVE_RADIUS_MIN_KM
import com.trust3.xcpro.ogn.OgnDisplayUpdateMode
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OgnSettingsScreen(
    navController: NavHostController,
    drawerState: DrawerState
) {
    val viewModel: OgnSettingsViewModel = hiltViewModel()
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
            TrafficSettingsTopAppBar(
                title = "OGN",
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

@Composable
internal fun OgnSettingsContent(
    uiState: OgnSettingsUiState,
    onSetOgnOverlayEnabled: (Boolean) -> Unit,
    onSetIconSizePx: (Int) -> Unit,
    onSetReceiveRadiusKm: (Int) -> Unit,
    onSetAutoReceiveRadiusEnabled: (Boolean) -> Unit,
    onSetDisplayUpdateMode: (OgnDisplayUpdateMode) -> Unit,
    onOwnFlarmDraftChanged: (String) -> Unit,
    onCommitOwnFlarmDraft: () -> Unit,
    onOwnIcaoDraftChanged: (String) -> Unit,
    onCommitOwnIcaoDraft: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ognOverlayEnabled = uiState.ognOverlayEnabled
    val showSciaEnabled = uiState.showSciaEnabled
    val iconSizePx = uiState.iconSizePx
    val receiveRadiusKm = uiState.receiveRadiusKm
    val autoReceiveRadiusEnabled = uiState.autoReceiveRadiusEnabled
    val displayUpdateMode = uiState.displayUpdateMode
    val displayModes = OgnDisplayUpdateMode.sliderModes

    var sliderValue by remember { mutableStateOf(iconSizePx.toFloat()) }
    var receiveRadiusSliderValue by remember { mutableStateOf(receiveRadiusKm.toFloat()) }
    var displayModeSliderValue by remember {
        mutableStateOf(OgnDisplayUpdateMode.toSliderIndex(displayUpdateMode).toFloat())
    }

    LaunchedEffect(iconSizePx) {
        sliderValue = iconSizePx.toFloat()
    }
    LaunchedEffect(receiveRadiusKm) {
        receiveRadiusSliderValue = receiveRadiusKm.toFloat()
    }
    LaunchedEffect(displayUpdateMode) {
        displayModeSliderValue = OgnDisplayUpdateMode.toSliderIndex(displayUpdateMode).toFloat()
    }

    val displayModeDraft = OgnDisplayUpdateMode.fromSliderIndex(displayModeSliderValue.roundToInt())

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
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
            Column(modifier = Modifier.padding(16.dp)) {
                Text("OGN traffic", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Enable OGN traffic overlays on the map.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable OGN traffic", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = ognOverlayEnabled,
                        onCheckedChange = onSetOgnOverlayEnabled,
                        enabled = !showSciaEnabled
                    )
                }
                val helperText = if (showSciaEnabled && ognOverlayEnabled) {
                    "Show Scia is enabled in the Scia tab. Disable it there before turning OGN traffic off."
                } else if (ognOverlayEnabled) {
                    "OGN traffic is currently enabled."
                } else {
                    "OGN traffic is currently disabled."
                }
                Text(
                    text = helperText,
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
                Text("OGN", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Adjust the OGN glider icon size shown on the map.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "OGN icon size: ${sliderValue.roundToInt()} px",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = sliderValue,
                    onValueChange = { value ->
                        val snapped = value.roundToInt().coerceIn(
                            OGN_ICON_SIZE_MIN_PX,
                            OGN_ICON_SIZE_MAX_PX
                        )
                        sliderValue = snapped.toFloat()
                    },
                    onValueChangeFinished = {
                        val snapped = sliderValue.roundToInt().coerceIn(
                            OGN_ICON_SIZE_MIN_PX,
                            OGN_ICON_SIZE_MAX_PX
                        )
                        if (snapped != iconSizePx) {
                            onSetIconSizePx(snapped)
                        }
                    },
                    valueRange = OGN_ICON_SIZE_MIN_PX.toFloat()..OGN_ICON_SIZE_MAX_PX.toFloat(),
                    steps = OGN_ICON_SIZE_MAX_PX - OGN_ICON_SIZE_MIN_PX - 1
                )
                Text(
                    text = "Minimum ${OGN_ICON_SIZE_MIN_PX}px, maximum ${OGN_ICON_SIZE_MAX_PX}px.",
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
                Text("Receive radius", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Controls OGN traffic search radius around your position.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto radius (Advanced)", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Uses flight context first, then zoom, with 40/80/150/220 km buckets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoReceiveRadiusEnabled,
                        onCheckedChange = onSetAutoReceiveRadiusEnabled
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (autoReceiveRadiusEnabled) {
                        "Manual fallback: ${receiveRadiusSliderValue.roundToInt()} km"
                    } else {
                        "Radius: ${receiveRadiusSliderValue.roundToInt()} km"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = receiveRadiusSliderValue,
                    enabled = !autoReceiveRadiusEnabled,
                    onValueChange = { value ->
                        val snapped = snapRadiusKm(value)
                        receiveRadiusSliderValue = snapped.toFloat()
                    },
                    onValueChangeFinished = {
                        if (autoReceiveRadiusEnabled) return@Slider
                        val snapped = snapRadiusKm(receiveRadiusSliderValue).coerceIn(
                            OGN_RECEIVE_RADIUS_MIN_KM,
                            OGN_RECEIVE_RADIUS_MAX_KM
                        )
                        if (snapped != receiveRadiusKm) {
                            onSetReceiveRadiusKm(snapped)
                        }
                    },
                    valueRange = OGN_RECEIVE_RADIUS_MIN_KM.toFloat()..OGN_RECEIVE_RADIUS_MAX_KM.toFloat(),
                    steps = ((OGN_RECEIVE_RADIUS_MAX_KM - OGN_RECEIVE_RADIUS_MIN_KM) / OGN_RADIUS_STEP_KM) - 1
                )
                Text(
                    text = if (autoReceiveRadiusEnabled) {
                        "Auto mode updates ingest radius with cooldown/hysteresis. Manual value is kept as fallback."
                    } else {
                        "Lower radius reduces traffic volume and can save battery. Range ${OGN_RECEIVE_RADIUS_MIN_KM}-${OGN_RECEIVE_RADIUS_MAX_KM} km."
                    },
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
                Text("Display update speed", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Controls map draw cadence only. OGN ingest remains live.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Mode: ${displayModeDraft.displayLabel}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = displayModeSliderValue,
                    onValueChange = { value ->
                        val snappedIndex = value.roundToInt()
                            .coerceIn(0, displayModes.lastIndex)
                        displayModeSliderValue = snappedIndex.toFloat()
                    },
                    onValueChangeFinished = {
                        val snappedIndex = displayModeSliderValue.roundToInt()
                            .coerceIn(0, displayModes.lastIndex)
                        val mode = OgnDisplayUpdateMode.fromSliderIndex(snappedIndex)
                        if (mode != displayUpdateMode) {
                            onSetDisplayUpdateMode(mode)
                        }
                    },
                    valueRange = 0f..displayModes.lastIndex.toFloat(),
                    steps = displayModes.size - 2
                )
                Text(
                    text = displayModeDescription(displayModeDraft),
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
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Ownship OGN IDs", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Enter your own FLARM and ICAO24 hex IDs to suppress your own OGN marker, trails, and thermals.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.ownFlarmDraft,
                    onValueChange = onOwnFlarmDraftChanged,
                    label = { Text("Own FLARM ID (6 hex)") },
                    singleLine = true,
                    isError = uiState.ownFlarmError != null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onCommitOwnFlarmDraft() }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text(
                            text = uiState.ownFlarmError
                                ?: "Example: DDA85C. Leave blank to disable FLARM self-filter."
                        )
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onCommitOwnFlarmDraft,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save FLARM")
                    }
                    TextButton(
                        onClick = {
                            onOwnFlarmDraftChanged("")
                            onCommitOwnFlarmDraft()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.ownIcaoDraft,
                    onValueChange = onOwnIcaoDraftChanged,
                    label = { Text("Own ICAO24 (6 hex)") },
                    singleLine = true,
                    isError = uiState.ownIcaoError != null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onCommitOwnIcaoDraft() }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text(
                            text = uiState.ownIcaoError
                                ?: "Example: 4CA6A4. Leave blank to disable ICAO self-filter."
                        )
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onCommitOwnIcaoDraft,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save ICAO")
                    }
                    TextButton(
                        onClick = {
                            onOwnIcaoDraftChanged("")
                            onCommitOwnIcaoDraft()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear")
                    }
                }
            }
        }
    }
}

private fun displayModeDescription(mode: OgnDisplayUpdateMode): String =
    when (mode) {
        OgnDisplayUpdateMode.REAL_TIME -> "Fastest visual updates."
        OgnDisplayUpdateMode.BALANCED -> "Limits redraws to about once per second."
        OgnDisplayUpdateMode.BATTERY -> "Limits redraws to about once every three seconds."
    }

private const val OGN_RADIUS_STEP_KM = 5

private fun snapRadiusKm(rawValue: Float): Int {
    val rounded = rawValue.roundToInt()
    val snapped = ((rounded + (OGN_RADIUS_STEP_KM / 2)) / OGN_RADIUS_STEP_KM) * OGN_RADIUS_STEP_KM
    return snapped.coerceIn(OGN_RECEIVE_RADIUS_MIN_KM, OGN_RECEIVE_RADIUS_MAX_KM)
}
