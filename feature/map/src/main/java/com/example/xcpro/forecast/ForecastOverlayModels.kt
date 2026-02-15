package com.example.xcpro.forecast

@JvmInline
value class ForecastParameterId(val value: String)

val DEFAULT_FORECAST_PARAMETER_ID = ForecastParameterId("THERMAL")

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
    val valueProperty: String = "idx"
)

enum class ForecastTileFormat {
    RASTER,
    VECTOR_INDEXED_FILL
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
    val validTimeUtcMs: Long
)

data class ForecastOverlayUiState(
    val enabled: Boolean = false,
    val opacity: Float = FORECAST_OPACITY_DEFAULT,
    val autoTimeEnabled: Boolean = FORECAST_AUTO_TIME_DEFAULT,
    val parameters: List<ForecastParameterMeta> = emptyList(),
    val selectedParameterId: ForecastParameterId = DEFAULT_FORECAST_PARAMETER_ID,
    val timeSlots: List<ForecastTimeSlot> = emptyList(),
    val selectedTimeUtcMs: Long? = null,
    val legend: ForecastLegendSpec? = null,
    val tileSpec: ForecastTileSpec? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
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
