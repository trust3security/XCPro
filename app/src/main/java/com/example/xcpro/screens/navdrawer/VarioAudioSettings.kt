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
import com.example.xcpro.audio.VarioAudioProfile
import com.example.xcpro.audio.VarioAudioSettings
import com.example.xcpro.sensors.FlightDataCalculator
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

        // === ENABLE/DISABLE ===
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enable Vario Audio",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Audio feedback for lift and sink",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        applySettings()
                    }
                )
            }
        }

        // === VOLUME CONTROL ===
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Volume: ${(volume * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium
                )
                Slider(
                    value = volume,
                    onValueChange = { volume = it },
                    onValueChangeFinished = { applySettings() },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Adjust audio volume",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // === PROFILE SELECTION ===
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Audio Profile",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                VarioAudioProfile.values().forEach { profile ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedProfile == profile,
                            onClick = {
                                selectedProfile = profile
                                applySettings()
                            }
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = profile.name.replace('_', ' '),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = when (profile) {
                                    VarioAudioProfile.COMPETITION -> "XCTracer-style, silence for sink"
                                    VarioAudioProfile.PARAGLIDING -> "Gentler, slower beeps"
                                    VarioAudioProfile.SILENT_SINK -> "No sink audio (most common)"
                                    VarioAudioProfile.FULL_AUDIO -> "Both lift and sink audio"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // === THRESHOLDS ===
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Thresholds",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Lift threshold
                Text(
                    text = "Lift Threshold: ${String.format("%.1f", liftThreshold)} m/s",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = liftThreshold,
                    onValueChange = { liftThreshold = it },
                    onValueChangeFinished = { applySettings() },
                    valueRange = 0.1f..1.0f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Minimum lift for audio alert",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Deadband
                Text(
                    text = "Deadband: ±${String.format("%.1f", deadband)} m/s",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = deadband,
                    onValueChange = { deadband = it },
                    onValueChangeFinished = { applySettings() },
                    valueRange = 0.1f..0.5f,
                    steps = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Silence range around zero",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Sink threshold
                Text(
                    text = "Sink Warning: ${String.format("%.1f", sinkThreshold)} m/s",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = sinkThreshold,
                    onValueChange = { sinkThreshold = it },
                    onValueChangeFinished = { applySettings() },
                    valueRange = -5.0f..-1.0f,
                    steps = 7,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Strong sink warning threshold",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // === TEST TONES ===
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Test Audio",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = "Test Tone Frequencies",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                flightDataCalculator?.audioEngine?.playTestTone(450.0, 1000)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("450Hz")
                    }
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                flightDataCalculator?.audioEngine?.playTestTone(579.0, 1000)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("579Hz")
                    }
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                flightDataCalculator?.audioEngine?.playTestTone(1000.0, 1000)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("1000Hz")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Test Vario Patterns (3 seconds)",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Weak lift
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                flightDataCalculator?.audioEngine?.playTestPattern(0.5, 3000)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Test Weak Lift (+0.5 m/s)")
                    }

                    // Moderate lift
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                flightDataCalculator?.audioEngine?.playTestPattern(2.0, 3000)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Test Moderate Lift (+2.0 m/s)")
                    }

                    // Strong lift
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                flightDataCalculator?.audioEngine?.playTestPattern(5.0, 3000)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Test Strong Lift (+5.0 m/s)")
                    }

                    // Strong sink
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                flightDataCalculator?.audioEngine?.playTestPattern(-3.0, 3000)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Test Strong Sink (-3.0 m/s)")
                    }
                }
            }
        }

        // === INFORMATION ===
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "ℹ️ About Vario Audio",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "• Zero-lag thermal detection (<100ms)\n" +
                            "• TE-compensated (no stick thermals)\n" +
                            "• XCTracer-compliant frequency mapping\n" +
                            "• Higher pitch = stronger lift\n" +
                            "• Faster beeping = stronger lift\n" +
                            "• Professional competition standard",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

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
