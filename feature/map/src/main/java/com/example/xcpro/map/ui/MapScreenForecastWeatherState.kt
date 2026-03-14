package com.example.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcpro.forecast.ForecastOverlayUiState
import com.example.xcpro.forecast.ForecastOverlayViewModel
import com.example.xcpro.forecast.ForecastPointCallout
import com.example.xcpro.forecast.ForecastTileFormat
import com.example.xcpro.forecast.ForecastWindDisplayMode
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.weather.rain.WeatherOverlayRuntimeState
import com.example.xcpro.weather.rain.WeatherOverlayViewModel
import org.maplibre.android.maps.MapLibreMap

internal data class MapScreenForecastWeatherState(
    val forecastViewModel: ForecastOverlayViewModel,
    val forecastOverlayState: ForecastOverlayUiState,
    val forecastPointCallout: ForecastPointCallout?,
    val forecastQueryStatus: String?,
    val weatherOverlayState: WeatherOverlayRuntimeState,
    val skySightWarningMessage: String?,
    val skySightErrorMessage: String?,
    val isForecastWindArrowOverlayActive: Boolean
)

@Composable
internal fun rememberMapScreenForecastWeatherState(
    mapLibreMap: MapLibreMap?,
    currentLocation: MapLocationUiModel?,
    overlayManager: MapOverlayManager
): MapScreenForecastWeatherState {
    val forecastViewModel: ForecastOverlayViewModel = hiltViewModel()
    val forecastOverlayState by forecastViewModel.overlayState.collectAsStateWithLifecycle()
    val forecastPointCallout by forecastViewModel.pointCallout.collectAsStateWithLifecycle()
    val forecastQueryStatus by forecastViewModel.queryStatus.collectAsStateWithLifecycle()

    val weatherOverlayViewModel: WeatherOverlayViewModel = hiltViewModel()
    val weatherOverlayState by weatherOverlayViewModel.overlayState.collectAsStateWithLifecycle()

    val forecastRuntimeWarning by overlayManager.forecastRuntimeWarningMessage.collectAsStateWithLifecycle()
    val skySightSatelliteRuntimeError by overlayManager.skySightSatelliteRuntimeErrorMessage.collectAsStateWithLifecycle()

    val skySightRegionCoverageWarning = computeSkySightRegionCoverageWarning(
        mapLibreMap = mapLibreMap,
        fallbackLocation = currentLocation,
        regionCode = forecastOverlayState.selectedRegionCode
    )
    val skySightRainArbitrationWarning = computeSkySightRainSuppressionWarning(
        forecastOverlayState = forecastOverlayState,
        rainViewerEnabled = weatherOverlayState.enabled
    )
    val skySightUiMessages = resolveSkySightUiMessages(
        repositoryWarningMessage = forecastOverlayState.warningMessage,
        regionCoverageWarningMessage = skySightRegionCoverageWarning,
        runtimeWarningMessage = forecastRuntimeWarning,
        runtimeArbitrationWarningMessage = skySightRainArbitrationWarning,
        repositoryErrorMessage = forecastOverlayState.errorMessage,
        runtimeErrorMessage = skySightSatelliteRuntimeError
    )
    val isForecastWindArrowOverlayActive = forecastOverlayState.windOverlayEnabled &&
        forecastOverlayState.windDisplayMode == ForecastWindDisplayMode.ARROW &&
        forecastOverlayState.windTileSpec?.format == ForecastTileFormat.VECTOR_WIND_POINTS

    return MapScreenForecastWeatherState(
        forecastViewModel = forecastViewModel,
        forecastOverlayState = forecastOverlayState,
        forecastPointCallout = forecastPointCallout,
        forecastQueryStatus = forecastQueryStatus,
        weatherOverlayState = weatherOverlayState,
        skySightWarningMessage = skySightUiMessages.warningMessage,
        skySightErrorMessage = skySightUiMessages.errorMessage,
        isForecastWindArrowOverlayActive = isForecastWindArrowOverlayActive
    )
}
