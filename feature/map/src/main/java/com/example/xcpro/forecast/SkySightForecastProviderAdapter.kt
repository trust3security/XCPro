package com.example.xcpro.forecast

import com.example.xcpro.common.di.IoDispatcher
import com.example.xcpro.di.ForecastHttpClient
import com.example.xcpro.di.SkySightApiKey
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class SkySightForecastProviderAdapter @Inject constructor(
    @ForecastHttpClient private val httpClient: OkHttpClient,
    @SkySightApiKey private val skySightApiKey: String,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) : ForecastCatalogPort, ForecastTilesPort, ForecastLegendPort, ForecastValuePort {
    private val requestExecutor = SkySightRequestExecutor(
        httpClient = httpClient,
        skySightApiKey = skySightApiKey,
        dispatcher = dispatcher
    )

    override suspend fun getParameters(): List<ForecastParameterMeta> = PARAMETER_META

    override fun getTimeSlots(
        nowUtcMs: Long,
        regionCode: String
    ): List<ForecastTimeSlot> {
        val zoneId = resolveRegionZone(regionCode)
        val localDate = Instant.ofEpochMilli(nowUtcMs).atZone(zoneId).toLocalDate()
        return buildRegionTimeSlotsForDate(
            localDate = localDate,
            zoneId = zoneId
        )
            .sortedBy { slot -> slot.validTimeUtcMs }
    }

    override suspend fun getTileSpec(
        parameterId: ForecastParameterId,
        timeSlot: ForecastTimeSlot,
        regionCode: String
    ): ForecastTileSpec {
        val selectedRegion = normalizeForecastRegionCode(regionCode)
        val target = resolveRegionTarget(selectedRegion)
        val regionDateTime = formatRegionDateTime(
            utcMs = timeSlot.validTimeUtcMs,
            regionCode = selectedRegion
        )
        val tileParameter = resolveTileParameter(parameterId)

        val isWind = isWindParameter(tileParameter)
        val urlTemplate = if (isWind) {
            "https://edge.skysight.io/$target/${regionDateTime.datePart}/${regionDateTime.timePart}/wind/{z}/{x}/{y}/$tileParameter"
        } else {
            "https://edge.skysight.io/$target/${regionDateTime.datePart}/${regionDateTime.timePart}/$tileParameter/{z}/{x}/{y}"
        }

        val primarySourceLayer = resolveSourceLayer(
            tileParameter = tileParameter,
            timePart = regionDateTime.timePart
        )
        val sourceLayerCandidates = resolveSourceLayerCandidates(
            tileParameter = tileParameter,
            timePart = regionDateTime.timePart
        )

        return ForecastTileSpec(
            urlTemplate = urlTemplate,
            minZoom = WIND_MIN_ZOOM,
            maxZoom = if (isWind) WIND_MAX_ZOOM else FILL_MAX_ZOOM,
            tileSizePx = 256,
            attribution = "Map tiles provider",
            format = if (isWind) {
                ForecastTileFormat.VECTOR_WIND_POINTS
            } else {
                ForecastTileFormat.VECTOR_INDEXED_FILL
            },
            sourceLayer = primarySourceLayer,
            sourceLayerCandidates = sourceLayerCandidates,
            valueProperty = "idx",
            speedProperty = if (isWind) "spd" else null,
            directionProperty = if (isWind) "dir" else null
        )
    }

    override suspend fun getLegend(
        parameterId: ForecastParameterId,
        timeSlot: ForecastTimeSlot,
        regionCode: String
    ): ForecastLegendSpec {
        val selectedRegion = normalizeForecastRegionCode(regionCode)
        val target = resolveRegionTarget(selectedRegion)
        val datePart = formatRegionDateTime(
            utcMs = timeSlot.validTimeUtcMs,
            regionCode = selectedRegion
        ).datePart
        val tileParameter = resolveTileParameter(parameterId)
        val url = "https://static2.skysight.io/$target/$datePart/legend/$tileParameter/1800.legend.json"
        val rawBody = executeGet(url = url, requestLabel = "legend request")
        return parseLegendSpec(
            rawBody = rawBody,
            unitLabel = unitLabelFor(parameterId)
        )
    }

    override suspend fun getValue(
        latitude: Double,
        longitude: Double,
        parameterId: ForecastParameterId,
        timeSlot: ForecastTimeSlot,
        regionCode: String
    ): ForecastPointValue = withContext(dispatcher) {
        requireLatitudeLongitude(latitude = latitude, longitude = longitude)
        val selectedRegion = normalizeForecastRegionCode(regionCode)
        val target = resolveRegionTarget(selectedRegion)
        val regionDateTime = formatRegionDateTime(
            utcMs = timeSlot.validTimeUtcMs,
            regionCode = selectedRegion
        )
        val timestamp = regionDateTime.datePart + regionDateTime.timePart
        val payload = JSONObject()
            .put("times", JSONArray().put(timestamp))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val url = "https://cf.skysight.io/point/$latitude/$longitude?region=$target"
        val rawBody = executePost(
            url = url,
            body = payload,
            requestLabel = "point-value request"
        )
        val jsonBody = JSONObject(rawBody)
        val value = parsePointValue(
            jsonBody = jsonBody,
            fieldName = resolvePointField(parameterId)
        )
        val direction = resolvePointDirectionField(parameterId)?.let { fieldName ->
            parseOptionalPointValue(
                jsonBody = jsonBody,
                fieldName = fieldName
            )
        }
        ForecastPointValue(
            value = value,
            unitLabel = unitLabelFor(parameterId),
            validTimeUtcMs = timeSlot.validTimeUtcMs,
            directionFromDeg = direction
        )
    }

    private suspend fun executeGet(url: String, requestLabel: String): String = withContext(dispatcher) {
        requestExecutor.executeGet(url = url, requestLabel = requestLabel)
    }

    private suspend fun executePost(
        url: String,
        body: okhttp3.RequestBody,
        requestLabel: String
    ): String = withContext(dispatcher) {
        requestExecutor.executePost(
            url = url,
            body = body,
            requestLabel = requestLabel
        )
    }

    private fun parseLegendSpec(
        rawBody: String,
        unitLabel: String
    ): ForecastLegendSpec {
        val arrayStart = rawBody.indexOf('[')
        val arrayEnd = rawBody.lastIndexOf(']')
        if (arrayStart < 0 || arrayEnd <= arrayStart) {
            throw IOException("Invalid SkySight legend payload")
        }
        val jsonArray = JSONArray(rawBody.substring(arrayStart, arrayEnd + 1))
        val stops = buildList {
            for (index in 0 until jsonArray.length()) {
                val entry = jsonArray.getJSONObject(index)
                val value = entry.optDouble("value", Double.NaN)
                val rgb = entry.optJSONArray("rgb")
                if (value.isNaN() || rgb == null || rgb.length() < 3) continue
                val red = rgb.optInt(0, 0).coerceIn(0, 255)
                val green = rgb.optInt(1, 0).coerceIn(0, 255)
                val blue = rgb.optInt(2, 0).coerceIn(0, 255)
                val argb = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
                add(
                    ForecastLegendStop(
                        value = value,
                        argb = argb
                    )
                )
            }
        }
        if (stops.isEmpty()) {
            throw IOException("SkySight legend returned no stops")
        }
        return ForecastLegendSpec(
            unitLabel = unitLabel,
            stops = stops
        )
    }

    private fun parsePointValue(jsonBody: JSONObject, fieldName: String): Double {
        val values = jsonBody.optJSONArray(fieldName)
        if (values != null && values.length() > 0) {
            val first = values.optDouble(0, Double.NaN)
            if (!first.isNaN()) return first
        }
        val direct = jsonBody.optDouble(fieldName, Double.NaN)
        if (!direct.isNaN()) return direct
        throw IOException("SkySight point field '$fieldName' missing")
    }

    private fun parseOptionalPointValue(jsonBody: JSONObject, fieldName: String): Double? {
        val values = jsonBody.optJSONArray(fieldName)
        if (values != null && values.length() > 0) {
            val first = values.optDouble(0, Double.NaN)
            if (!first.isNaN()) return first
        }
        val direct = jsonBody.optDouble(fieldName, Double.NaN)
        if (!direct.isNaN()) return direct
        return null
    }

    private fun resolveTileParameter(parameterId: ForecastParameterId): String {
        val normalized = parameterId.value.trim().uppercase(Locale.US)
        return when (normalized) {
            "THERMAL" -> "wstar_bsratio"
            "CLOUDBASE" -> "zsfclcl"
            "RAIN" -> "accrain"
            else -> sanitizeTileParameter(parameterId.value.trim())
        }
    }

    private fun sanitizeTileParameter(rawParameter: String): String {
        if (rawParameter.isEmpty()) return DEFAULT_FORECAST_PARAMETER_ID.value
        if (!SAFE_PARAMETER_PATTERN.matches(rawParameter)) {
            return DEFAULT_FORECAST_PARAMETER_ID.value
        }
        return rawParameter
    }

    private fun resolveSourceLayer(tileParameter: String, timePart: String): String =
        when (tileParameter.uppercase(Locale.US)) {
            "WSTAR_BSRATIO" -> "bsratio"
            "SFCWIND0", "BLTOPWIND" -> tileParameter.lowercase(Locale.US)
            else -> timePart
        }

    private fun resolveSourceLayerCandidates(
        tileParameter: String,
        timePart: String
    ): List<String> {
        return when (tileParameter.uppercase(Locale.US)) {
            "WSTAR_BSRATIO" -> listOf("bsratio", timePart)
            "SFCWIND0", "BLTOPWIND" -> listOf(tileParameter.lowercase(Locale.US))
            else -> listOf(timePart)
        }
    }

    private fun isWindParameter(tileParameter: String): Boolean =
        when (tileParameter.uppercase(Locale.US)) {
            "SFCWIND0", "BLTOPWIND" -> true
            else -> false
        }

    private fun resolvePointField(parameterId: ForecastParameterId): String {
        return when (resolveTileParameter(parameterId).uppercase(Locale.US)) {
            "WSTAR_BSRATIO" -> "wstar"
            "SFCWIND0" -> "sfcwindspd"
            "BLTOPWIND" -> "bltopwindspd"
            else -> resolveTileParameter(parameterId)
        }
    }

    private fun resolvePointDirectionField(parameterId: ForecastParameterId): String? {
        return when (resolveTileParameter(parameterId).uppercase(Locale.US)) {
            "SFCWIND0" -> "sfcwinddir"
            "BLTOPWIND" -> "bltopwinddir"
            else -> null
        }
    }

    private fun unitLabelFor(parameterId: ForecastParameterId): String {
        val resolvedId = resolveTileParameter(parameterId).uppercase(Locale.US)
        return PARAMETER_META
            .firstOrNull { it.id.value.uppercase(Locale.US) == resolvedId }
            ?.unitLabel
            ?: "value"
    }

    private fun formatRegionDateTime(
        utcMs: Long,
        regionCode: String
    ): RegionDateTime {
        val zoneId = resolveRegionZone(regionCode)
        val localDateTime = Instant.ofEpochMilli(utcMs).atZone(zoneId)
        return RegionDateTime(
            datePart = localDateTime.format(DATE_FORMATTER),
            timePart = localDateTime.format(TIME_FORMATTER)
        )
    }

    private fun buildRegionTimeSlotsForDate(
        localDate: LocalDate,
        zoneId: ZoneId
    ): List<ForecastTimeSlot> {
        val slotStart = ZonedDateTime.of(
            localDate,
            LocalTime.of(SLOT_START_HOUR, 0),
            zoneId
        )
        val slots = ArrayList<ForecastTimeSlot>(SLOT_COUNT_PER_DAY)
        for (index in 0 until SLOT_COUNT_PER_DAY) {
            val utcMs = slotStart
                .plusMinutes((index * SLOT_STEP_MINUTES).toLong())
                .toInstant()
                .toEpochMilli()
            slots.add(ForecastTimeSlot(validTimeUtcMs = utcMs))
        }
        return slots
    }

    private fun requireLatitudeLongitude(latitude: Double, longitude: Double) {
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "SkySight latitude is out of range"
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "SkySight longitude is out of range"
        }
    }

    private fun resolveRegionTarget(regionCode: String): String =
        normalizeForecastRegionCode(regionCode)

    private fun resolveRegionZone(regionCode: String): ZoneId =
        forecastRegionZoneId(regionCode)

    private data class RegionDateTime(
        val datePart: String,
        val timePart: String
    )

    private companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val SAFE_PARAMETER_PATTERN = Regex("^[A-Za-z0-9_]+$")
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmm")
        private const val SLOT_START_HOUR = 6
        private const val SLOT_STEP_MINUTES = 30
        private const val SLOT_COUNT_PER_DAY = 29
        private const val WIND_MIN_ZOOM = 3
        private const val WIND_MAX_ZOOM = 16
        private const val FILL_MAX_ZOOM = 5

        private val PARAMETER_META: List<ForecastParameterMeta> = listOf(
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
            ),
            ForecastParameterMeta(
                id = ForecastParameterId("wblmaxmin"),
                name = "Convergence",
                category = "Lift",
                unitLabel = "m/s",
                supportsPointValue = false
            ),
            ForecastParameterMeta(
                id = ForecastParameterId("sfcwind0"),
                name = "Surface Wind",
                category = "Wind",
                unitLabel = "kt"
            ),
            ForecastParameterMeta(
                id = ForecastParameterId("bltopwind"),
                name = "BL Top Wind",
                category = "Wind",
                unitLabel = "kt"
            ),
            ForecastParameterMeta(
                id = ForecastParameterId("zsfclcl"),
                name = "Cloudbase",
                category = "Cloud",
                unitLabel = "m"
            ),
            ForecastParameterMeta(
                id = ForecastParameterId("zsfclcldif"),
                name = "Cloudbase Spread",
                category = "Cloud",
                unitLabel = "m"
            ),
            ForecastParameterMeta(
                id = ForecastParameterId("accrain"),
                name = "Rain",
                category = "Precip",
                unitLabel = "mm"
            ),
            ForecastParameterMeta(
                id = ForecastParameterId("CFRACL"),
                name = "Cloud Low",
                category = "Cloud",
                unitLabel = "%"
            ),
            ForecastParameterMeta(
                id = ForecastParameterId("CFRACM"),
                name = "Cloud Mid",
                category = "Cloud",
                unitLabel = "%"
            ),
            ForecastParameterMeta(
                id = ForecastParameterId("CFRACH"),
                name = "Cloud High",
                category = "Cloud",
                unitLabel = "%"
            ),
            ForecastParameterMeta(
                id = ForecastParameterId("potfd"),
                name = "Potential Distance",
                category = "XC",
                unitLabel = "km"
            )
        )
    }
}
