package com.trust3.xcpro.weglide.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

interface WeGlideApi {
    @GET("v1/aircraft")
    suspend fun getAircraft(
        @Header("Authorization") authorization: String? = null
    ): Response<List<WeGlideAircraftDto>>
}
