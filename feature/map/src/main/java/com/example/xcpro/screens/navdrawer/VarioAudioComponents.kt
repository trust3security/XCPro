package com.example.xcpro.screens.navdrawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.xcpro.audio.VarioAudioProfile

@Composable
internal fun VarioAudioEnableCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
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
                onCheckedChange = onEnabledChange
            )
        }
    }
}

@Composable
internal fun VarioAudioVolumeCard(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit
) {
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
                onValueChange = onVolumeChange,
                onValueChangeFinished = onVolumeChangeFinished,
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
}

@Composable
internal fun VarioAudioProfileCard(
    selectedProfile: VarioAudioProfile,
    onProfileSelected: (VarioAudioProfile) -> Unit
) {
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
                text = "Profiles",
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
                        onClick = { onProfileSelected(profile) }
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
                                VarioAudioProfile.SMART_THERMAL -> "Adaptive \"smart\" tones with harmonics"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun VarioAudioThresholdCard(
    liftThreshold: Float,
    onLiftChange: (Float) -> Unit,
    onLiftChangeFinished: () -> Unit,
    deadband: Float,
    onDeadbandChange: (Float) -> Unit,
    onDeadbandChangeFinished: () -> Unit,
    sinkThreshold: Float,
    onSinkChange: (Float) -> Unit,
    onSinkChangeFinished: () -> Unit
) {
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

            Text(
                text = "Lift Threshold: ${String.format("%.1f", liftThreshold)} m/s",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = liftThreshold,
                onValueChange = onLiftChange,
                onValueChangeFinished = onLiftChangeFinished,
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

            Text(
                text = "Deadband: ±${String.format("%.1f", deadband)} m/s",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = deadband,
                onValueChange = onDeadbandChange,
                onValueChangeFinished = onDeadbandChangeFinished,
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

            Text(
                text = "Sink Warning: ${String.format("%.1f", sinkThreshold)} m/s",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = sinkThreshold,
                onValueChange = onSinkChange,
                onValueChangeFinished = onSinkChangeFinished,
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
}

@Composable
internal fun VarioAudioTestCard(
    onPlayTone: (Double) -> Unit,
    onPlayPattern: (Double) -> Unit
) {
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
                listOf(450.0, 579.0, 1000.0).forEach { freq ->
                    Button(
                        onClick = { onPlayTone(freq) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("${freq.toInt()}Hz")
                    }
                }
            }

            Spacer(modifier = Modifier.size(16.dp))

            Text(
                text = "Test Vario Patterns (3 seconds)",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    0.5 to "Test Weak Lift (+0.5 m/s)",
                    2.0 to "Test Moderate Lift (+2.0 m/s)",
                    5.0 to "Test Strong Lift (+5.0 m/s)",
                    -3.0 to "Test Strong Sink (-3.0 m/s)"
                ).forEach { (value, label) ->
                    Button(
                        onClick = { onPlayPattern(value) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(label)
                    }
                }
            }
        }
    }
}

@Composable
internal fun VarioAudioInfoCard() {
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
                text = "About Vario Audio",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = """
                    • Zero-lag thermal detection (<100ms)
                    • TE-compensated (no stick thermals)
                    • XCTracer-compliant frequency mapping
                    • Higher pitch = stronger lift
                    • Faster beeping = stronger lift
                    • Competition-ready performance
                """.trimIndent(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

