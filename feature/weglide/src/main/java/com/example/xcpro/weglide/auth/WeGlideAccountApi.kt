package com.example.xcpro.weglide.auth

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url

interface WeGlideAccountApi {
    @GET
    suspend fun getCurrentAccount(
        @Url url: String,
        @Header("Authorization") authorization: String
    ): Response<WeGlideAccountProfileDto>
}
