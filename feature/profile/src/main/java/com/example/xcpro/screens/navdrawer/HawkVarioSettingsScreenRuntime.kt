package com.example.xcpro.screens.navdrawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.ui1.UIVariometer
import com.example.ui1.buildVarioDialConfig
import com.example.ui1.stripUnit
import com.example.xcpro.common.units.UnitsFormatter
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.units.VerticalSpeedMs
import com.example.xcpro.hawk.HawkConfidence
import com.example.xcpro.hawk.HawkVarioUiState
import com.example.xcpro.profiles.ProfileIdResolver
import com.example.xcpro.profiles.ProfileViewModel
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HawkVarioSettingsScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    viewModel: HawkVarioSettingsViewModel = hiltViewModel(),
    onNavigateUp: (() -> Unit)? = null,
    onSecondaryNavigate: (() -> Unit)? = null,
    onNavigateToMap: (() -> Unit)? = null
) {
    val profileViewModel: ProfileViewModel = hiltViewModel()
    val profileUiState by profileViewModel.uiState.collectAsStateWithLifecycle()
    val profileId = ProfileIdResolver.canonicalOrDefault(profileUiState.activeProfile?.id)
    LaunchedEffect(profileId) {
        viewModel.setProfileId(profileId)
    }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
                title = "HAWK Vario",
                onNavigateUp = navigateUpAction,
                onSecondaryNavigate = secondaryNavigateAction,
                onNavigateToMap = navigateToMapAction
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "General",
                style = MaterialTheme.typography.titleMedium
            )

            HawkVarioDisplayOptionsCard(
                enableHawkUi = uiState.enableHawkUi,
                onEnableHawkUiChange = viewModel::setEnableHawkUi,
                showHawkCard = uiState.showHawkCard,
                onShowHawkCardChange = viewModel::setShowHawkCard
            )

            HawkNeedleTuningCard(
                omegaMinHz = uiState.needleOmegaMinHz,
                omegaMaxHz = uiState.needleOmegaMaxHz,
                targetTauSec = uiState.needleTargetTauSec,
                driftTauMinSec = uiState.needleDriftTauMinSec,
                driftTauMaxSec = uiState.needleDriftTauMaxSec,
                onOmegaMinChange = viewModel::setNeedleOmegaMinHz,
                onOmegaMaxChange = viewModel::setNeedleOmegaMaxHz,
                onTargetTauChange = viewModel::setNeedleTargetTauSec,
                onDriftTauMinChange = viewModel::setNeedleDriftTauMinSec,
                onDriftTauMaxChange = viewModel::setNeedleDriftTauMaxSec
            )

            if (uiState.enableHawkUi) {
                HawkVarioPreviewCard(
                    state = uiState.hawkVarioUiState,
                    unitsPreferences = uiState.unitsPreferences,
                    omegaMinHz = uiState.needleOmegaMinHz,
                    omegaMaxHz = uiState.needleOmegaMaxHz,
                    targetTauSec = uiState.needleTargetTauSec,
                    driftTauMinSec = uiState.needleDriftTauMinSec,
                    driftTauMaxSec = uiState.needleDriftTauMaxSec
                )
                HawkLiveDataCard(uiState.hawkVarioUiState)
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "HAWK live data",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Enable HAWK UI to view live HAWK figures.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HawkVarioDisplayOptionsCard(
    enableHawkUi: Boolean,
    onEnableHawkUiChange: (Boolean) -> Unit,
    showHawkCard: Boolean,
    onShowHawkCardChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "HAWK display",
                style = MaterialTheme.typography.titleSmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Enable HAWK UI",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Show live HAWK figures on this screen",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = enableHawkUi,
                    onCheckedChange = onEnableHawkUiChange
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "HAWK Vario card",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Show the HAWK vario card in Variometers",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = showHawkCard,
                    onCheckedChange = onShowHawkCardChange
                )
            }
        }
    }
}

@Composable
private fun HawkVarioPreviewCard(
    state: HawkVarioUiState,
    unitsPreferences: UnitsPreferences,
    omegaMinHz: Double,
    omegaMaxHz: Double,
    targetTauSec: Double,
    driftTauMinSec: Double,
    driftTauMaxSec: Double
) {
    val dialConfig = remember(unitsPreferences) {
        buildVarioDialConfig(unitsPreferences)
    }
    val smoothedSi = state.varioSmoothedMps
    val pneumaticSi = rememberPneumaticNeedle(
        smoothedSi,
        state.confidence,
        state.confidenceScore,
        omegaMinHz,
        omegaMaxHz,
        targetTauSec,
        driftTauMinSec,
        driftTauMaxSec
    )
    val displayValueUser = smoothedSi?.let {
        unitsPreferences.verticalSpeed.fromSi(VerticalSpeedMs(it.toDouble())).toFloat()
    } ?: 0f
    val displayLabel = smoothedSi?.let {
        stripUnit(UnitsFormatter.verticalSpeed(VerticalSpeedMs(it.toDouble()), unitsPreferences))
    } ?: "--.-"
    val pneumaticLabel = pneumaticSi?.let {
        stripUnit(UnitsFormatter.verticalSpeed(VerticalSpeedMs(it.toDouble()), unitsPreferences))
    } ?: "--.-"
    val secondaryColor = if (!state.accelOk || !state.baroOk) {
        MaterialTheme.colorScheme.error
    } else {
        null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "HAWK variometer preview",
                style = MaterialTheme.typography.titleSmall
            )
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val maxSize = minOf(maxWidth, 280.dp)
                Box(modifier = Modifier.size(maxSize)) {
                    UIVariometer(
                        needleValue = smoothedSi ?: 0f,
                        fastNeedleValue = null,
                        averageNeedleValue = null,
                        dampedNeedleValue = pneumaticSi,
                        showPrimaryNeedle = false,
                        microArcEnabled = state.confidence == HawkConfidence.LEVEL6,
                        displayValue = displayValueUser,
                        valueLabel = displayLabel,
                        secondaryLabel = "PNEU $pneumaticLabel",
                        secondaryLabelColor = secondaryColor,
                        dialConfig = dialConfig,
                        windDirectionScreenDeg = null,
                        windIsValid = false,
                        windSpeedLabel = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Text(
                text = "Blue = smoothed, green = pneumatic (smoothed)",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun HawkNeedleTuningCard(
    omegaMinHz: Double,
    omegaMaxHz: Double,
    targetTauSec: Double,
    driftTauMinSec: Double,
    driftTauMaxSec: Double,
    onOmegaMinChange: (Double) -> Unit,
    onOmegaMaxChange: (Double) -> Unit,
    onTargetTauChange: (Double) -> Unit,
    onDriftTauMinChange: (Double) -> Unit,
    onDriftTauMaxChange: (Double) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Green needle tuning",
                style = MaterialTheme.typography.titleSmall
            )

            SliderRow(
                title = "Response speed (low confidence)",
                valueText = String.format(Locale.US, "%.2f Hz", omegaMinHz),
                description = "Lower = heavier, more pneumatic feel when confidence is mid.",
                value = omegaMinHz.toFloat(),
                valueRange = 0.5f..2.5f,
                onValueChange = { onOmegaMinChange(it.toDouble()) }
            )

            SliderRow(
                title = "Response speed (high confidence)",
                valueText = String.format(Locale.US, "%.2f Hz", omegaMaxHz),
                description = "Upper limit for how fast the green needle can react.",
                value = omegaMaxHz.toFloat(),
                valueRange = 1.0f..4.0f,
                onValueChange = { onOmegaMaxChange(it.toDouble()) }
            )

            SliderRow(
                title = "Input smoothing",
                valueText = String.format(Locale.US, "%.2f s", targetTauSec),
                description = "Low-pass on the green needle input; higher = less jitter but more lag.",
                value = targetTauSec.toFloat(),
                valueRange = 0.2f..2.0f,
                onValueChange = { onTargetTauChange(it.toDouble()) }
            )

            SliderRow(
                title = "Drift to zero (low confidence)",
                valueText = String.format(Locale.US, "%.1f s", driftTauMinSec),
                description = "How quickly the needle relaxes toward zero at mid confidence.",
                value = driftTauMinSec.toFloat(),
                valueRange = 1.0f..8.0f,
                onValueChange = { onDriftTauMinChange(it.toDouble()) }
            )

            SliderRow(
                title = "Drift to zero (high confidence)",
                valueText = String.format(Locale.US, "%.1f s", driftTauMaxSec),
                description = "How quickly it relaxes near high confidence (usually slower).",
                value = driftTauMaxSec.toFloat(),
                valueRange = 2.0f..15.0f,
                onValueChange = { onDriftTauMaxChange(it.toDouble()) }
            )
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueText: String,
    description: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodySmall
        )
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

