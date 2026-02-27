package com.example.xcpro.screens.navdrawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcpro.glider.GliderViewModel
import com.example.xcpro.glider.PolarCalculator

@Composable
fun PreviewCard() {
    val viewModel: GliderViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedModel = uiState.selectedModel
    val effectiveModel = uiState.effectiveModel
    val fallbackActive = uiState.isFallbackPolarActive
    val cfg = uiState.config

    val speedKmh = remember { mutableStateOf(100f) }
    val sink = effectiveModel?.let { model ->
        runCatching {
            PolarCalculator.sinkMs(speedKmh.value.toDouble() / 3.6, model, cfg)
        }.getOrNull()?.takeIf { it.isFinite() }
    }

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
                text = if (selectedModel == null && !fallbackActive) {
                    "Select an aircraft to preview polar"
                } else if (fallbackActive) {
                    "Fallback active: ${effectiveModel?.name ?: "Default club"} - ${speedKmh.value.toInt()} km/h"
                } else {
                    "Model: ${selectedModel?.name ?: effectiveModel?.name.orEmpty()} - ${speedKmh.value.toInt()} km/h"
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
            val hint = when {
                fallbackActive -> "Using default club fallback polar"
                cfg.threePointPolar != null -> "Using 3-point polar"
                else -> "Using model polar"
            }
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (fallbackActive) {
                Text(
                    text = "Select a glider or enter a 3-point polar to replace fallback.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(
                text = "Netto/audio use this polar instantly (with bugs + ballast).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        }
    }
}
