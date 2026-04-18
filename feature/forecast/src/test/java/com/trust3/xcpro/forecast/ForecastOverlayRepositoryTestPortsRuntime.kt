package com.trust3.xcpro.forecast

import kotlinx.coroutines.CancellationException

internal class CountingTilesPort : ForecastTilesPort {
    var calls: Int = 0

    override suspend fun getTileSpec(
        parameterId: ForecastParameterId,
        timeSlot: ForecastTimeSlot,
        regionCode: String
    ): ForecastTileSpec {
        calls += 1
        return ForecastTileSpec(
            urlTemplate = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
            minZoom = 0,
            maxZoom = 18
        )
    }
}

internal class CountingLegendPort : ForecastLegendPort {
    var calls: Int = 0

    override suspend fun getLegend(
        parameterId: ForecastParameterId,
        timeSlot: ForecastTimeSlot,
        regionCode: String
    ): ForecastLegendSpec {
        calls += 1
        return ForecastLegendSpec(
            unitLabel = "m/s",
            stops = listOf(
                ForecastLegendStop(value = 0.0, argb = 0xFF000000.toInt()),
                ForecastLegendStop(value = 1.0, argb = 0xFFFFFFFF.toInt())
            )
        )
    }
}

internal class RecordingTilesPort : ForecastTilesPort {
    val requestedParameterIds = mutableListOf<ForecastParameterId>()

    override suspend fun getTileSpec(
        parameterId: ForecastParameterId,
        timeSlot: ForecastTimeSlot,
        regionCode: String
    ): ForecastTileSpec {
        requestedParameterIds.add(parameterId)
        return ForecastTileSpec(
            urlTemplate = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
            minZoom = 0,
            maxZoom = 18
        )
    }
}

internal class RecordingLegendPort : ForecastLegendPort {
    val requestedParameterIds = mutableListOf<ForecastParameterId>()

    override suspend fun getLegend(
        parameterId: ForecastParameterId,
        timeSlot: ForecastTimeSlot,
        regionCode: String
    ): ForecastLegendSpec {
        requestedParameterIds.add(parameterId)
        return ForecastLegendSpec(
            unitLabel = "m/s",
            stops = listOf(
                ForecastLegendStop(value = 0.0, argb = 0xFF000000.toInt()),
                ForecastLegendStop(value = 1.0, argb = 0xFFFFFFFF.toInt())
            )
        )
    }
}

internal class LowercaseCatalogPort : ForecastCatalogPort, ForecastValuePort {
    override suspend fun getParameters(): List<ForecastParameterMeta> = listOf(
        ForecastParameterMeta(
            id = ForecastParameterId("wstar_bsratio"),
            name = "Thermal",
            category = "Thermal",
            unitLabel = "m/s"
        ),
        ForecastParameterMeta(
            id = ForecastParameterId("dwcrit"),
            name = "Thermal Height",
            category = "Thermal",
            unitLabel = "m"
        )
    )

    override fun getTimeSlots(
        nowUtcMs: Long,
        regionCode: String
    ): List<ForecastTimeSlot> = listOf(
        ForecastTimeSlot(validTimeUtcMs = nowUtcMs)
    )

    override suspend fun getValue(
        latitude: Double,
        longitude: Double,
        parameterId: ForecastParameterId,
        timeSlot: ForecastTimeSlot,
        regionCode: String
    ): ForecastPointValue = ForecastPointValue(
        value = 0.0,
        unitLabel = "m",
        validTimeUtcMs = timeSlot.validTimeUtcMs
    )
}

internal class OffsetAwareCatalogPort : ForecastCatalogPort, ForecastValuePort {
    override suspend fun getParameters(): List<ForecastParameterMeta> = listOf(
        ForecastParameterMeta(
            id = DEFAULT_FORECAST_PARAMETER_ID,
            name = "Thermal",
            category = "Thermal",
            unitLabel = "m/s"
        )
    )

    override fun getTimeSlots(
        nowUtcMs: Long,
        regionCode: String
    ): List<ForecastTimeSlot> = listOf(
        ForecastTimeSlot(validTimeUtcMs = nowUtcMs - 60 * 60_000L),
        ForecastTimeSlot(validTimeUtcMs = nowUtcMs - 30 * 60_000L),
        ForecastTimeSlot(validTimeUtcMs = nowUtcMs),
        ForecastTimeSlot(validTimeUtcMs = nowUtcMs + 30 * 60_000L),
        ForecastTimeSlot(validTimeUtcMs = nowUtcMs + 60 * 60_000L)
    )

    override suspend fun getValue(
        latitude: Double,
        longitude: Double,
        parameterId: ForecastParameterId,
        timeSlot: ForecastTimeSlot,
        regionCode: String
    ): ForecastPointValue = ForecastPointValue(
        value = 0.0,
        unitLabel = "m/s",
        validTimeUtcMs = timeSlot.validTimeUtcMs
    )
}

internal class RainConvergenceCatalogPort : ForecastCatalogPort {
    override suspend fun getParameters(): List<ForecastParameterMeta> = listOf(
        ForecastParameterMeta(
            id = ForecastParameterId("accrain"),
            name = "Rain",
            category = "Precip",
            unitLabel = "mm/h"
        ),
        ForecastParameterMeta(
            id = ForecastParameterId("wblmaxmin"),
            name = "Convergence",
            category = "Lift",
            unitLabel = "m/s",
            supportsPointValue = false
        )
    )

    override fun getTimeSlots(
        nowUtcMs: Long,
        regionCode: String
    ): List<ForecastTimeSlot> = listOf(
        ForecastTimeSlot(validTimeUtcMs = nowUtcMs)
    )
}

internal class ConvergenceCatalogPort : ForecastCatalogPort {
    override suspend fun getParameters(): List<ForecastParameterMeta> = listOf(
        ForecastParameterMeta(
            id = ForecastParameterId("wblmaxmin"),
            name = "Convergence",
            category = "Lift",
            unitLabel = "m/s",
            supportsPointValue = false
        )
    )

    override fun getTimeSlots(
        nowUtcMs: Long,
        regionCode: String
    ): List<ForecastTimeSlot> = listOf(
        ForecastTimeSlot(validTimeUtcMs = nowUtcMs)
    )
}

internal class CountingValuePort : ForecastValuePort {
    var calls: Int = 0

    override suspend fun getValue(
        latitude: Double,
        longitude: Double,
        parameterId: ForecastParameterId,
        timeSlot: ForecastTimeSlot,
        regionCode: String
    ): ForecastPointValue {
        calls += 1
        return ForecastPointValue(
            value = 0.0,
            unitLabel = "m/s",
            validTimeUtcMs = timeSlot.validTimeUtcMs
        )
    }
}

internal class CancellingTilesPort : ForecastTilesPort {
    override suspend fun getTileSpec(
        parameterId: ForecastParameterId,
        timeSlot: ForecastTimeSlot,
        regionCode: String
    ): ForecastTileSpec {
        throw CancellationException("cancel tile fetch")
    }
}

internal class CancellingValuePort : ForecastValuePort {
    override suspend fun getValue(
        latitude: Double,
        longitude: Double,
        parameterId: ForecastParameterId,
        timeSlot: ForecastTimeSlot,
        regionCode: String
    ): ForecastPointValue {
        throw CancellationException("cancel point query")
    }
}

internal class FailingTilesPort(
    private val message: String
) : ForecastTilesPort {
    override suspend fun getTileSpec(
        parameterId: ForecastParameterId,
        timeSlot: ForecastTimeSlot,
        regionCode: String
    ): ForecastTileSpec {
        throw IllegalStateException(message)
    }
}

internal class FailingLegendPort(
    private val message: String
) : ForecastLegendPort {
    override suspend fun getLegend(
        parameterId: ForecastParameterId,
        timeSlot: ForecastTimeSlot,
        regionCode: String
    ): ForecastLegendSpec {
        throw IllegalStateException(message)
    }
}

internal class CountingCatalogOnlyPort : ForecastCatalogPort, ForecastValuePort {
    var parametersCalls: Int = 0
    var timeSlotsCalls: Int = 0

    override suspend fun getParameters(): List<ForecastParameterMeta> {
        parametersCalls += 1
        return listOf(
            ForecastParameterMeta(
                id = DEFAULT_FORECAST_PARAMETER_ID,
                name = "Thermal",
                category = "Thermal",
                unitLabel = "m/s"
            ),
            ForecastParameterMeta(
                id = DEFAULT_FORECAST_WIND_PARAMETER_ID,
                name = "Wind",
                category = "Wind",
                unitLabel = "kt"
            )
        )
    }

    override fun getTimeSlots(
        nowUtcMs: Long,
        regionCode: String
    ): List<ForecastTimeSlot> {
        timeSlotsCalls += 1
        return listOf(ForecastTimeSlot(validTimeUtcMs = nowUtcMs))
    }

    override suspend fun getValue(
        latitude: Double,
        longitude: Double,
        parameterId: ForecastParameterId,
        timeSlot: ForecastTimeSlot,
        regionCode: String
    ): ForecastPointValue = ForecastPointValue(
        value = 0.0,
        unitLabel = "m/s",
        validTimeUtcMs = timeSlot.validTimeUtcMs
    )
}
