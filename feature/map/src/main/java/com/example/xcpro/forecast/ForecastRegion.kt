package com.example.xcpro.forecast

data class ForecastRegionOption(
    val code: String,
    val label: String
)

const val DEFAULT_FORECAST_REGION_CODE = "WEST_US"

val FORECAST_REGION_OPTIONS: List<ForecastRegionOption> = listOf(
    ForecastRegionOption(code = "WEST_US", label = "West US"),
    ForecastRegionOption(code = "EAST_US", label = "East US"),
    ForecastRegionOption(code = "EUROPE", label = "Europe"),
    ForecastRegionOption(code = "EAST_AUS", label = "East Australia"),
    ForecastRegionOption(code = "WA", label = "Western Australia"),
    ForecastRegionOption(code = "NZ", label = "New Zealand"),
    ForecastRegionOption(code = "JAPAN", label = "Japan"),
    ForecastRegionOption(code = "ARGENTINA_CHILE", label = "Argentina / Chile"),
    ForecastRegionOption(code = "SANEW", label = "South Africa / Namibia"),
    ForecastRegionOption(code = "BRAZIL", label = "Brazil"),
    ForecastRegionOption(code = "HRRR", label = "HRRR (US High Resolution)"),
    ForecastRegionOption(code = "ICONEU", label = "ICON Europe")
)

fun normalizeForecastRegionCode(rawCode: String?): String {
    val normalized = rawCode
        ?.trim()
        .orEmpty()
        .uppercase()
    return if (FORECAST_REGION_OPTIONS.any { it.code == normalized }) {
        normalized
    } else {
        DEFAULT_FORECAST_REGION_CODE
    }
}

fun forecastRegionLabel(regionCode: String): String =
    FORECAST_REGION_OPTIONS
        .firstOrNull { it.code == normalizeForecastRegionCode(regionCode) }
        ?.label
        ?: regionCode
