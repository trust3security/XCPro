package com.example.xcpro.forecast

import java.time.Instant
import java.time.ZoneId

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

private val FORECAST_REGION_TIMEZONES: Map<String, String> = mapOf(
    "WEST_US" to "America/Los_Angeles",
    "EAST_US" to "America/New_York",
    "EUROPE" to "Europe/Berlin",
    "EAST_AUS" to "Australia/Sydney",
    "WA" to "Australia/Perth",
    "NZ" to "Pacific/Auckland",
    "JAPAN" to "Asia/Tokyo",
    "ARGENTINA_CHILE" to "America/Argentina/Buenos_Aires",
    "SANEW" to "Africa/Johannesburg",
    "BRAZIL" to "America/Sao_Paulo",
    "HRRR" to "America/Denver",
    "ICONEU" to "Europe/Berlin"
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

fun forecastRegionZoneId(regionCode: String): ZoneId {
    val normalized = normalizeForecastRegionCode(regionCode)
    val timezone = FORECAST_REGION_TIMEZONES[normalized] ?: "UTC"
    return ZoneId.of(timezone)
}

fun forecastRegionLocalDayBucket(
    utcMs: Long,
    regionCode: String
): Long = Instant.ofEpochMilli(utcMs)
    .atZone(forecastRegionZoneId(regionCode))
    .toLocalDate()
    .toEpochDay()

fun forecastRegionLabel(regionCode: String): String =
    FORECAST_REGION_OPTIONS
        .firstOrNull { it.code == normalizeForecastRegionCode(regionCode) }
        ?.label
        ?: regionCode
