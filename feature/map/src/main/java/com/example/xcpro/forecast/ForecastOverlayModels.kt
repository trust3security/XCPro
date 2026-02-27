package com.example.xcpro.forecast

@JvmInline
value class ForecastParameterId(val value: String)

val DEFAULT_FORECAST_PARAMETER_ID = ForecastParameterId("wstar_bsratio")

data class ForecastParameterMeta(
    val id: ForecastParameterId,
    val name: String,
    val category: String,
    val unitLabel: String,
    val supportsLegend: Boolean = true,
    val supportsPointValue: Boolean = true,
    val supportsTiles: Boolean = true
)

data class ForecastTimeSlot(
    val validTimeUtcMs: Long
)

data class ForecastTileSpec(
    val urlTemplate: String,
    val minZoom: Int,
    val maxZoom: Int,
    val tileSizePx: Int = 256,
    val attribution: String = "Map tiles provider",
    val format: ForecastTileFormat = ForecastTileFormat.RASTER,
    val sourceLayer: String? = null,
    val sourceLayerCandidates: List<String> = emptyList(),
    val valueProperty: String = "idx",
    val speedProperty: String? = null,
    val directionProperty: String? = null
)

enum class ForecastTileFormat {
    RASTER,
    VECTOR_INDEXED_FILL,
    VECTOR_WIND_POINTS
}

data class ForecastLegendStop(
    val value: Double,
    val argb: Int
)

data class ForecastLegendSpec(
    val unitLabel: String,
    val stops: List<ForecastLegendStop>
)

data class ForecastPointValue(
    val value: Double,
    val unitLabel: String,
    val validTimeUtcMs: Long,
    val directionFromDeg: Double? = null
)

data class ForecastOverlayUiState(
    val enabled: Boolean = false,
    val opacity: Float = FORECAST_OPACITY_DEFAULT,
    val windOverlayScale: Float = FORECAST_WIND_OVERLAY_SCALE_DEFAULT,
    val windOverlayEnabled: Boolean = FORECAST_WIND_OVERLAY_ENABLED_DEFAULT,
    val windDisplayMode: ForecastWindDisplayMode = FORECAST_WIND_DISPLAY_MODE_DEFAULT,
    val skySightSatelliteOverlayEnabled: Boolean = FORECAST_SKYSIGHT_SATELLITE_OVERLAY_ENABLED_DEFAULT,
    val skySightSatelliteImageryEnabled: Boolean = FORECAST_SKYSIGHT_SATELLITE_IMAGERY_ENABLED_DEFAULT,
    val skySightSatelliteRadarEnabled: Boolean = FORECAST_SKYSIGHT_SATELLITE_RADAR_ENABLED_DEFAULT,
    val skySightSatelliteLightningEnabled: Boolean = FORECAST_SKYSIGHT_SATELLITE_LIGHTNING_ENABLED_DEFAULT,
    val skySightSatelliteAnimateEnabled: Boolean = FORECAST_SKYSIGHT_SATELLITE_ANIMATE_ENABLED_DEFAULT,
    val skySightSatelliteHistoryFrames: Int = FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_DEFAULT,
    val selectedRegionCode: String = DEFAULT_FORECAST_REGION_CODE,
    val autoTimeEnabled: Boolean = FORECAST_AUTO_TIME_DEFAULT,
    val followTimeOffsetMinutes: Int = FORECAST_FOLLOW_TIME_OFFSET_MINUTES_DEFAULT,
    val primaryParameters: List<ForecastParameterMeta> = emptyList(),
    val selectedPrimaryParameterId: ForecastParameterId = DEFAULT_FORECAST_PARAMETER_ID,
    val secondaryPrimaryOverlayEnabled: Boolean = FORECAST_SECONDARY_PRIMARY_OVERLAY_ENABLED_DEFAULT,
    val selectedSecondaryPrimaryParameterId: ForecastParameterId = DEFAULT_FORECAST_SECONDARY_PRIMARY_PARAMETER_ID,
    val windParameters: List<ForecastParameterMeta> = emptyList(),
    val selectedWindParameterId: ForecastParameterId = DEFAULT_FORECAST_WIND_PARAMETER_ID,
    val timeSlots: List<ForecastTimeSlot> = emptyList(),
    val selectedTimeUtcMs: Long? = null,
    val primaryLegend: ForecastLegendSpec? = null,
    val primaryTileSpec: ForecastTileSpec? = null,
    val secondaryPrimaryLegend: ForecastLegendSpec? = null,
    val secondaryPrimaryTileSpec: ForecastTileSpec? = null,
    val windLegend: ForecastLegendSpec? = null,
    val windTileSpec: ForecastTileSpec? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val warningMessage: String? = null
)

data class ForecastPointCallout(
    val latitude: Double,
    val longitude: Double,
    val pointValue: ForecastPointValue
)

sealed interface ForecastPointQueryResult {
    data class Success(
        val latitude: Double,
        val longitude: Double,
        val pointValue: ForecastPointValue
    ) : ForecastPointQueryResult

    data class Unavailable(
        val reason: String
    ) : ForecastPointQueryResult

    data class Error(
        val message: String
    ) : ForecastPointQueryResult
}
