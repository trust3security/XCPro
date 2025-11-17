package com.example.xcpro.screens.navdrawer

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevoVarioSettingsScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    viewModel: LevoVarioSettingsViewModel = hiltViewModel()
) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val audioSettings = uiState.audioSettings

    Scaffold(
        topBar = {
            SettingsTopAppBar(
                title = "Levo Vario",
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
            Text(
                text = "Control how the Levo variometer blends phone IMU data with barometric altitude. " +
                    "Turn off IMU assist if your mount vibrates or phone bumps create false lift.",
                style = MaterialTheme.typography.bodyMedium
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "IMU Assist",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (uiState.imuAssistEnabled) {
                                "Enabled above 15 m/s so the variometer reacts instantly during real flight."
                            } else {
                                "Disabled. Levo relies on barometric smoothing only, reducing stick thermals."
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = uiState.imuAssistEnabled,
                        onCheckedChange = { viewModel.setImuAssistEnabled(it) }
                    )
                }
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
                        text = "How it works",
                        style = MaterialTheme.typography.titleSmall
                    )
                    BulletText("IMU assist is only applied when GPS speed exceeds 15 m/s to avoid ground bumps.")
                    BulletText("When off, the Levo needle tracks filtered baro/TE data for maximum stability.")
                    BulletText("You can toggle this mid-flight if your mount starts vibrating or drifting.")
                }
            }

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

            VarioAudioProfileCard(
                selectedProfile = audioSettings.profile,
                onProfileSelected = viewModel::setAudioProfile
            )

            VarioAudioThresholdCard(
                liftThreshold = audioSettings.liftThreshold.toFloat(),
                onLiftChange = viewModel::setLiftThreshold,
                onLiftChangeFinished = {},
                deadband = audioSettings.deadbandRange.toFloat(),
                onDeadbandChange = viewModel::setDeadband,
                onDeadbandChangeFinished = {},
                sinkThreshold = audioSettings.sinkSilenceThreshold.toFloat(),
                onSinkChange = viewModel::setSinkThreshold,
                onSinkChangeFinished = {}
            )

            VarioAudioTestCard(
                onPlayTone = viewModel::playTestTone,
                onPlayPattern = viewModel::playTestPattern
            )

            VarioAudioInfoCard()
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
