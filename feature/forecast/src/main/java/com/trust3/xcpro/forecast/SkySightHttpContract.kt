package com.trust3.xcpro.forecast

import java.io.IOException
import java.util.Locale
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

object SkySightHttpContract {
    private val allowlistedHosts: Set<String> = setOf(
        "skysight.io",
        "edge.skysight.io",
        "static2.skysight.io",
        "cf.skysight.io",
        "satellite.skysight.io",
        "apig2.skysight.io"
    )

    private val originHosts: Set<String> = setOf(
        "skysight.io",
        "edge.skysight.io",
        "cf.skysight.io",
        "satellite.skysight.io",
        "apig2.skysight.io"
    )
    private val mapLibreOriginHosts: Set<String> = setOf(
        "edge.skysight.io",
        "satellite.skysight.io",
        "apig2.skysight.io"
    )
    private val apiKeyRequiredHosts: Set<String> = setOf(
        "skysight.io",
        "edge.skysight.io",
        "cf.skysight.io",
        "apig2.skysight.io"
    )

    private const val REQUIRED_ORIGIN = "https://xalps.skysight.io"
    private const val ACCEPT_VALUE = "application/json, text/plain, */*"

    fun validatedHttpUrl(rawUrl: String): HttpUrl {
        val parsedUrl = rawUrl.toHttpUrlOrNull()
            ?: throw IOException("Invalid SkySight URL")
        if (!parsedUrl.isHttps) {
            throw IOException("SkySight URL must use HTTPS")
        }
        val normalizedHost = normalizedHost(parsedUrl.host)
        if (normalizedHost !in allowlistedHosts) {
            throw IOException("SkySight host '$normalizedHost' is not allowlisted")
        }
        return parsedUrl
    }

    fun applyStandardHeaders(
        builder: Request.Builder,
        host: String,
        apiKey: String?
    ): Request.Builder {
        builder.header("Accept", ACCEPT_VALUE)
        if (requiresOriginHeader(host)) {
            builder.header("Origin", REQUIRED_ORIGIN)
        }
        val trimmedApiKey = apiKey?.trim().orEmpty()
        if (trimmedApiKey.isNotEmpty()) {
            builder.header("X-API-KEY", trimmedApiKey)
        }
        return builder
    }

    fun retryAfterSeconds(headerValue: String?): Int? {
        return headerValue
            ?.trim()
            ?.substringBefore(".")
            ?.toIntOrNull()
            ?.coerceAtLeast(1)
    }

    fun isAllowlistedHost(host: String): Boolean = normalizedHost(host) in allowlistedHosts

    fun requiresOriginHeader(host: String): Boolean = normalizedHost(host) in originHosts

    fun isMapLibreOriginHost(host: String): Boolean = normalizedHost(host) in mapLibreOriginHosts

    fun requiresApiKey(host: String): Boolean = normalizedHost(host) in apiKeyRequiredHosts

    fun requiredOriginHeaderValue(): String = REQUIRED_ORIGIN

    fun isRetryableStatusCode(code: Int): Boolean = code == 429 || code in 500..599

    private fun normalizedHost(host: String): String = host.lowercase(Locale.US)
}
