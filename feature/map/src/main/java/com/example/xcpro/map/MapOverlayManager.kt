package com.example.xcpro.map

import android.content.Context
import android.util.Log
import com.example.xcpro.core.time.TimeBridge
import com.example.xcpro.adsb.Icao24
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.adsb.ADSB_ICON_SIZE_DEFAULT_PX
import com.example.xcpro.adsb.clampAdsbIconSizePx
import com.example.xcpro.airspace.AirspaceUseCase
import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.forecast.FORECAST_OPACITY_DEFAULT
import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_ANIMATE_ENABLED_DEFAULT
import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_DEFAULT
import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_IMAGERY_ENABLED_DEFAULT
import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_LIGHTNING_ENABLED_DEFAULT
import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_OVERLAY_ENABLED_DEFAULT
import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_RADAR_ENABLED_DEFAULT
import com.example.xcpro.forecast.FORECAST_WIND_OVERLAY_SCALE_DEFAULT
import com.example.xcpro.forecast.ForecastLegendSpec
import com.example.xcpro.forecast.ForecastTileFormat
import com.example.xcpro.forecast.ForecastTileSpec
import com.example.xcpro.forecast.ForecastWindDisplayMode
import com.example.xcpro.forecast.clampForecastOpacity
import com.example.xcpro.forecast.clampForecastWindOverlayScale
import com.example.xcpro.forecast.FORECAST_WIND_DISPLAY_MODE_DEFAULT
import com.example.xcpro.forecast.clampSkySightSatelliteHistoryFrames
import com.example.xcpro.flightdata.WaypointFilesUseCase
import com.example.xcpro.map.BuildConfig
import com.example.xcpro.ogn.OGN_ICON_SIZE_DEFAULT_PX
import com.example.xcpro.ogn.OgnDisplayUpdateMode
import com.example.xcpro.ogn.OgnGliderTrailSegment
import com.example.xcpro.ogn.OgnTrafficTarget
import com.example.xcpro.ogn.OgnThermalHotspot
import com.example.xcpro.ogn.clampOgnIconSizePx
import com.example.xcpro.weather.rain.WEATHER_RAIN_OPACITY_DEFAULT
import com.example.xcpro.weather.rain.WEATHER_RAIN_STALE_DIMMED_OPACITY_MAX
import com.example.xcpro.weather.rain.WEATHER_RAIN_TRANSITION_DURATION_BALANCED_MS
import com.example.xcpro.weather.rain.WeatherRainFrameSelection
import com.example.xcpro.weather.rain.WeatherRadarStatusCode
import com.example.xcpro.weather.rain.clampWeatherRainOpacity
import com.example.xcpro.loadAndApplyAirspace
import com.example.xcpro.loadAndApplyWaypoints
import com.example.xcpro.map.trail.SnailTrailManager
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

class MapOverlayManager(
    context: Context,
    mapState: MapScreenState,
    mapStateReader: MapStateReader,
    taskRenderSyncCoordinator: TaskRenderSyncCoordinator,
    taskWaypointCountProvider: () -> Int,
    stateActions: MapStateActions,
    snailTrailManager: SnailTrailManager,
    coroutineScope: CoroutineScope,
    airspaceUseCase: AirspaceUseCase,
    waypointFilesUseCase: WaypointFilesUseCase,
    ognTrafficOverlayFactory: (MapLibreMap, Int, Boolean) -> OgnTrafficOverlay =
        { map, iconSizePx, useSatelliteContrastIcons ->
            OgnTrafficOverlay(
                context = context,
                map = map,
                initialIconSizePx = iconSizePx,
                initialUseSatelliteContrastIcons = useSatelliteContrastIcons
            )
        },
    ognThermalOverlayFactory: (MapLibreMap) -> OgnThermalOverlay =
        { map -> OgnThermalOverlay(map = map) },
    ognGliderTrailOverlayFactory: (MapLibreMap) -> OgnGliderTrailOverlay =
        { map -> OgnGliderTrailOverlay(map = map) },
    adsbTrafficOverlayFactory: (MapLibreMap, Int) -> AdsbTrafficOverlay =
        { map, iconSizePx ->
            AdsbTrafficOverlay(
                context = context,
                map = map,
                initialIconSizePx = iconSizePx
            )
        }
) : MapOverlayManagerRuntime(
    context = context,
    mapState = mapState,
    mapStateReader = mapStateReader,
    taskRenderSyncCoordinator = taskRenderSyncCoordinator,
    taskWaypointCountProvider = taskWaypointCountProvider,
    stateActions = stateActions,
    snailTrailManager = snailTrailManager,
    coroutineScope = coroutineScope,
    airspaceUseCase = airspaceUseCase,
    waypointFilesUseCase = waypointFilesUseCase,
    ognTrafficOverlayFactory = ognTrafficOverlayFactory,
    ognThermalOverlayFactory = ognThermalOverlayFactory,
    ognGliderTrailOverlayFactory = ognGliderTrailOverlayFactory,
    adsbTrafficOverlayFactory = adsbTrafficOverlayFactory
)
