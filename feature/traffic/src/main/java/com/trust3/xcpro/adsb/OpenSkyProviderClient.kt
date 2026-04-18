package com.trust3.xcpro.adsb

import com.trust3.xcpro.common.di.IoDispatcher
import java.io.InterruptedIOException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class OpenSkyProviderClient @Inject constructor(
    private val httpClient: OkHttpClient,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) : AdsbProviderClient {

    override suspend fun fetchStates(
        bbox: BBox,
        auth: AdsbAuth?
    ): ProviderResult = withContext(dispatcher) {
        val request = Request.Builder()
            .url(
                BASE_URL.toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("lamin", bbox.lamin.formatCoord())
                    .addQueryParameter("lomin", bbox.lomin.formatCoord())
                    .addQueryParameter("lamax", bbox.lamax.formatCoord())
                    .addQueryParameter("lomax", bbox.lomax.formatCoord())
                    .addQueryParameter("extended", "1")
                    .build()
            )
            .get()
            .apply {
                header("Accept", "application/json")
                val token = auth?.bearerToken?.takeIf { it.isNotBlank() }
                if (token != null) {
                    header("Authorization", "Bearer $token")
                }
            }
            .build()

        try {
            httpClient.newCall(request).awaitResponse().use { response ->
                val code = response.code
                val remainingCredits = response.header(HEADER_REMAINING)?.parseIntHeader()
                return@withContext when (code) {
                    200 -> {
                        val body = response.body?.string().orEmpty()
                        val parsed = runCatching { OpenSkyStateVectorMapper.parseResponse(body) }
                            .getOrElse {
                                return@withContext ProviderResult.NetworkError(
                                    kind = AdsbNetworkFailureKind.MALFORMED_RESPONSE,
                                    message = "Malformed OpenSky response"
                                )
                            }
                        ProviderResult.Success(
                            response = parsed,
                            httpCode = code,
                            remainingCredits = remainingCredits
                        )
                    }

                    429 -> {
                        val retryAfter = response.header(HEADER_RETRY_AFTER)?.parseIntHeader()
                            ?.coerceAtLeast(1)
                            ?: DEFAULT_RETRY_AFTER_SEC
                        ProviderResult.RateLimited(
                            retryAfterSec = retryAfter,
                            remainingCredits = remainingCredits
                        )
                    }

                    else -> {
                        val message = response.message.takeIf { it.isNotBlank() } ?: "HTTP $code"
                        ProviderResult.HttpError(code = code, message = message)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            ProviderResult.NetworkError(
                kind = e.toNetworkFailureKind(),
                message = e::class.java.simpleName.ifBlank { "NetworkError" }
            )
        }
    }

    private fun String.parseIntHeader(): Int? {
        return trim()
            .substringBefore(".")
            .toIntOrNull()
    }

    private fun Double.formatCoord(): String = String.format(Locale.US, "%.6f", this)

    private fun IOException.toNetworkFailureKind(): AdsbNetworkFailureKind = when (this) {
        is UnknownHostException -> AdsbNetworkFailureKind.DNS
        is SocketTimeoutException,
        is InterruptedIOException -> AdsbNetworkFailureKind.TIMEOUT
        is NoRouteToHostException -> AdsbNetworkFailureKind.NO_ROUTE
        is ConnectException -> AdsbNetworkFailureKind.CONNECT
        is SSLException -> AdsbNetworkFailureKind.TLS
        else -> AdsbNetworkFailureKind.UNKNOWN
    }

    private companion object {
        private const val BASE_URL = "https://opensky-network.org/api/states/all"
        private const val HEADER_REMAINING = "X-Rate-Limit-Remaining"
        private const val HEADER_RETRY_AFTER = "X-Rate-Limit-Retry-After-Seconds"
        private const val DEFAULT_RETRY_AFTER_SEC = 60
    }
}
