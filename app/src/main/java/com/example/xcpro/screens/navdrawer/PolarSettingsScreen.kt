package com.example.xcpro.screens.navdrawer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.compose.ui.platform.LocalContext
import com.example.xcpro.glider.GliderRepository
import com.example.xcpro.glider.PolarCalculator
import com.example.xcpro.glider.SpeedLimits
import com.example.xcpro.glider.ThreePointPolar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolarSettingsScreen(
    navController: NavHostController
) {
    val scroll = rememberScrollState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                title = {
                    Text(
                        text = "Polar",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
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
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Set up your aircraft profile and polar so netto, speed‑to‑fly and glide calculations are accurate.",
                style = MaterialTheme.typography.bodyMedium
            )

            // Aircraft selection
            AircraftSelectCard()

            // Model details
            DetailsCard()

            ConfigCard()

            ThreePointPolarCard()

            InfoCard(
                title = "Polar",
                body = "Uses manufacturer polar curves with interpolation. Adjusts for wing loading and bugs."
            )

            InfoCard(
                title = "Docs",
                body = "See GLIDER_POLAR_SYSTEM.md and GLIDER_POLAR_IMPLEMENTATION_GUIDE.md for details."
            )

            PreviewCard()
        }
    }
}

@Composable
private fun AircraftSelectCard() {
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
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
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
                            .padding(vertical = 8.dp),
                    ) {
                        Text(
                            text = model.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (model.id == selected?.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsCard() {
    val context = LocalContext.current
    val repo = remember(context) { GliderRepository.getInstance(context) }
    val model by repo.selectedModel.collectAsState(initial = null)

    if (model == null) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val m = model!!
            val lines = listOfNotNull(
                m.wingSpanM?.let { "Wingspan: ${"%.1f".format(it)} m" },
                m.wingAreaM2?.let { "Wing Area: ${"%.2f".format(it)} m²" },
                m.aspectRatio?.let { "Aspect Ratio: ${"%.1f".format(it)}" },
                m.bestLD?.let { ld ->
                    val spd = m.bestLDSpeedKmh?.let { " at ${it.toInt()} km/h" } ?: ""
                    "Best L/D: ${"%.0f".format(ld)}:1$spd"
                },
                m.minSinkMs?.let { "Min Sink: ${"%.2f".format(it)} m/s" },
                m.speedLimits?.let { sl: SpeedLimits ->
                    listOfNotNull(
                        sl.vneKmh?.let { "VNE: $it km/h" },
                        sl.vraKmh?.let { "VRA: $it km/h" },
                        sl.vaKmh?.let { "VA: $it km/h" },
                        sl.vwKmh?.let { "VW: $it km/h" },
                        sl.vtKmh?.let { "VT: $it km/h" }
                    ).joinToString(" · ")
                }
            )
            lines.forEach { line ->
                if (line.isNotBlank()) Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ConfigCard() {
    val context = LocalContext.current
    val repo = remember(context) { GliderRepository.getInstance(context) }
    val cfg by repo.config.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
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
                    value = cfg.pilotAndGearKg.toString(),
                    onValueChange = { text ->
                        val v = text.toDoubleOrNull()
                        if (v != null) repo.updateConfig { it.copy(pilotAndGearKg = v.coerceAtLeast(0.0)) }
                    },
                    label = { Text("Pilot + Gear (kg)") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                OutlinedTextField(
                    value = cfg.waterBallastKg.toString(),
                    onValueChange = { text ->
                        val v = text.toDoubleOrNull()
                        if (v != null) repo.updateConfig { it.copy(waterBallastKg = v.coerceAtLeast(0.0)) }
                    },
                    label = { Text("Water Ballast (kg)") },
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = cfg.bugsPercent.toString(),
                onValueChange = { text ->
                    val v = text.toIntOrNull()
                    if (v != null) repo.updateConfig { it.copy(bugsPercent = v.coerceIn(0, 50)) }
                },
                label = { Text("Bugs (%)") },
                modifier = Modifier.fillMaxWidth()
            )

            // Reference weight for 3-point polar
            OutlinedTextField(
                value = (cfg.referenceWeightKg ?: 0.0).toString(),
                onValueChange = { text ->
                    val v = text.toDoubleOrNull()
                    repo.setReferenceWeightKg(v)
                },
                label = { Text("Reference Weight (kg)") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PreviewCard() {
    val context = LocalContext.current
    val repo = remember(context) { GliderRepository.getInstance(context) }
    val model by repo.selectedModel.collectAsState(initial = null)
    val cfg by repo.config.collectAsState()

    var speedKmh = remember { androidx.compose.runtime.mutableStateOf(100f) }
    val sink = model?.let { m -> PolarCalculator.sinkMs(speedKmh.value.toDouble() / 3.6, m, cfg) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Quick Preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = if (model == null) "Select an aircraft to preview polar" else "Model: ${model!!.name} - ${speedKmh.value.toInt()} km/h",
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
            // Indicate if 3-point polar is active
            val hint = if (cfg.threePointPolar != null) "Using 3-point polar" else "Using model polar"
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ThreePointPolarCard() {
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
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("LX/Hawk 3-Point Polar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = tpp.lowKmh.toString(),
                    onValueChange = { v -> v.toDoubleOrNull()?.let { repo.setThreePointPolar(tpp.copy(lowKmh = it)) } },
                    label = { Text("Low Speed (km/h)") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                OutlinedTextField(
                    value = tpp.lowSinkMs.toString(),
                    onValueChange = { v -> v.toDoubleOrNull()?.let { repo.setThreePointPolar(tpp.copy(lowSinkMs = it)) } },
                    label = { Text("Low Sink (m/s)") },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = tpp.midKmh.toString(),
                    onValueChange = { v -> v.toDoubleOrNull()?.let { repo.setThreePointPolar(tpp.copy(midKmh = it)) } },
                    label = { Text("Mid Speed (km/h)") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                OutlinedTextField(
                    value = tpp.midSinkMs.toString(),
                    onValueChange = { v -> v.toDoubleOrNull()?.let { repo.setThreePointPolar(tpp.copy(midSinkMs = it)) } },
                    label = { Text("Mid Sink (m/s)") },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = tpp.highKmh.toString(),
                    onValueChange = { v -> v.toDoubleOrNull()?.let { repo.setThreePointPolar(tpp.copy(highKmh = it)) } },
                    label = { Text("High Speed (km/h)") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                OutlinedTextField(
                    value = tpp.highSinkMs.toString(),
                    onValueChange = { v -> v.toDoubleOrNull()?.let { repo.setThreePointPolar(tpp.copy(highSinkMs = it)) } },
                    label = { Text("High Sink (m/s)") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}



