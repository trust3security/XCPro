package com.example.xcpro.map.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lens
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun BoxScope.MapTrafficConnectionIndicatorsHost(
    indicators: TrafficConnectionIndicatorsUiState,
    reserveTopEndPrimarySlot: Boolean
) {
    val visibleIndicators = remember(indicators) {
        listOfNotNull(indicators.ogn, indicators.adsb)
    }
    if (visibleIndicators.isEmpty()) return

    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
        12.dp +
        if (reserveTopEndPrimarySlot) TRAFFIC_STATUS_PRIMARY_SLOT_RESERVE_DP else 0.dp
    Column(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = topPadding, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End
    ) {
        visibleIndicators.forEach { indicator ->
            TrafficConnectionIndicatorPill(indicator = indicator)
        }
    }
}

@Composable
private fun TrafficConnectionIndicatorPill(
    indicator: TrafficConnectionIndicatorUiModel
) {
    val toneColor = when (indicator.tone) {
        TrafficConnectionIndicatorTone.GREEN -> Color(0xFF16A34A)
        TrafficConnectionIndicatorTone.RED -> Color(0xFFDC2626)
    }
    val statusText = when (indicator.tone) {
        TrafficConnectionIndicatorTone.GREEN -> "connected"
        TrafficConnectionIndicatorTone.RED -> "failed"
    }
    Surface(
        modifier = Modifier.semantics {
            contentDescription = "${indicator.sourceLabel} $statusText"
        },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier.padding(PillPadding),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Lens,
                    contentDescription = null,
                    tint = toneColor
                )
                Text(
                    text = indicator.sourceLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private val PillPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
private val TRAFFIC_STATUS_PRIMARY_SLOT_RESERVE_DP = 56.dp
