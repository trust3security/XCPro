package com.example.xcpro.screens.navdrawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
internal fun VarioAudioThresholdCard(
    liftStartThreshold: Float,
    onLiftStartChange: (Float) -> Unit,
    onLiftStartChangeFinished: () -> Unit,
    sinkStartThreshold: Float,
    onSinkStartChange: (Float) -> Unit,
    onSinkStartChangeFinished: () -> Unit
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
                text = "Lift Start: ${String.format("%.1f", liftStartThreshold)} m/s",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = liftStartThreshold,
                onValueChange = onLiftStartChange,
                onValueChangeFinished = onLiftStartChangeFinished,
                valueRange = 0.1f..1.0f,
                steps = 8,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Start climb beeps once audio vario reaches this value.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Sink Start: ${String.format("%.1f", sinkStartThreshold)} m/s",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = sinkStartThreshold,
                onValueChange = onSinkStartChange,
                onValueChangeFinished = onSinkStartChangeFinished,
                valueRange = -5.0f..0.0f,
                steps = 10,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Set to 0.0 m/s for immediate sink tone. Lower values wait for stronger sink.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                    - Zero-lag thermal detection (<100ms)
                    - TE-compensated (no stick thermals)
                    - XCTracer-compliant frequency mapping
                    - Higher pitch = stronger lift
                    - Faster beeping = stronger lift
                    - Competition-ready performance
                """.trimIndent(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

