package com.trust3.xcpro.forecast

import com.trust3.xcpro.common.di.IoDispatcher
import com.trust3.xcpro.di.ForecastHttpClient
import com.trust3.xcpro.di.SkySightApiKey
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@Singleton
class ForecastAuthRepository @Inject constructor(
    private val credentialsRepository: ForecastCredentialsRepository,
    @ForecastHttpClient private val httpClient: OkHttpClient,
    @SkySightApiKey private val skySightApiKey: String,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {
    suspend fun verifySavedCredentials(): ForecastAuthCheckResult = withContext(dispatcher) {
        val credentials = credentialsRepository.loadCredentials()
            ?: return@withContext ForecastAuthCheckResult.MissingCredentials

        val apiKey = skySightApiKey.trim()
        if (apiKey.isBlank()) {
            return@withContext ForecastAuthCheckResult.MissingApiKey
        }

        val requestBody = JSONObject()
            .put("username", credentials.username)
            .put("password", credentials.password)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val authUrl = SkySightHttpContract.validatedHttpUrl(AUTH_ENDPOINT)
        val requestBuilder = Request.Builder()
            .url(authUrl)
            .post(requestBody)
        SkySightHttpContract.applyStandardHeaders(
            builder = requestBuilder,
            host = authUrl.host,
            apiKey = apiKey
        )
        val request = requestBuilder.build()

        var attempt = 1
        while (attempt <= MAX_AUTH_ATTEMPTS) {
            try {
                var retryDelayMs: Long? = null
                var terminalResult: ForecastAuthCheckResult? = null
                httpClient.newCall(request).awaitResponse().use { response ->
                    val code = response.code
                    val responseBody = response.body?.string().orEmpty()
                    val message = resolveAuthMessage(
                        responseMessage = response.message,
                        responseBody = responseBody,
                        httpCode = code
                    )
                    if (response.isSuccessful) {
                        return@withContext ForecastAuthCheckResult.Success(
                            code = code,
                            message = message
                        )
                    }
                    if (code == 401 || code == 403) {
                        return@withContext ForecastAuthCheckResult.InvalidCredentials(
                            code = code,
                            message = message
                        )
                    }
                    if (code == 429) {
                        val retryAfterSec = SkySightHttpContract.retryAfterSeconds(
                            response.header(RETRY_AFTER_HEADER)
                        )
                        if (attempt < MAX_AUTH_ATTEMPTS) {
                            retryDelayMs = computeRetryDelayMs(
                                attempt = attempt,
                                retryAfterSec = retryAfterSec
                            )
                            return@use
                        }
                        terminalResult = ForecastAuthCheckResult.RateLimited(
                            code = code,
                            message = message,
                            retryAfterSec = retryAfterSec
                        )
                        return@use
                    }
                    if (code in 500..599) {
                        if (attempt < MAX_AUTH_ATTEMPTS) {
                            retryDelayMs = computeRetryDelayMs(
                                attempt = attempt,
                                retryAfterSec = null
                            )
                            return@use
                        }
                        terminalResult = ForecastAuthCheckResult.ServerError(
                            code = code,
                            message = message,
                            retryable = true
                        )
                        return@use
                    }
                    terminalResult = ForecastAuthCheckResult.HttpError(
                        code = code,
                        message = message
                    )
                }
                if (terminalResult != null) {
                    return@withContext terminalResult as ForecastAuthCheckResult
                }
                if (retryDelayMs != null) {
                    delay(retryDelayMs as Long)
                    attempt += 1
                    continue
                }
                return@withContext ForecastAuthCheckResult.NetworkError(
                    kind = ForecastAuthNetworkFailureKind.UNKNOWN,
                    message = "NetworkError",
                    retryable = false
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                val kind = e.toNetworkFailureKind()
                val retryable = kind.isRetryable()
                if (retryable && attempt < MAX_AUTH_ATTEMPTS) {
                    delay(
                        computeRetryDelayMs(
                            attempt = attempt,
                            retryAfterSec = null
                        )
                    )
                    attempt += 1
                    continue
                }
                return@withContext ForecastAuthCheckResult.NetworkError(
                    kind = kind,
                    message = e::class.java.simpleName.ifBlank { "NetworkError" },
                    retryable = retryable
                )
            }
        }

        return@withContext ForecastAuthCheckResult.NetworkError(
            kind = ForecastAuthNetworkFailureKind.UNKNOWN,
            message = "NetworkError",
            retryable = false
        )
    }

    private fun resolveAuthMessage(
        responseMessage: String,
        responseBody: String,
        httpCode: Int
    ): String {
        val parsedMessage = parseMessageFromJson(responseBody)
        if (!parsedMessage.isNullOrBlank()) return parsedMessage
        if (responseMessage.isNotBlank()) return responseMessage
        return "HTTP $httpCode"
    }

    private fun parseMessageFromJson(responseBody: String): String? {
        if (responseBody.isBlank()) return null
        return runCatching {
            val json = JSONObject(responseBody)
            listOf("message", "error", "detail")
                .firstNotNullOfOrNull { field ->
                    json.optString(field).trim().takeIf { it.isNotBlank() }
                }
        }.getOrNull()
    }

    private fun IOException.toNetworkFailureKind(): ForecastAuthNetworkFailureKind = when (this) {
        is UnknownHostException -> ForecastAuthNetworkFailureKind.DNS
        is SocketTimeoutException,
        is InterruptedIOException -> ForecastAuthNetworkFailureKind.TIMEOUT
        is NoRouteToHostException -> ForecastAuthNetworkFailureKind.NO_ROUTE
        is ConnectException -> ForecastAuthNetworkFailureKind.CONNECT
        is SSLException -> ForecastAuthNetworkFailureKind.TLS
        else -> ForecastAuthNetworkFailureKind.UNKNOWN
    }

    private fun ForecastAuthNetworkFailureKind.isRetryable(): Boolean = when (this) {
        ForecastAuthNetworkFailureKind.TIMEOUT,
        ForecastAuthNetworkFailureKind.CONNECT,
        ForecastAuthNetworkFailureKind.NO_ROUTE,
        ForecastAuthNetworkFailureKind.UNKNOWN -> true
        ForecastAuthNetworkFailureKind.DNS,
        ForecastAuthNetworkFailureKind.TLS -> false
    }

    private fun computeRetryDelayMs(attempt: Int, retryAfterSec: Int?): Long {
        val retryAfterDelayMs = retryAfterSec?.coerceAtLeast(1)?.times(1_000L) ?: 0L
        val exponentialBackoffMs = NETWORK_RETRY_BACKOFF_BASE_MS * (1L shl (attempt - 1))
        return maxOf(retryAfterDelayMs, exponentialBackoffMs)
    }

    private companion object {
        private const val AUTH_ENDPOINT = "https://skysight.io/api/auth"
        private const val RETRY_AFTER_HEADER = "Retry-After"
        private const val MAX_AUTH_ATTEMPTS = 3
        private const val NETWORK_RETRY_BACKOFF_BASE_MS = 350L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
