package com.example.xcpro.forecast

interface ForecastCatalogPort {
    suspend fun getParameters(): List<ForecastParameterMeta>

    fun getTimeSlots(
        nowUtcMs: Long,
        regionCode: String
    ): List<ForecastTimeSlot>
}

interface ForecastTilesPort {
    suspend fun getTileSpec(
        parameterId: ForecastParameterId,
        timeSlot: ForecastTimeSlot,
        regionCode: String
    ): ForecastTileSpec
}

interface ForecastLegendPort {
    suspend fun getLegend(
        parameterId: ForecastParameterId,
        timeSlot: ForecastTimeSlot,
        regionCode: String
    ): ForecastLegendSpec
}

interface ForecastValuePort {
    suspend fun getValue(
        latitude: Double,
        longitude: Double,
        parameterId: ForecastParameterId,
        timeSlot: ForecastTimeSlot,
        regionCode: String
    ): ForecastPointValue
}
