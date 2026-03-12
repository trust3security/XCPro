package com.example.xcpro.forecast

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

internal class SkySightRequestExecutor(
    private val httpClient: OkHttpClient,
    private val skySightApiKey: String,
    private val dispatcher: CoroutineDispatcher
) {
    suspend fun executeGet(
        url: String,
        requestLabel: String = "GET request"
    ): String = withContext(dispatcher) {
        val request = buildSkySightRequest(
            rawUrl = url,
            method = HttpMethod.GET,
            body = null
        )
        executeRequestWithRetry(
            request = request,
            operationLabel = requestLabel
        )
    }

    suspend fun executePost(
        url: String,
        body: RequestBody,
        requestLabel: String = "POST request"
    ): String = withContext(dispatcher) {
        val request = buildSkySightRequest(
            rawUrl = url,
            method = HttpMethod.POST,
            body = body
        )
        executeRequestWithRetry(
            request = request,
            operationLabel = requestLabel
        )
    }

    private suspend fun executeRequestWithRetry(
        request: Request,
        operationLabel: String
    ): String {
        var lastFailure: IOException? = null
        var attempt = 1
        while (attempt <= MAX_NETWORK_ATTEMPTS) {
            try {
                httpClient.newCall(request).awaitResponse().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        return responseBody
                    }
                    val message = resolveHttpFailureMessage(
                        responseCode = response.code,
                        responseMessage = response.message,
                        responseBody = responseBody
                    )
                    val retryAfterSec = SkySightHttpContract.retryAfterSeconds(
                        response.header(RETRY_AFTER_HEADER)
                    )
                    val failure = SkySightRequestException(
                        code = response.code,
                        retryable = SkySightHttpContract.isRetryableStatusCode(response.code),
                        retryAfterSec = retryAfterSec,
                        message = "SkySight $operationLabel failed: $message"
                    )
                    if (!failure.retryable || attempt >= MAX_NETWORK_ATTEMPTS) {
                        throw failure
                    }
                    delay(computeRetryDelayMs(attempt = attempt, retryAfterSec = failure.retryAfterSec))
                }
            } catch (e: IOException) {
                lastFailure = e
                if (!e.isRetryableIoFailure() || attempt >= MAX_NETWORK_ATTEMPTS) {
                    throw e
                }
                delay(computeRetryDelayMs(attempt = attempt, retryAfterSec = null))
            }
            attempt += 1
        }
        throw lastFailure ?: IOException("SkySight $operationLabel failed")
    }

    private fun buildSkySightRequest(
        rawUrl: String,
        method: HttpMethod,
        body: RequestBody?
    ): Request {
        val httpUrl = SkySightHttpContract.validatedHttpUrl(rawUrl)
        val trimmedApiKey = skySightApiKey.trim()
        if (trimmedApiKey.isBlank() && SkySightHttpContract.requiresApiKey(httpUrl.host)) {
            throw IOException("SkySight API key is missing")
        }
        val builder = Request.Builder()
            .url(httpUrl)
        when (method) {
            HttpMethod.GET -> builder.get()
            HttpMethod.POST -> builder.post(requireNotNull(body))
        }
        SkySightHttpContract.applyStandardHeaders(
            builder = builder,
            host = httpUrl.host,
            apiKey = trimmedApiKey
        )
        return builder.build()
    }

    private fun resolveHttpFailureMessage(
        responseCode: Int,
        responseMessage: String,
        responseBody: String
    ): String {
        val parsedBodyMessage = parseMessageFromJson(responseBody)
        if (!parsedBodyMessage.isNullOrBlank()) {
            return "HTTP $responseCode ($parsedBodyMessage)"
        }
        if (responseMessage.isNotBlank()) {
            return "HTTP $responseCode ($responseMessage)"
        }
        return "HTTP $responseCode"
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

    private fun computeRetryDelayMs(attempt: Int, retryAfterSec: Int?): Long {
        val retryAfterDelayMs = retryAfterSec?.coerceAtLeast(1)?.times(1_000L) ?: 0L
        val exponentialBackoffMs = NETWORK_RETRY_BACKOFF_BASE_MS * (1L shl (attempt - 1))
        return maxOf(retryAfterDelayMs, exponentialBackoffMs)
    }

    private fun IOException.isRetryableIoFailure(): Boolean {
        if (this is SkySightRequestException) return retryable
        return when (this) {
            is SocketTimeoutException,
            is InterruptedIOException,
            is ConnectException,
            is NoRouteToHostException -> true
            is UnknownHostException,
            is SSLException -> false
            else -> true
        }
    }

    private enum class HttpMethod {
        GET,
        POST
    }

    private class SkySightRequestException(
        val code: Int,
        val retryable: Boolean,
        val retryAfterSec: Int?,
        message: String
    ) : IOException(message)

    private companion object {
        private const val RETRY_AFTER_HEADER = "Retry-After"
        private const val MAX_NETWORK_ATTEMPTS = 3
        private const val NETWORK_RETRY_BACKOFF_BASE_MS = 350L
    }
}
