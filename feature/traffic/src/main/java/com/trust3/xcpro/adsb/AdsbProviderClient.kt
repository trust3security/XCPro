package com.trust3.xcpro.adsb

interface AdsbProviderClient {
    suspend fun fetchStates(
        bbox: BBox,
        auth: AdsbAuth?
    ): ProviderResult
}

enum class AdsbNetworkFailureKind {
    DNS,
    TIMEOUT,
    CONNECT,
    NO_ROUTE,
    TLS,
    MALFORMED_RESPONSE,
    UNKNOWN
}

sealed interface ProviderResult {
    data class Success(
        val response: OpenSkyResponse,
        val httpCode: Int,
        val remainingCredits: Int?
    ) : ProviderResult

    data class RateLimited(
        val retryAfterSec: Int,
        val remainingCredits: Int?
    ) : ProviderResult

    data class HttpError(
        val code: Int,
        val message: String
    ) : ProviderResult

    data class NetworkError(
        val kind: AdsbNetworkFailureKind,
        val message: String
    ) : ProviderResult
}
