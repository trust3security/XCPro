package com.example.xcpro.forecast

enum class ForecastAuthNetworkFailureKind {
    DNS,
    TIMEOUT,
    CONNECT,
    NO_ROUTE,
    TLS,
    UNKNOWN
}

sealed interface ForecastAuthCheckResult {
    data class Success(
        val code: Int,
        val message: String
    ) : ForecastAuthCheckResult

    data class InvalidCredentials(
        val code: Int,
        val message: String
    ) : ForecastAuthCheckResult

    data class RateLimited(
        val code: Int,
        val message: String,
        val retryAfterSec: Int?
    ) : ForecastAuthCheckResult

    data class ServerError(
        val code: Int,
        val message: String,
        val retryable: Boolean = true
    ) : ForecastAuthCheckResult

    data class HttpError(
        val code: Int,
        val message: String
    ) : ForecastAuthCheckResult

    data class NetworkError(
        val kind: ForecastAuthNetworkFailureKind,
        val message: String,
        val retryable: Boolean
    ) : ForecastAuthCheckResult

    data object MissingCredentials : ForecastAuthCheckResult

    data object MissingApiKey : ForecastAuthCheckResult
}
