package com.trust3.xcpro.adsb.metadata.data

import com.trust3.xcpro.adsb.awaitResponse
import com.trust3.xcpro.common.di.IoDispatcher
import com.trust3.xcpro.di.AdsbMetadataHttpClient
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@Singleton
class OpenSkyIcaoMetadataClient @Inject constructor(
    @AdsbMetadataHttpClient private val httpClient: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    suspend fun fetchByIcao24(icao24: String): Result<AircraftMetadataEntity?> = withContext(ioDispatcher) {
        try {
            val request = Request.Builder()
                .url("$METADATA_BY_ICAO_URL_BASE/$icao24")
                .header("Accept", "application/json")
                .get()
                .build()

            val metadata = httpClient.newCall(request).awaitResponse().use { response ->
                if (response.code == 404) {
                    return@use null
                }
                if (!response.isSuccessful) {
                    throw IOException("ICAO metadata lookup failed HTTP ${response.code} for $icao24")
                }
                val body = response.body?.string()?.trim().orEmpty()
                if (body.isBlank()) {
                    return@use null
                }
                parseResponse(body)
            }
            Result.success(metadata)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseResponse(body: String): AircraftMetadataEntity? {
        val json = JSONObject(body)
        val icao24 = normalizeIcao24(readNullableString(json, "icao24")) ?: return null
        val registration = normalizeText(readNullableString(json, "registration"))
        val typecode = normalizeText(readNullableString(json, "typecode"))
        val model = normalizeText(readNullableString(json, "model"))
        val manufacturerName = normalizeText(readNullableString(json, "manufacturerName"))
        val owner = normalizeText(readNullableString(json, "owner"))
        val operator = normalizeText(readNullableString(json, "operator"))
        val operatorCallsign = normalizeText(readNullableString(json, "operatorCallsign"))
        val icaoAircraftType = normalizeText(readNullableString(json, "icaoAircraftType"))
            ?: normalizeText(readNullableString(json, "icaoAircraftClass"))
        val qualityScore = qualityScore(registration, typecode, model)

        return AircraftMetadataEntity(
            icao24 = icao24,
            registration = registration,
            typecode = typecode,
            model = model,
            manufacturerName = manufacturerName,
            owner = owner,
            operator = operator,
            operatorCallsign = operatorCallsign,
            icaoAircraftType = icaoAircraftType,
            qualityScore = qualityScore,
            sourceRowOrder = ON_DEMAND_SOURCE_ROW_ORDER
        )
    }

    private fun normalizeIcao24(raw: String?): String? {
        return raw
            ?.trim()
            ?.lowercase()
            ?.takeIf { ICAO24_REGEX.matches(it) }
    }

    private fun normalizeText(raw: String?): String? {
        return raw?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun readNullableString(json: JSONObject, key: String): String? {
        if (json.isNull(key) || !json.has(key)) return null
        return json.getString(key)
    }

    private fun qualityScore(
        registration: String?,
        typecode: String?,
        model: String?
    ): Int {
        var score = 0
        if (!registration.isNullOrBlank()) score += 1
        if (!typecode.isNullOrBlank()) score += 1
        if (!model.isNullOrBlank()) score += 1
        return score
    }

    private companion object {
        const val METADATA_BY_ICAO_URL_BASE =
            "https://opensky-network.org/api/metadata/aircraft/icao"
        const val ON_DEMAND_SOURCE_ROW_ORDER = Long.MAX_VALUE
        val ICAO24_REGEX = Regex("[0-9a-f]{6}")
    }
}
