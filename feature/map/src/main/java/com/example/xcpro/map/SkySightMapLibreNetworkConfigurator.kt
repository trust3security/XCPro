package com.example.xcpro.map

import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.OkHttpClient
import okhttp3.Request
import org.maplibre.android.module.http.HttpRequestUtil

object SkySightMapLibreNetworkConfigurator {
    private val isConfigured = AtomicBoolean(false)
    private const val REQUIRED_ORIGIN = "https://xalps.skysight.io"

    private val skySightHosts: Set<String> = setOf(
        "edge.skysight.io",
        "apig2.skysight.io",
        "satellite.skysight.io"
    )

    fun ensureConfigured() {
        if (!isConfigured.compareAndSet(false, true)) return

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val requestWithHeaders = applySkySightHeaders(request)
                chain.proceed(requestWithHeaders)
            }
            .build()

        HttpRequestUtil.setOkHttpClient(client)
    }

    internal fun applySkySightHeaders(request: Request): Request {
        val host = request.url.host.lowercase(Locale.US)
        return if (isSkySightHost(host)) {
            request.newBuilder()
                .header("Origin", REQUIRED_ORIGIN)
                .build()
        } else {
            request
        }
    }

    internal fun isSkySightHost(host: String): Boolean {
        val normalized = host.lowercase(Locale.US)
        return normalized in skySightHosts
    }
}

