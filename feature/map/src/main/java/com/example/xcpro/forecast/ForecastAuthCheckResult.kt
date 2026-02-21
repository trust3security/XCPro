package com.example.xcpro.forecast

sealed interface ForecastAuthCheckResult {
    data class Success(
        val code: Int,
        val message: String
    ) : ForecastAuthCheckResult

    data class HttpError(
        val code: Int,
        val message: String
    ) : ForecastAuthCheckResult

    data class NetworkError(
        val message: String
    ) : ForecastAuthCheckResult

    data object MissingCredentials : ForecastAuthCheckResult

    data object MissingApiKey : ForecastAuthCheckResult
}
