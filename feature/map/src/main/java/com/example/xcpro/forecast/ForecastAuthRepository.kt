package com.example.xcpro.forecast

import com.example.xcpro.common.di.IoDispatcher
import com.example.xcpro.map.BuildConfig
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@Singleton
class ForecastAuthRepository @Inject constructor(
    private val credentialsRepository: ForecastCredentialsRepository,
    private val httpClient: OkHttpClient,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {
    suspend fun verifySavedCredentials(): ForecastAuthCheckResult = withContext(dispatcher) {
        val credentials = credentialsRepository.loadCredentials()
            ?: return@withContext ForecastAuthCheckResult.MissingCredentials

        val apiKey = BuildConfig.SKYSIGHT_API_KEY.trim()
        if (apiKey.isBlank()) {
            return@withContext ForecastAuthCheckResult.MissingApiKey
        }

        val requestBody = JSONObject()
            .put("username", credentials.username)
            .put("password", credentials.password)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(AUTH_ENDPOINT)
            .post(requestBody)
            .header("X-API-KEY", apiKey)
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val code = response.code
                val message = response.message.ifBlank { "HTTP $code" }
                return@withContext if (response.isSuccessful) {
                    ForecastAuthCheckResult.Success(code = code, message = message)
                } else {
                    ForecastAuthCheckResult.HttpError(code = code, message = message)
                }
            }
        } catch (e: IOException) {
            return@withContext ForecastAuthCheckResult.NetworkError(
                message = e::class.java.simpleName.ifBlank { "NetworkError" }
            )
        }
    }

    private companion object {
        private const val AUTH_ENDPOINT = "https://skysight.io/api/auth"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
