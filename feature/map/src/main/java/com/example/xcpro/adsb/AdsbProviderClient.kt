package com.example.xcpro.adsb

interface AdsbProviderClient {
    suspend fun fetchStates(
        bbox: BBox,
        auth: AdsbAuth?
    ): ProviderResult
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
        val message: String
    ) : ProviderResult
}

