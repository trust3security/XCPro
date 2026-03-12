package com.example.xcpro.weglide.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface WeGlideUploadApi {
    @Multipart
    @POST("v1/igcfile")
    suspend fun uploadIgcOAuth(
        @Header("Authorization") bearerToken: String,
        @Part file: MultipartBody.Part,
        @Part("aircraft_id") aircraftId: RequestBody,
        @Part("user_id") userId: RequestBody?,
        @Part("date_of_birth") dateOfBirth: RequestBody?
    ): Response<ResponseBody>
}
