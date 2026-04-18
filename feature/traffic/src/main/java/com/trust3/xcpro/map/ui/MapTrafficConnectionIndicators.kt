package com.trust3.xcpro.map.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lens
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

fun TrafficConnectionIndicatorsUiState.visibleIndicatorCount(): Int =
    visibleIndicators().size

fun TrafficConnectionIndicatorsUiState.followingIndicatorTopOffset(): Dp =
    visibleIndicators().fold(0.dp) { total, indicator ->
        total + indicator.followingReserveBlockHeight()
    }

private fun TrafficConnectionIndicatorsUiState.visibleIndicators():
    List<TrafficConnectionIndicatorUiModel> = listOfNotNull(ogn, adsb)

@Composable
fun BoxScope.MapTrafficConnectionIndicatorsHost(
    indicators: TrafficConnectionIndicatorsUiState,
    reserveTopEndPrimarySlot: Boolean
) {
    val visibleIndicators = remember(indicators) {
        indicators.visibleIndicators()
    }
    if (visibleIndicators.isEmpty()) return

    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
        12.dp +
        if (reserveTopEndPrimarySlot) TRAFFIC_STATUS_PRIMARY_SLOT_RESERVE_DP else 0.dp
    Column(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = topPadding, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(TrafficIndicatorStackSpacing),
        horizontalAlignment = Alignment.End
    ) {
        visibleIndicators.forEach { indicator ->
            TrafficConnectionIndicatorChip(indicator = indicator)
        }
    }
}

@Composable
private fun TrafficConnectionIndicatorChip(
    indicator: TrafficConnectionIndicatorUiModel
) {
    when (val presentation = indicator.presentation) {
        TrafficConnectionIndicatorPresentation.Dot -> {
            TrafficConnectionIndicatorDot(indicator = indicator)
        }

        is TrafficConnectionIndicatorPresentation.LostCard -> {
            TrafficConnectionLostCard(
                indicator = indicator,
                message = presentation.message
            )
        }
    }
}

@Composable
private fun TrafficConnectionIndicatorDot(
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
        Box(
            modifier = Modifier.padding(IndicatorPadding),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Lens,
                contentDescription = null,
                tint = toneColor,
                modifier = Modifier.size(TrafficIndicatorDotSize)
            )
        }
    }
}

@Composable
private fun TrafficConnectionLostCard(
    indicator: TrafficConnectionIndicatorUiModel,
    message: String
) {
    val toneColor = when (indicator.tone) {
        TrafficConnectionIndicatorTone.GREEN -> Color(0xFF16A34A)
        TrafficConnectionIndicatorTone.RED -> Color(0xFFDC2626)
    }
    Surface(
        modifier = Modifier.semantics {
            contentDescription = message
        },
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, toneColor.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier
                .height(TrafficIndicatorLostCardHeight)
                .padding(horizontal = TrafficIndicatorLostCardHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(TrafficIndicatorLostCardGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Lens,
                contentDescription = null,
                tint = toneColor,
                modifier = Modifier.size(TrafficIndicatorLostCardDotSize)
            )
            Text(
                text = message,
                color = toneColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

private fun TrafficConnectionIndicatorUiModel.followingReserveBlockHeight(): Dp =
    indicatorContentHeight() + TrafficIndicatorStackSpacing

private fun TrafficConnectionIndicatorUiModel.indicatorContentHeight(): Dp =
    when (presentation) {
        TrafficConnectionIndicatorPresentation.Dot -> TrafficIndicatorDotSurfaceSize
        is TrafficConnectionIndicatorPresentation.LostCard -> TrafficIndicatorLostCardHeight
    }

private val IndicatorPadding = 0.9.dp
private val TrafficIndicatorDotSize = 10.8.dp
private val TrafficIndicatorDotSurfaceSize = TrafficIndicatorDotSize + IndicatorPadding + IndicatorPadding
private val TrafficIndicatorLostCardHeight = 18.dp
private val TrafficIndicatorLostCardHorizontalPadding = 6.dp
private val TrafficIndicatorLostCardGap = 4.dp
private val TrafficIndicatorLostCardDotSize = 6.dp
private val TrafficIndicatorStackSpacing = 12.dp
private val TRAFFIC_STATUS_PRIMARY_SLOT_RESERVE_DP = 56.dp
