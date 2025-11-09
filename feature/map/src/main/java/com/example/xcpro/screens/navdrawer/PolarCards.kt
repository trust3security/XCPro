package com.example.xcpro.screens.navdrawer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.xcpro.glider.GliderRepository
import com.example.xcpro.glider.PolarCalculator
import com.example.xcpro.common.glider.SpeedLimits
import com.example.xcpro.common.glider.ThreePointPolar
import kotlin.math.roundToInt

@Composable
fun PreviewCard() {
    val context = LocalContext.current
    val repo = remember(context) { GliderRepository.getInstance(context) }
    val model by repo.selectedModel.collectAsState(initial = null)
    val cfg by repo.config.collectAsState()

    val speedKmh = remember { mutableStateOf(100f) }
    val sink = model?.let { m -> PolarCalculator.sinkMs(speedKmh.value.toDouble() / 3.6, m, cfg) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Quick Preview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (model == null) {
                    "Select an aircraft to preview polar"
                } else {
                    "Model: ${model!!.name} - ${speedKmh.value.toInt()} km/h"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = speedKmh.value,
                onValueChange = { speedKmh.value = it },
                valueRange = 50f..200f
            )
            if (sink != null) {
                Text(String.format("Estimated sink: %.2f m/s", sink))
            }
            val hint = if (cfg.threePointPolar != null) "Using 3-point polar" else "Using model polar"
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Netto/audio use this polar instantly (with bugs + ballast).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun ConfigCard() {
    val context = LocalContext.current
    val repo = remember(context) { GliderRepository.getInstance(context) }
    val cfg by repo.config.collectAsState()

    var pilotInput by remember(cfg.pilotAndGearKg) {
        mutableStateOf(cfg.pilotAndGearKg.roundToInt().toString())
    }
    var ballastInput by remember(cfg.waterBallastKg) {
        mutableStateOf(cfg.waterBallastKg.roundToInt().toString())
    }

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
            OutlinedTextField(
                value = cfg.bugsPercent.toString(),
                onValueChange = { text ->
                    text.toIntOrNull()?.let { value ->
                        repo.updateConfig { it.copy(bugsPercent = value.coerceIn(0, 50)) }
                    }
                },
                label = { Text("Bugs (%)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = cfg.referenceWeightKg?.toString() ?: "",
                onValueChange = { text ->
                    val value = text.toDoubleOrNull()
                    repo.setReferenceWeightKg(value)
                },
                label = { Text("Reference Weight (kg)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@Composable
fun ThreePointPolarCard() {
    val context = LocalContext.current
    val repo = remember(context) { GliderRepository.getInstance(context) }
    val cfg by repo.config.collectAsState()
    val tpp = cfg.threePointPolar ?: ThreePointPolar()

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
                text = "LX/Hawk 3-Point Polar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = tpp.lowKmh.toString(),
                    onValueChange = { value ->
                        value.toDoubleOrNull()?.let { repo.setThreePointPolar(tpp.copy(lowKmh = it)) }
                    },
                    label = { Text("Low Speed (km/h)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = tpp.lowSinkMs.toString(),
                    onValueChange = { value ->
                        value.toDoubleOrNull()?.let { repo.setThreePointPolar(tpp.copy(lowSinkMs = it)) }
                    },
                    label = { Text("Low Sink (m/s)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = tpp.midKmh.toString(),
                    onValueChange = { value ->
                        value.toDoubleOrNull()?.let { repo.setThreePointPolar(tpp.copy(midKmh = it)) }
                    },
                    label = { Text("Mid Speed (km/h)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = tpp.midSinkMs.toString(),
                    onValueChange = { value ->
                        value.toDoubleOrNull()?.let { repo.setThreePointPolar(tpp.copy(midSinkMs = it)) }
                    },
                    label = { Text("Mid Sink (m/s)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = tpp.highKmh.toString(),
                    onValueChange = { value ->
                        value.toDoubleOrNull()?.let { repo.setThreePointPolar(tpp.copy(highKmh = it)) }
                    },
                    label = { Text("High Speed (km/h)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = tpp.highSinkMs.toString(),
                    onValueChange = { value ->
                        value.toDoubleOrNull()?.let { repo.setThreePointPolar(tpp.copy(highSinkMs = it)) }
                    },
                    label = { Text("High Sink (m/s)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
        }
    }
}

@Composable
fun FlapSpeedTableCard() {
    PolarPlaceholderCard(
        title = "Flap / Speed Table",
        body = "Use the map screen until this table is implemented."
    )
}

@Composable
fun AircraftSelectCard() {
    val context = LocalContext.current
    val repo = remember(context) { GliderRepository.getInstance(context) }
    val selected by repo.selectedModel.collectAsState(initial = null)
    val models = repo.listModels()

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
                text = "Aircraft",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = selected?.name ?: "None selected",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                models.forEach { model ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { repo.selectModelById(model.id) }
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = model.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (model.id == selected?.id) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DetailsCard() {
    val context = LocalContext.current
    val repo = remember(context) { GliderRepository.getInstance(context) }
    val model by repo.selectedModel.collectAsState(initial = null)

    val selectedModel = model ?: return

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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            val detailLines = listOfNotNull(
                selectedModel.wingSpanM?.let { "Wingspan: ${"%.1f".format(it)} m" },
                selectedModel.wingAreaM2?.let { "Wing Area: ${"%.2f".format(it)} m^2" },
                selectedModel.aspectRatio?.let { "Aspect Ratio: ${"%.1f".format(it)}" },
                selectedModel.bestLD?.let { ld ->
                    val speed = selectedModel.bestLDSpeedKmh?.let { " at ${it.toInt()} km/h" } ?: ""
                    "Best L/D: ${"%.0f".format(ld)}:1$speed"
                },
                selectedModel.minSinkMs?.let { "Min Sink: ${"%.2f".format(it)} m/s" },
                selectedModel.speedLimits?.let { limits ->
                    listOfNotNull(
                        limits.vneKmh?.let { "VNE: $it km/h" },
                        limits.vraKmh?.let { "VRA: $it km/h" },
                        limits.vaKmh?.let { "VA: $it km/h" },
                        limits.vwKmh?.let { "VW: $it km/h" },
                        limits.vtKmh?.let { "VT: $it km/h" }
                    ).joinToString(" | ")
                }
            )
            detailLines.forEach { line ->
                if (line.isNotBlank()) {
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}



@Composable
private fun PolarPlaceholderCard(
    title: String,
    body: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

