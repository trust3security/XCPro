package com.example.xcpro.map

import com.example.xcpro.forecast.SkySightHttpContract
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.OkHttpClient
import okhttp3.Request
import org.maplibre.android.module.http.HttpRequestUtil

object SkySightMapLibreNetworkConfigurator {
    private val isConfigured = AtomicBoolean(false)

    fun ensureConfigured() {
        if (!isConfigured.compareAndSet(false, true)) return

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val requestWithHeaders = applySkySightHeaders(request)
                chain.proceed(requestWithHeaders)
            }
            .build()

        HttpRequestUtil.setOkHttpClient(client)
    }

    internal fun applySkySightHeaders(request: Request): Request {
        val host = request.url.host
        return if (isSkySightHost(host)) {
            request.newBuilder()
                .header("Origin", SkySightHttpContract.requiredOriginHeaderValue())
                .build()
        } else {
            request
        }
    }

    internal fun isSkySightHost(host: String): Boolean {
        return SkySightHttpContract.isMapLibreOriginHost(host)
    }
}

