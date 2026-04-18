package com.trust3.xcpro.screens.navdrawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevoVarioSettingsScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    viewModel: LevoVarioSettingsViewModel = hiltViewModel(),
    onNavigateUp: (() -> Unit)? = null,
    onSecondaryNavigate: (() -> Unit)? = null,
    onNavigateToMap: (() -> Unit)? = null
) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val audioSettings = uiState.audioSettings
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
                title = "Levo Vario",
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
            AutoMcCard(
                enabled = uiState.autoMcEnabled,
                onEnabledChange = viewModel::setAutoMcEnabled
            )
            MacCreadyCard(
                macCready = uiState.macCready,
                onMacCreadyChange = viewModel::setMacCready,
                enabled = !uiState.autoMcEnabled
            )
            MacCreadyRiskCard(
                macCreadyRisk = uiState.macCreadyRisk,
                onMacCreadyRiskChange = viewModel::setMacCreadyRisk
            )

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
                        text = "Why MacCready matters",
                        style = MaterialTheme.typography.titleSmall
                    )
                    BulletText("This setting is shared with your glide computer; adjust it whenever you change tactics.")
                    BulletText("TC Avg and T Avg cards turn red when their value falls below your risk MacCready.")
                    BulletText("Set the same value here and in your glide computer to keep audio, colors, and polar math aligned.")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "General",
                style = MaterialTheme.typography.titleMedium
            )

            VarioDisplayOptionsCard(
                teCompensationEnabled = uiState.teCompensationEnabled,
                onTeCompensationEnabledChange = viewModel::setTeCompensationEnabled,
                showWindSpeedOnVario = uiState.showWindSpeedOnVario,
                onShowWindSpeedOnVarioChange = viewModel::setShowWindSpeedOnVario
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Audio alerts",
                style = MaterialTheme.typography.titleMedium
            )

            VarioAudioEnableCard(
                enabled = audioSettings.enabled,
                onEnabledChange = viewModel::setAudioEnabled
            )

            VarioAudioVolumeCard(
                volume = audioSettings.volume,
                onVolumeChange = viewModel::setAudioVolume,
                onVolumeChangeFinished = {}
            )

            VarioAudioThresholdCard(
                liftStartThreshold = uiState.liftStartThreshold,
                onLiftStartChange = viewModel::setLiftStartThreshold,
                onLiftStartChangeFinished = {},
                sinkStartThreshold = uiState.sinkStartThreshold,
                onSinkStartChange = viewModel::setSinkStartThreshold,
                onSinkStartChangeFinished = {}
            )
        }
    }
}

@Composable
private fun MacCreadyCard(
    macCready: Double,
    onMacCreadyChange: (Double) -> Unit,
    enabled: Boolean
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
                text = "MacCready (m/s)",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = String.format("%.1f m/s  (%.1f kt)", macCready, macCready * 1.94384),
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = macCready.toFloat().coerceIn(0f, 5f),
                onValueChange = { value ->
                    onMacCreadyChange(value.toDouble())
                },
                enabled = enabled,
                valueRange = 0f..5f,
                steps = 20
            )
            if (!enabled) {
                Text(
                    text = "Auto MC is enabled. Disable it to edit manual MacCready.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun MacCreadyRiskCard(
    macCreadyRisk: Double,
    onMacCreadyRiskChange: (Double) -> Unit
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
                text = "MacCready Risk (m/s)",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = String.format("%.1f m/s  (%.1f kt)", macCreadyRisk, macCreadyRisk * 1.94384),
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = macCreadyRisk.toFloat().coerceIn(0f, 5f),
                onValueChange = { value ->
                    onMacCreadyRiskChange(value.toDouble())
                },
                valueRange = 0f..5f,
                steps = 20
            )
            Text(
                text = "Controls when thermal cards turn red. Match your glide computer's risk slider for identical coloring.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun BulletText(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(text = "-", style = MaterialTheme.typography.bodyMedium)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun VarioDisplayOptionsCard(
    teCompensationEnabled: Boolean,
    onTeCompensationEnabledChange: (Boolean) -> Unit,
    showWindSpeedOnVario: Boolean,
    onShowWindSpeedOnVarioChange: (Boolean) -> Unit
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
                text = "Variometer behavior",
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
                        text = "Wind speed",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Show wind speed above the needle when wind is available",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = showWindSpeedOnVario,
                    onCheckedChange = onShowWindSpeedOnVarioChange
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
                        text = "Total Energy compensation",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Use TE-compensated vario when eligible airspeed is available",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = teCompensationEnabled,
                    onCheckedChange = onTeCompensationEnabledChange
                )
            }
        }
    }
}

@Composable
private fun AutoMcCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
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
                text = "Auto MacCready",
                style = MaterialTheme.typography.titleMedium
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
                        text = if (enabled) "Using Auto MC" else "Using Manual MC",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Auto MC updates at thermal exit based on recent climbs.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange
                )
            }
        }
    }
}
