package com.example.xcpro.screens.navdrawer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.dfcards.CardPreferences
import com.example.xcpro.audio.VarioAudioProfile
import com.example.xcpro.audio.VarioAudioSettings
import com.example.xcpro.sensors.FlightDataCalculator
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Variometer Audio Settings Screen - Standalone version
 *
 * This version works standalone and shows test tones only.
 * Full integration requires MapScreen context with FlightDataCalculator.
 */
@Composable
fun VarioAudioSettingsScreen(
    navController: androidx.navigation.NavHostController,
    drawerState: androidx.compose.material3.DrawerState,
    modifier: Modifier = Modifier
) {
    VarioAudioSettingsContent(
        flightDataCalculator = null, // Standalone mode
        modifier = modifier,
        onDismiss = { navController.popBackStack() }
    )
}

/**
 * Variometer Audio Settings Screen Content
 *
 * Professional audio configuration for zero-lag thermal detection:
 * - Enable/disable audio
 * - Volume control
 * - Profile selection (Competition, Paragliding, Silent Sink, Full Audio)
 * - Threshold adjustments
 * - Test tones
 */
@Composable
fun VarioAudioSettingsContent(
    flightDataCalculator: FlightDataCalculator?,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Collect current settings from audio engine
    val currentSettings by flightDataCalculator?.audioEngine?.settings?.collectAsState()
        ?: remember { mutableStateOf(VarioAudioSettings()) }

    var enabled by remember { mutableStateOf(currentSettings.enabled) }
    var volume by remember { mutableStateOf(currentSettings.volume) }
    var selectedProfile by remember { mutableStateOf(currentSettings.profile) }
    var liftThreshold by remember { mutableStateOf(currentSettings.liftThreshold.toFloat()) }
    var sinkThreshold by remember { mutableStateOf(currentSettings.sinkSilenceThreshold.toFloat()) }
    var deadband by remember { mutableStateOf(currentSettings.deadbandRange.toFloat()) }
    val cardPreferences = remember { CardPreferences(context) }
    val smoothingAlpha by cardPreferences.getVarioSmoothingAlpha().collectAsState(initial = 0.25f)
    var smoothingSliderValue by remember { mutableStateOf(smoothingAlpha) }
    var isSmoothingSliderActive by remember { mutableStateOf(false) }

    LaunchedEffect(smoothingAlpha) {
        if (!isSmoothingSliderActive) {
            smoothingSliderValue = smoothingAlpha
        }
    }


    // Update local state when settings change
    LaunchedEffect(currentSettings) {
        enabled = currentSettings.enabled
        volume = currentSettings.volume
        selectedProfile = currentSettings.profile
        liftThreshold = currentSettings.liftThreshold.toFloat()
        sinkThreshold = currentSettings.sinkSilenceThreshold.toFloat()
        deadband = currentSettings.deadbandRange.toFloat()
    }

    // Function to apply settings
    fun applySettings() {
        val newSettings = VarioAudioSettings(
            enabled = enabled,
            volume = volume,
            profile = selectedProfile,
            liftThreshold = liftThreshold.toDouble(),
            sinkSilenceThreshold = sinkThreshold.toDouble(),
            deadbandRange = deadband.toDouble()
        )
        flightDataCalculator?.audioEngine?.updateSettings(newSettings)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Header
        Text(
            text = "Variometer Audio Settings",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Show warning if no FlightDataCalculator available
        if (flightDataCalculator == null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "⚠️ Flight System Not Active",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Vario audio requires active flight session. Navigate to the map screen to activate the flight system, then audio settings will be fully functional.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        VarioAudioEnableCard(
            enabled = enabled,
            onEnabledChange = {
                enabled = it
                applySettings()
            }
        )

        VarioAudioVolumeCard(
            volume = volume,
            onVolumeChange = { volume = it },
            onVolumeChangeFinished = { applySettings() }
        )

        VarioAudioProfileCard(
            selectedProfile = selectedProfile,
            onProfileSelected = {
                selectedProfile = it
                applySettings()
            }
        )

                VarioAudioThresholdCard(
            liftThreshold = liftThreshold,
            onLiftChange = { liftThreshold = it },
            onLiftChangeFinished = { applySettings() },
            deadband = deadband,
            onDeadbandChange = { deadband = it },
            onDeadbandChangeFinished = { applySettings() },
            sinkThreshold = sinkThreshold,
            onSinkChange = { sinkThreshold = it },
            onSinkChangeFinished = { applySettings() }
        )

        VarioAudioTestCard(
            onPlayTone = { frequency ->
                coroutineScope.launch {
                    flightDataCalculator?.audioEngine?.playTestTone(frequency, 1000)
                }
            },
            onPlayPattern = { value ->
                coroutineScope.launch {
                    flightDataCalculator?.audioEngine?.playTestPattern(value, 3000)
                }
            }
        )

        val smoothingDescription = when {
            smoothingSliderValue <= 0.15f -> "Fast response"
            smoothingSliderValue <= 0.25f -> "Balanced"
            smoothingSliderValue <= 0.35f -> "Calm"
            else -> "Very calm"
        }

        VarioAudioDisplayCard(
            smoothingValue = smoothingSliderValue,
            onValueChange = {
                isSmoothingSliderActive = true
                smoothingSliderValue = it
            },
            onValueChangeFinished = {
                isSmoothingSliderActive = false
                coroutineScope.launch {
                    cardPreferences.saveVarioSmoothingAlpha(smoothingSliderValue)
                }
            },
            smoothingDescription = smoothingDescription
        )

        VarioAudioInfoCard()

// === CLOSE BUTTON ===
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Close")
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}




