package com.example.xcpro.weglide.data

import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.weglide.api.WeGlideUploadApi
import com.example.xcpro.weglide.auth.WeGlideAuthManager
import com.example.xcpro.weglide.domain.ResolveWeGlideAircraftForProfileUseCase
import com.example.xcpro.weglide.domain.WeGlideAccountStore
import com.example.xcpro.weglide.domain.WeGlideAircraftMappingResolution
import com.example.xcpro.weglide.domain.WeGlideFlightUploadRepository
import com.example.xcpro.weglide.domain.WeGlideUploadExecutionResult
import com.example.xcpro.weglide.domain.WeGlideUploadQueueRecord
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.firstOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class WeGlideFlightUploadRepositoryImpl @Inject constructor(
    private val uploadApi: WeGlideUploadApi,
    private val authManager: WeGlideAuthManager,
    private val accountStore: WeGlideAccountStore,
    private val resolveWeGlideAircraftForProfileUseCase: ResolveWeGlideAircraftForProfileUseCase,
    private val igcDocumentStore: WeGlideIgcDocumentStore
) : WeGlideFlightUploadRepository {

    override suspend fun uploadQueuedFlight(
        item: WeGlideUploadQueueRecord
    ): WeGlideUploadExecutionResult {
        val resolution = resolveWeGlideAircraftForProfileUseCase(item.localProfileId)
        if (resolution.status != WeGlideAircraftMappingResolution.Status.MAPPED ||
            resolution.mapping == null ||
            resolution.aircraft == null
        ) {
            return WeGlideUploadExecutionResult.PermanentFailure(
                code = null,
                message = "No WeGlide aircraft mapping for profile ${item.localProfileId}"
            )
        }

        val accessToken = authManager.getValidAccessToken()
            ?: return WeGlideUploadExecutionResult.PermanentFailure(
                code = 401,
                message = "WeGlide account is not connected"
            )

        val bytes = runCatching {
            igcDocumentStore.readBytes(item.igcPath)
        }.getOrElse { error ->
            return WeGlideUploadExecutionResult.RetryableFailure(
                code = null,
                message = error.message ?: "Failed to read IGC file"
            )
        }

        val fileName = DocumentRef(uri = item.igcPath).fileName()
        val filePart = MultipartBody.Part.createFormData(
            name = "file",
            filename = fileName,
            body = bytes.toRequestBody("application/octet-stream".toMediaType())
        )
        val aircraftIdBody = resolution.mapping.weglideAircraftId
            .toString()
            .toRequestBody(TEXT_PLAIN_MEDIA_TYPE)
        val userIdBody = accountStore.accountLink
            .firstOrNull()
            ?.userId
            ?.toString()
            ?.toRequestBody(TEXT_PLAIN_MEDIA_TYPE)

        val response = runCatching {
            uploadApi.uploadIgcOAuth(
                bearerToken = "Bearer $accessToken",
                file = filePart,
                aircraftId = aircraftIdBody,
                userId = userIdBody,
                dateOfBirth = null
            )
        }.getOrElse { error ->
            return WeGlideUploadExecutionResult.RetryableFailure(
                code = null,
                message = error.message ?: "WeGlide upload call failed"
            )
        }

        val responseText = runCatching {
            response.body()?.string() ?: response.errorBody()?.string()
        }.getOrNull()

        return when (response.code()) {
            201 -> WeGlideUploadExecutionResult.Success(
                remoteFlightId = parseRemoteFlightId(responseText)
            )
            401 -> {
                authManager.disconnect()
                WeGlideUploadExecutionResult.PermanentFailure(
                    code = 401,
                    message = "WeGlide authorization expired"
                )
            }
            409 -> WeGlideUploadExecutionResult.Duplicate(
                remoteFlightId = parseRemoteFlightId(responseText)
            )
            429, 500, 502, 503, 504 -> WeGlideUploadExecutionResult.RetryableFailure(
                code = response.code(),
                message = responseText ?: "WeGlide temporarily unavailable"
            )
            422 -> WeGlideUploadExecutionResult.PermanentFailure(
                code = 422,
                message = responseText ?: "WeGlide rejected the upload"
            )
            else -> {
                if (response.isSuccessful) {
                    WeGlideUploadExecutionResult.Success(
                        remoteFlightId = parseRemoteFlightId(responseText)
                    )
                } else {
                    WeGlideUploadExecutionResult.PermanentFailure(
                        code = response.code(),
                        message = responseText ?: "Unexpected WeGlide response ${response.code()}"
                    )
                }
            }
        }
    }

    private fun parseRemoteFlightId(responseText: String?): Long? {
        if (responseText.isNullOrBlank()) return null
        return runCatching {
            val root = JsonParser.parseString(responseText)
            when {
                root.isJsonArray -> {
                    val array = root.asJsonArray
                    if (array.size() == 0) {
                        null
                    } else {
                        array[0].asJsonObject.get("id")?.asLong
                    }
                }
                root.isJsonObject -> {
                    val obj = root.asJsonObject
                    when {
                        obj.has("id") -> obj.get("id").asLong
                        obj.has("data") && obj.get("data").isJsonArray -> {
                            val array = obj.getAsJsonArray("data")
                            if (array.size() == 0) {
                                null
                            } else {
                                array[0].asJsonObject.get("id")?.asLong
                            }
                        }
                        else -> null
                    }
                }
                else -> null
            }
        }.getOrNull()
    }

    companion object {
        private val TEXT_PLAIN_MEDIA_TYPE = "text/plain".toMediaType()
    }
}
