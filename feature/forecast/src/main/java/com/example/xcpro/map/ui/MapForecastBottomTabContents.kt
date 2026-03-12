package com.example.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.xcpro.forecast.ForecastOverlayUiState
import com.example.xcpro.forecast.ForecastParameterId

@Composable
fun MapForecastSkySightTabContent(
    title: String,
    uiState: ForecastOverlayUiState,
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
    onSkySightSatelliteHistoryFramesChanged: (Int) -> Unit,
    satViewEnabled: Boolean,
    onSatViewEnabledChanged: (Boolean) -> Unit,
    warningMessage: String?,
    errorMessage: String?,
    modifier: Modifier = Modifier
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
        satViewEnabled = satViewEnabled,
        onSatViewEnabledChanged = onSatViewEnabledChanged,
        title = title,
        warningMessage = warningMessage,
        errorMessage = errorMessage,
        modifier = modifier
    )
}
