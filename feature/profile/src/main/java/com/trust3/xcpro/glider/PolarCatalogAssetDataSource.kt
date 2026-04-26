package com.trust3.xcpro.glider

import android.content.Context
import com.google.gson.Gson
import com.trust3.xcpro.common.glider.GliderAircraftTypes
import com.trust3.xcpro.common.glider.GliderModel
import com.trust3.xcpro.common.glider.PolarPoint
import com.trust3.xcpro.common.glider.SpeedLimits
import com.trust3.xcpro.common.glider.WaterBallastCapacity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PolarCatalogAssetDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()

    fun loadModels(): List<GliderModel> {
        val catalog = readJson("polars/catalog.json", PolarCatalogDto::class.java)
        return catalog.aircraft.orEmpty()
            .mapNotNull { indexEntry -> indexEntry.path?.let { readJson(it, PolarAircraftDto::class.java) } }
            .mapNotNull(::toModel)
    }

    private fun toModel(dto: PolarAircraftDto): GliderModel? {
        val id = dto.id?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val name = dto.name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val classLabel = dto.classLabel?.trim()?.takeIf { it.isNotEmpty() } ?: ""
        val points = dto.polarPoints.orEmpty()
            .mapNotNull { point ->
                val speedMs = point.speedMs
                val sinkMs = point.sinkMs
                if (
                    speedMs != null &&
                    sinkMs != null &&
                    speedMs.isFinite() &&
                    sinkMs.isFinite() &&
                    speedMs > 0.0 &&
                    sinkMs > 0.0
                ) {
                    PolarPoint(speedMs = speedMs, sinkMs = sinkMs)
                } else {
                    null
                }
            }
            .takeIf { it.isNotEmpty() }

        return GliderModel(
            id = id,
            aircraftType = GliderAircraftTypes.normalize(dto.type),
            name = name,
            classLabel = classLabel,
            wingSpanM = dto.wingSpanM,
            wingAreaM2 = dto.wingAreaM2,
            emptyWeightKg = dto.emptyWeightKg,
            maxWeightKg = dto.maxWeightKg,
            bestLD = dto.bestLD,
            bestLDSpeedMs = dto.bestLDSpeedMs,
            minSinkMs = dto.minSinkMs,
            minSinkSpeedMs = dto.minSinkSpeedMs,
            points = points,
            water = dto.water,
            speedLimits = dto.speedLimits,
            minWingLoadingKgM2 = dto.minWingLoadingKgM2,
            maxWingLoadingKgM2 = dto.maxWingLoadingKgM2
        )
    }

    private fun <T> readJson(path: String, type: Class<T>): T =
        context.assets.open(path).bufferedReader().use { reader ->
            gson.fromJson(reader, type)
        }

    private data class PolarCatalogDto(
        val schemaVersion: Int? = null,
        val aircraft: List<PolarCatalogEntryDto>? = null
    )

    private data class PolarCatalogEntryDto(
        val id: String? = null,
        val type: String? = null,
        val name: String? = null,
        val path: String? = null
    )

    private data class PolarAircraftDto(
        val schemaVersion: Int? = null,
        val id: String? = null,
        val type: String? = null,
        val name: String? = null,
        val classLabel: String? = null,
        val wingSpanM: Double? = null,
        val wingAreaM2: Double? = null,
        val emptyWeightKg: Int? = null,
        val maxWeightKg: Int? = null,
        val bestLD: Double? = null,
        val bestLDSpeedMs: Double? = null,
        val minSinkMs: Double? = null,
        val minSinkSpeedMs: Double? = null,
        val minWingLoadingKgM2: Double? = null,
        val maxWingLoadingKgM2: Double? = null,
        val water: WaterBallastCapacity? = null,
        val speedLimits: SpeedLimits? = null,
        val polarPoints: List<PolarPointDto>? = null
    )

    private data class PolarPointDto(
        val speedMs: Double? = null,
        val sinkMs: Double? = null
    )
}
