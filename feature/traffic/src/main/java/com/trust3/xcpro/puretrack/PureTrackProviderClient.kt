package com.trust3.xcpro.puretrack

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

interface PureTrackProviderClient {
    suspend fun login(email: String, password: String): PureTrackProviderResult<PureTrackLoginSession>

    suspend fun fetchTraffic(
        request: PureTrackTrafficRequest,
        bearerToken: String
    ): PureTrackProviderResult<PureTrackTrafficResponse>
}

class OkHttpPureTrackProviderClient(
    private val appKeyProvider: PureTrackAppKeyProvider,
    private val httpClient: OkHttpClient,
    private val dispatcher: CoroutineDispatcher,
    private val rowParser: PureTrackRowParser = PureTrackRowParser()
) : PureTrackProviderClient {

    override suspend fun login(
        email: String,
        password: String
    ): PureTrackProviderResult<PureTrackLoginSession> = withContext(dispatcher) {
        val appKey = appKeyProvider.loadAppKey()?.trim()?.takeIf { it.isNotBlank() }
            ?: return@withContext PureTrackProviderResult.MissingAppKey
        val formBody = FormBody.Builder()
            .add("key", appKey)
            .add("email", email)
            .add("password", password)
            .build()
        val request = Request.Builder()
            .url(LOGIN_ENDPOINT)
            .post(formBody)
            .header("Accept", "application/json")
            .build()

        execute(request) { response ->
            response.toResult { body ->
                parseLoginResponse(body)
            }
        }
    }

    override suspend fun fetchTraffic(
        request: PureTrackTrafficRequest,
        bearerToken: String
    ): PureTrackProviderResult<PureTrackTrafficResponse> = withContext(dispatcher) {
        val appKey = appKeyProvider.loadAppKey()?.trim()?.takeIf { it.isNotBlank() }
            ?: return@withContext PureTrackProviderResult.MissingAppKey
        val token = bearerToken.trim().takeIf { it.isNotBlank() }
            ?: return@withContext PureTrackProviderResult.MissingBearerToken
        val formBody = request.toFormBody(appKey)
        val httpRequest = Request.Builder()
            .url(TRAFFIC_ENDPOINT)
            .post(formBody)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .build()

        execute(httpRequest) { response ->
            response.toResult { body ->
                parseTrafficResponse(body)
            }
        }
    }

    private suspend fun <T> execute(
        request: Request,
        parseResponse: (Response) -> PureTrackProviderResult<T>
    ): PureTrackProviderResult<T> {
        return try {
            httpClient.newCall(request).awaitPureTrackResponse().use { response ->
                parseResponse(response)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            PureTrackProviderResult.NetworkError(
                kind = e.toNetworkFailureKind(),
                message = e::class.java.simpleName.ifBlank { "NetworkError" }
            )
        }
    }

    private fun <T> Response.toResult(parseBody: (String) -> T): PureTrackProviderResult<T> {
        return when (code) {
            200 -> {
                val bodyString = body?.string().orEmpty()
                runCatching {
                    PureTrackProviderResult.Success(
                        value = parseBody(bodyString),
                        httpCode = code
                    )
                }.getOrElse {
                    PureTrackProviderResult.NetworkError(
                        kind = PureTrackNetworkFailureKind.MALFORMED_RESPONSE,
                        message = "Malformed PureTrack response"
                    )
                }
            }

            429 -> PureTrackProviderResult.RateLimited(
                retryAfterSec = header(HEADER_RETRY_AFTER)?.parseIntHeader()
            )

            else -> PureTrackProviderResult.HttpError(
                code = code,
                message = message.takeIf { it.isNotBlank() } ?: "HTTP $code"
            )
        }
    }

    private fun parseLoginResponse(body: String): PureTrackLoginSession {
        val root = JsonParser.parseString(body).asJsonObject
        val token = root.getPrimitiveString("access_token")?.trim().orEmpty()
        val pro = root.get("pro")
            ?.takeIf { it.isJsonPrimitive }
            ?.asBoolean
            ?: throw IllegalArgumentException("Missing pro flag")
        require(token.isNotBlank()) { "Missing access token" }
        return PureTrackLoginSession(
            accessToken = token,
            pro = pro
        )
    }

    private fun parseTrafficResponse(body: String): PureTrackTrafficResponse {
        val rows = JsonParser.parseString(body).trafficRows()
        val parsedRows = rows.map { rowParser.parse(it) }
        return PureTrackTrafficResponse(
            targets = parsedRows.mapNotNull { it.target },
            diagnostics = PureTrackTrafficDiagnostics(
                parsedRows = parsedRows.count { it.parsed },
                droppedRows = parsedRows.count { !it.parsed },
                duplicateTokenCount = parsedRows.sumOf { it.duplicateTokenCount }
            )
        )
    }

    private fun JsonElement.trafficRows(): List<String> {
        val array = when {
            isJsonArray -> asJsonArray
            isJsonObject -> asJsonObject.get("data")?.asJsonArray
            else -> null
        } ?: throw IllegalArgumentException("Missing traffic data")
        return array.toStringList()
    }

    private fun JsonArray.toStringList(): List<String> =
        map { element ->
            require(element.isJsonPrimitive) { "Non-string traffic row" }
            element.asString
        }

    private fun PureTrackTrafficRequest.toFormBody(appKey: String): FormBody {
        val body = FormBody.Builder()
            .add("key", appKey)
            .add("lat1", bounds.topRightLatitude.formatCoord())
            .add("long1", bounds.topRightLongitude.formatCoord())
            .add("lat2", bounds.bottomLeftLatitude.formatCoord())
            .add("long2", bounds.bottomLeftLongitude.formatCoord())
            .add("t", maxAgeMinutes.toString())
        category?.let { body.add("cat", it.apiValue) }
        objectTypeIds.joinToComma()?.let { body.add("o", it) }
        alwaysIncludeKeys.map { it.trim() }
            .filter { it.isNotBlank() }
            .sorted()
            .joinToString(",")
            .takeIf { it.isNotBlank() }
            ?.let { body.add("s", it) }
        if (isolateAlwaysIncludeKeys) {
            body.add("i", "1")
        }
        return body.build()
    }

    private fun Set<Int>.joinToComma(): String? =
        sorted()
            .joinToString(",")
            .takeIf { it.isNotBlank() }

    private fun com.google.gson.JsonObject.getPrimitiveString(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString

    private fun String.parseIntHeader(): Int? {
        return trim()
            .substringBefore(".")
            .toIntOrNull()
    }

    private fun Double.formatCoord(): String = String.format(Locale.US, "%.6f", this)

    private fun IOException.toNetworkFailureKind(): PureTrackNetworkFailureKind = when (this) {
        is UnknownHostException -> PureTrackNetworkFailureKind.DNS
        is SocketTimeoutException,
        is InterruptedIOException -> PureTrackNetworkFailureKind.TIMEOUT
        is NoRouteToHostException -> PureTrackNetworkFailureKind.NO_ROUTE
        is ConnectException -> PureTrackNetworkFailureKind.CONNECT
        is SSLException -> PureTrackNetworkFailureKind.TLS
        else -> PureTrackNetworkFailureKind.UNKNOWN
    }

    private companion object {
        private const val LOGIN_ENDPOINT = "https://puretrack.io/api/login"
        private const val TRAFFIC_ENDPOINT = "https://puretrack.io/api/traffic"
        private const val HEADER_RETRY_AFTER = "Retry-After"
    }
}
