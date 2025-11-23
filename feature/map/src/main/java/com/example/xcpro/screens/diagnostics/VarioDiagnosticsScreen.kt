package com.example.xcpro.screens.diagnostics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.xcpro.sensors.VarioDiagnosticsSample
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VarioDiagnosticsScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    viewModel: VarioDiagnosticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val latest = uiState.latest
    val history = uiState.history

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vario Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DiagnosticsSummaryCard(latest)

            DiagnosticsChart(
                title = "TE Vertical Speed",
                unit = "m/s",
                history = history,
                valueSelector = { sample -> sample.teVerticalSpeed ?: sample.rawVerticalSpeed }
            )

            DiagnosticsChart(
                title = "Raw Vertical Speed",
                unit = "m/s",
                history = history,
                valueSelector = { it.rawVerticalSpeed }
            )

            DiagnosticsChart(
                title = "Baro Variance (sigma^2)",
                unit = "(m/s)^2",
                history = history,
                valueSelector = { it.diagnostics.adaptiveSigmaBaro }
            )

            DiagnosticsChart(
                title = "Measurement Noise (R)",
                unit = "m",
                history = history,
                valueSelector = { it.diagnostics.adaptiveMeasurementNoise }
            )

            DiagnosticsChart(
                title = "Process Noise (Q)",
                unit = "",
                history = history,
                valueSelector = { it.diagnostics.adaptiveProcessNoise }
            )

            DiagnosticsChart(
                title = "Tau",
                unit = "s",
                history = history,
                valueSelector = { it.diagnostics.adaptiveTauSeconds }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiagnosticsSummaryCard(latest: VarioDiagnosticsSample?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "Current Values", style = MaterialTheme.typography.titleMedium)
            if (latest == null) {
                Text("Waiting for diagnostics...", style = MaterialTheme.typography.bodyMedium)
            } else {
                MetricRow("TE Vario", formatMetersPerSecond(latest.teVerticalSpeed))
                MetricRow("Raw Vario", formatMetersPerSecond(latest.rawVerticalSpeed))
                MetricRow("sigma^2", latest.diagnostics.adaptiveSigmaBaro.format(3) + " (m/s)^2")
                MetricRow("R", latest.diagnostics.adaptiveMeasurementNoise.format(3) + " m")
                MetricRow("Q", latest.diagnostics.adaptiveProcessNoise.format(3))
                MetricRow("Tau", latest.diagnostics.adaptiveTauSeconds.format(2) + " s")
                MetricRow("Confidence", latest.diagnostics.confidence.format(2))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ElevatedAssistChip(onClick = { }, label = { Text("Baro ${'$'}{latest.diagnostics.baroHealthScore.format(2)}") })
                    ElevatedAssistChip(onClick = { }, label = { Text("IMU ${'$'}{latest.diagnostics.imuHealthScore.format(2)}") })
                    ElevatedAssistChip(onClick = { }, label = { Text("GPS ${'$'}{latest.diagnostics.gpsHealthScore.format(2)}") })
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsChart(
    title: String,
    unit: String,
    history: List<VarioDiagnosticsSample>,
    valueSelector: (VarioDiagnosticsSample) -> Double
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (history.isEmpty()) {
                Text("Waiting for samples...", style = MaterialTheme.typography.bodyMedium)
            } else {
                val values = history.map(valueSelector)
                val minValue = values.minOrNull() ?: 0.0
                val maxValue = values.maxOrNull() ?: 0.0
                val range = (maxValue - minValue).takeIf { abs(it) > 1e-6 } ?: 1.0
                val latestValue = values.last()
                val strokeColor = MaterialTheme.colorScheme.primary
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    val path = Path()
                    values.forEachIndexed { index, value ->
                        val x = if (values.size == 1) 0f else index.toFloat() / (values.size - 1)
                        val normalizedY = 1f - ((value - minValue) / range).toFloat()
                        val point = Offset(x * size.width, normalizedY * size.height)
                        if (index == 0) {
                            path.moveTo(point.x, point.y)
                        } else {
                            path.lineTo(point.x, point.y)
                        }
                    }
                    drawPath(path = path, color = strokeColor, style = Stroke(width = 4f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Min ${'$'}{minValue.format(2)} $unit", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "Now ${'$'}{latestValue.format(2)} $unit",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Max ${'$'}{maxValue.format(2)} $unit", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatMetersPerSecond(value: Double?): String {
    return value?.format(2)?.plus(" m/s") ?: "-"
}

private fun Double.format(decimals: Int = 2): String {
    return String.format(Locale.US, "%." + decimals + "f", this)
}
