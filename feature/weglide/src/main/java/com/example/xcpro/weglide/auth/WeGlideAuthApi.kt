package com.example.xcpro.weglide.auth

import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Url

interface WeGlideAuthApi {
    @FormUrlEncoded
    @POST
    suspend fun exchangeCode(
        @Url tokenUrl: String,
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("client_id") clientId: String,
        @Field("code_verifier") codeVerifier: String
    ): Response<WeGlideTokenResponseDto>

    @FormUrlEncoded
    @POST
    suspend fun refreshToken(
        @Url tokenUrl: String,
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String,
        @Field("client_id") clientId: String
    ): Response<WeGlideTokenResponseDto>
}
