package com.example.xcpro.forecast

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class FakeForecastProviderAdapter @Inject constructor() :
    ForecastCatalogPort,
    ForecastTilesPort,
    ForecastLegendPort,
    ForecastValuePort {

    private val parameters: List<ForecastParameterMeta> = listOf(
        ForecastParameterMeta(
            id = ForecastParameterId("THERMAL"),
            name = "Thermal",
            category = "Thermal",
            unitLabel = "m/s"
        ),
        ForecastParameterMeta(
            id = ForecastParameterId("CLOUDBASE"),
            name = "Cloudbase",
            category = "Cloud",
            unitLabel = "m"
        ),
        ForecastParameterMeta(
            id = ForecastParameterId("WIND_850"),
            name = "Wind 850hPa",
            category = "Wind",
            unitLabel = "kt"
        ),
        ForecastParameterMeta(
            id = ForecastParameterId("RAIN"),
            name = "Rain",
            category = "Precip",
            unitLabel = "mm/h"
        )
    )

    override suspend fun getParameters(): List<ForecastParameterMeta> = parameters

    override fun getTimeSlots(
        nowUtcMs: Long,
        regionCode: String
    ): List<ForecastTimeSlot> {
        val startUtcMs = roundDownToHour(nowUtcMs)
        return (0..24).map { hourOffset ->
            ForecastTimeSlot(validTimeUtcMs = startUtcMs + (hourOffset * HOUR_MS))
        }
    }

    override suspend fun getTileSpec(
        parameterId: ForecastParameterId,
        timeSlot: ForecastTimeSlot
    ): ForecastTileSpec {
        val slotBucket = timeSlot.validTimeUtcMs / HOUR_MS
        val urlTemplate = "$FAKE_TILE_TEMPLATE?layer=${parameterId.value.lowercase()}&slot=$slotBucket"
        return ForecastTileSpec(
            urlTemplate = urlTemplate,
            minZoom = 0,
            maxZoom = 18,
            tileSizePx = 256,
            attribution = "Map tiles provider"
        )
    }

    override suspend fun getLegend(parameterId: ForecastParameterId): ForecastLegendSpec {
        return when (parameterId.value.uppercase()) {
            "THERMAL" -> ForecastLegendSpec(
                unitLabel = "m/s",
                stops = listOf(
                    ForecastLegendStop(0.0, 0xFF0B3D91.toInt()),
                    ForecastLegendStop(1.0, 0xFF1976D2.toInt()),
                    ForecastLegendStop(2.0, 0xFF00ACC1.toInt()),
                    ForecastLegendStop(3.0, 0xFF43A047.toInt()),
                    ForecastLegendStop(4.0, 0xFFFDD835.toInt()),
                    ForecastLegendStop(5.0, 0xFFFB8C00.toInt()),
                    ForecastLegendStop(6.0, 0xFFE53935.toInt())
                )
            )

            "CLOUDBASE" -> ForecastLegendSpec(
                unitLabel = "m",
                stops = listOf(
                    ForecastLegendStop(500.0, 0xFF5D4037.toInt()),
                    ForecastLegendStop(1000.0, 0xFF8D6E63.toInt()),
                    ForecastLegendStop(1500.0, 0xFFA1887F.toInt()),
                    ForecastLegendStop(2000.0, 0xFFB0BEC5.toInt()),
                    ForecastLegendStop(2500.0, 0xFF90CAF9.toInt()),
                    ForecastLegendStop(3000.0, 0xFF64B5F6.toInt()),
                    ForecastLegendStop(3500.0, 0xFF42A5F5.toInt())
                )
            )

            "WIND_850" -> ForecastLegendSpec(
                unitLabel = "kt",
                stops = listOf(
                    ForecastLegendStop(0.0, 0xFF004D40.toInt()),
                    ForecastLegendStop(8.0, 0xFF00796B.toInt()),
                    ForecastLegendStop(16.0, 0xFF00897B.toInt()),
                    ForecastLegendStop(24.0, 0xFF26A69A.toInt()),
                    ForecastLegendStop(32.0, 0xFF80CBC4.toInt()),
                    ForecastLegendStop(40.0, 0xFFE0F2F1.toInt())
                )
            )

            else -> ForecastLegendSpec(
                unitLabel = "mm/h",
                stops = listOf(
                    ForecastLegendStop(0.0, 0xFFE3F2FD.toInt()),
                    ForecastLegendStop(2.0, 0xFF90CAF9.toInt()),
                    ForecastLegendStop(4.0, 0xFF42A5F5.toInt()),
                    ForecastLegendStop(8.0, 0xFF1E88E5.toInt()),
                    ForecastLegendStop(12.0, 0xFF1565C0.toInt()),
                    ForecastLegendStop(16.0, 0xFF0D47A1.toInt()),
                    ForecastLegendStop(20.0, 0xFF01579B.toInt())
                )
            )
        }
    }

    override suspend fun getValue(
        latitude: Double,
        longitude: Double,
        parameterId: ForecastParameterId,
        timeSlot: ForecastTimeSlot
    ): ForecastPointValue {
        val normalizedSeed = normalizedSeed(
            latitude = latitude,
            longitude = longitude,
            parameterId = parameterId,
            validTimeUtcMs = timeSlot.validTimeUtcMs
        )

        val (min, max, unitLabel) = when (parameterId.value.uppercase()) {
            "THERMAL" -> Triple(0.0, 6.0, "m/s")
            "CLOUDBASE" -> Triple(500.0, 3500.0, "m")
            "WIND_850" -> Triple(0.0, 40.0, "kt")
            else -> Triple(0.0, 20.0, "mm/h")
        }

        val rawValue = min + (max - min) * normalizedSeed
        val roundedValue = when (parameterId.value.uppercase()) {
            "CLOUDBASE" -> (rawValue / 10.0).roundToInt() * 10.0
            else -> (rawValue * 10.0).roundToInt() / 10.0
        }

        return ForecastPointValue(
            value = roundedValue,
            unitLabel = unitLabel,
            validTimeUtcMs = timeSlot.validTimeUtcMs
        )
    }

    private fun normalizedSeed(
        latitude: Double,
        longitude: Double,
        parameterId: ForecastParameterId,
        validTimeUtcMs: Long
    ): Double {
        val latBucket = (latitude * 1_000.0).roundToInt()
        val lonBucket = (longitude * 1_000.0).roundToInt()
        val hourBucket = validTimeUtcMs / HOUR_MS
        val stableKey = "${parameterId.value}|$latBucket|$lonBucket|$hourBucket"
        val positiveHash = stableKey.hashCode().toLong().and(0x7FFF_FFFFL)
        return positiveHash.toDouble() / 0x7FFF_FFFFL.toDouble()
    }

    private fun roundDownToHour(wallUtcMs: Long): Long = (wallUtcMs / HOUR_MS) * HOUR_MS

    private companion object {
        private const val HOUR_MS = 3_600_000L
        private const val FAKE_TILE_TEMPLATE = "https://tile.openstreetmap.fr/hot/{z}/{x}/{y}.png"
    }
}
