package com.trust3.xcpro.map.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trust3.xcpro.forecast.ForecastOverlayUiState
import com.trust3.xcpro.forecast.ForecastParameterId

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ForecastOverlayBottomSheet(
    uiState: ForecastOverlayUiState,
    onDismiss: () -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onPrimaryParameterToggled: (ForecastParameterId) -> Unit,
    onWindOverlayEnabledChanged: (Boolean) -> Unit,
    onWindParameterSelected: (ForecastParameterId) -> Unit,
    onAutoTimeEnabledChanged: (Boolean) -> Unit,
    onFollowTimeOffsetChanged: (Int) -> Unit,
    onJumpToNow: () -> Unit,
    onTimeSelected: (Long) -> Unit,
    onSkySightSatelliteOverlayEnabledChanged: (Boolean) -> Unit,
    onSkySightSatelliteImageryEnabledChanged: (Boolean) -> Unit,
    onSkySightSatelliteRadarEnabledChanged: (Boolean) -> Unit,
    onSkySightSatelliteLightningEnabledChanged: (Boolean) -> Unit,
    onSkySightSatelliteAnimateEnabledChanged: (Boolean) -> Unit,
    onSkySightSatelliteHistoryFramesChanged: (Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        ForecastOverlayControlsContent(
            uiState = uiState,
            onEnabledChanged = onEnabledChanged,
            onPrimaryParameterToggled = onPrimaryParameterToggled,
            onWindOverlayEnabledChanged = onWindOverlayEnabledChanged,
            onWindParameterSelected = onWindParameterSelected,
            onAutoTimeEnabledChanged = onAutoTimeEnabledChanged,
            onFollowTimeOffsetChanged = onFollowTimeOffsetChanged,
            onJumpToNow = onJumpToNow,
            onTimeSelected = onTimeSelected,
            onSkySightSatelliteOverlayEnabledChanged = onSkySightSatelliteOverlayEnabledChanged,
            onSkySightSatelliteImageryEnabledChanged = onSkySightSatelliteImageryEnabledChanged,
            onSkySightSatelliteRadarEnabledChanged = onSkySightSatelliteRadarEnabledChanged,
            onSkySightSatelliteLightningEnabledChanged = onSkySightSatelliteLightningEnabledChanged,
            onSkySightSatelliteAnimateEnabledChanged = onSkySightSatelliteAnimateEnabledChanged,
            onSkySightSatelliteHistoryFramesChanged = onSkySightSatelliteHistoryFramesChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        )
    }
}
