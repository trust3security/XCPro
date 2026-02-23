package com.example.xcpro.forecast

data class ForecastRegionCoverageBounds(
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double
)

fun forecastRegionLikelyContainsCoordinate(
    regionCode: String,
    latitude: Double,
    longitude: Double
): Boolean {
    if (!latitude.isFinite() || !longitude.isFinite()) return true
    if (latitude < -90.0 || latitude > 90.0) return false

    val normalizedRegionCode = normalizeForecastRegionCode(regionCode)
    val bounds = FORECAST_REGION_COVERAGE_BOUNDS[normalizedRegionCode] ?: return true
    val normalizedLongitude = normalizeLongitude(longitude)
    val latitudeInBounds = latitude in bounds.minLatitude..bounds.maxLatitude
    val longitudeInBounds = if (bounds.minLongitude <= bounds.maxLongitude) {
        normalizedLongitude in bounds.minLongitude..bounds.maxLongitude
    } else {
        normalizedLongitude >= bounds.minLongitude || normalizedLongitude <= bounds.maxLongitude
    }
    return latitudeInBounds && longitudeInBounds
}

private fun normalizeLongitude(longitude: Double): Double {
    val wrapped = ((longitude + 180.0) % 360.0 + 360.0) % 360.0
    return wrapped - 180.0
}

private val FORECAST_REGION_COVERAGE_BOUNDS: Map<String, ForecastRegionCoverageBounds> = mapOf(
    "WEST_US" to ForecastRegionCoverageBounds(
        minLatitude = 20.0,
        maxLatitude = 72.0,
        minLongitude = -170.0,
        maxLongitude = -95.0
    ),
    "EAST_US" to ForecastRegionCoverageBounds(
        minLatitude = 20.0,
        maxLatitude = 72.0,
        minLongitude = -105.0,
        maxLongitude = -55.0
    ),
    "EUROPE" to ForecastRegionCoverageBounds(
        minLatitude = 30.0,
        maxLatitude = 72.0,
        minLongitude = -15.0,
        maxLongitude = 45.0
    ),
    "EAST_AUS" to ForecastRegionCoverageBounds(
        minLatitude = -45.0,
        maxLatitude = -10.0,
        minLongitude = 138.0,
        maxLongitude = 160.0
    ),
    "WA" to ForecastRegionCoverageBounds(
        minLatitude = -38.0,
        maxLatitude = -10.0,
        minLongitude = 108.0,
        maxLongitude = 130.0
    ),
    "NZ" to ForecastRegionCoverageBounds(
        minLatitude = -48.0,
        maxLatitude = -33.0,
        minLongitude = 165.0,
        maxLongitude = 179.9
    ),
    "JAPAN" to ForecastRegionCoverageBounds(
        minLatitude = 24.0,
        maxLatitude = 46.0,
        minLongitude = 122.0,
        maxLongitude = 148.0
    ),
    "ARGENTINA_CHILE" to ForecastRegionCoverageBounds(
        minLatitude = -56.0,
        maxLatitude = -17.0,
        minLongitude = -76.0,
        maxLongitude = -53.0
    ),
    "SANEW" to ForecastRegionCoverageBounds(
        minLatitude = -36.0,
        maxLatitude = -14.0,
        minLongitude = 10.0,
        maxLongitude = 36.0
    ),
    "BRAZIL" to ForecastRegionCoverageBounds(
        minLatitude = -35.0,
        maxLatitude = 6.0,
        minLongitude = -75.0,
        maxLongitude = -30.0
    ),
    "HRRR" to ForecastRegionCoverageBounds(
        minLatitude = 20.0,
        maxLatitude = 55.0,
        minLongitude = -130.0,
        maxLongitude = -60.0
    ),
    "ICONEU" to ForecastRegionCoverageBounds(
        minLatitude = 30.0,
        maxLatitude = 72.0,
        minLongitude = -15.0,
        maxLongitude = 45.0
    )
)
