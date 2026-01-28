package com.example.xcpro.screens.navdrawer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcpro.glider.GliderRepository
import kotlin.math.roundToInt

@Composable
fun ConfigCard() {
    val context = LocalContext.current
    val repo = remember(context) { GliderRepository.getInstance(context) }
    val cfg by repo.config.collectAsStateWithLifecycle()

    var pilotInput by remember(cfg.pilotAndGearKg) {
        mutableStateOf(cfg.pilotAndGearKg.roundToInt().toString())
    }
    var ballastInput by remember(cfg.waterBallastKg) {
        mutableStateOf(cfg.waterBallastKg.roundToInt().toString())
    }
    var drainMinutesInput by remember(cfg.ballastDrainMinutes) {
        mutableStateOf(
            if (cfg.ballastDrainMinutes == 0.0) ""
            else cfg.ballastDrainMinutes.toString().trimEnd('0').trimEnd('.')
        )
    }
    var refWeightInput by remember(cfg.referenceWeightKg) {
        mutableStateOf(cfg.referenceWeightKg?.toInt()?.toString() ?: "")
    }
    val ballastActive = cfg.waterBallastKg > 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Configuration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = pilotInput,
                    onValueChange = { text ->
                        val sanitized = text.filter { it.isDigit() }
                        pilotInput = sanitized
                        when {
                            sanitized.isBlank() -> repo.updateConfig { it.copy(pilotAndGearKg = 0.0) }
                            else -> sanitized.toIntOrNull()?.let { value ->
                                repo.updateConfig { it.copy(pilotAndGearKg = value.toDouble().coerceAtLeast(0.0)) }
                            }
                        }
                    },
                    label = { Text("Pilot + Gear (kg)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = ballastInput,
                    onValueChange = { text ->
                        val sanitized = text.filter { it.isDigit() }
                        ballastInput = sanitized
                        when {
                            sanitized.isBlank() -> repo.updateConfig { it.copy(waterBallastKg = 0.0) }
                            else -> sanitized.toIntOrNull()?.let { value ->
                                repo.updateConfig { it.copy(waterBallastKg = value.toDouble().coerceAtLeast(0.0)) }
                            }
                        }
                    },
                    label = { Text("Water Ballast (kg)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = cfg.bugsPercent.toString(),
                    onValueChange = { text ->
                        text.toIntOrNull()?.let { value ->
                            repo.updateConfig { it.copy(bugsPercent = value.coerceIn(0, 50)) }
                        }
                    },
                    label = { Text("Bugs (%)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = drainMinutesInput,
                    onValueChange = { text ->
                        val sanitized = text.filter { it.isDigit() || it == '.' }
                        drainMinutesInput = sanitized
                        when {
                            sanitized.isBlank() -> repo.updateConfig { it.copy(ballastDrainMinutes = 0.0) }
                            else -> sanitized.toDoubleOrNull()?.let { value ->
                                repo.updateConfig {
                                    it.copy(
                                        ballastDrainMinutes = value.coerceIn(0.5, 60.0)
                                    )
                                }
                            }
                        }
                    },
                    label = { Text("Drain Time (min)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Decimal)
                )
            }
            OutlinedTextField(
                value = refWeightInput,
                onValueChange = { text ->
                    val sanitized = text.filter { it.isDigit() }
                    refWeightInput = sanitized
                    when {
                        sanitized.isBlank() -> repo.setReferenceWeightKg(null)
                        else -> sanitized.toIntOrNull()?.let { value ->
                            repo.setReferenceWeightKg(value.toDouble())
                        }
                    }
                },
                label = { Text("Reference Weight (kg)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Hide Ballast Pill",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (ballastActive) {
                            "Ballast pill forced on while water ballast > 0 kg."
                        } else {
                            "Removes the swipe-to-fill overlay on the map."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ballastActive) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Switch(
                    checked = cfg.hideBallastPill,
                    onCheckedChange = { enabled ->
                        repo.updateConfig { it.copy(hideBallastPill = enabled) }
                    },
                    enabled = !ballastActive
                )
            }
        }
    }
}
