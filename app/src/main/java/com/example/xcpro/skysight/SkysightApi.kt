package com.example.xcpro.skysight

import retrofit2.Response
import retrofit2.http.*

data class SkysightAuthRequest(
    val username: String,
    val password: String,
    val device_type: String = "Android",
    val device_serial: String
)

data class SkysightAuthResponse(
    val key: String,
    val valid_until: Long,
    val allowed_regions: List<String>?
)

data class Region(
    val id: String,
    val name: String,
    val bounds: List<Float>,
    val update: String,
    val update_interval: Int?,
    val update_times: List<Int>?
)

data class LayerInfo(
    val id: String,
    val name: String,
    val description: String,
    val data_type: String,
    val projection: String,
    val legend: Legend
)

data class Legend(
    val color_mode: String,
    val units: String,
    val units_scale_factor: Float = 1.0f,
    val colors: List<LegendItem>
)

data class LegendItem(
    val name: String,
    val value: String,
    val color: List<Int>
)

data class ServerInfo(
    val server_time: Long,
    val last_updated: Long
)

data class LastUpdateInfo(
    val id: String,
    val name: String,
    val last_updated: Long  // Unix timestamp
)

interface SkysightAuthApi {
    @POST("auth")
    @Headers("Content-Type: application/json")
    suspend fun authenticate(
        @Header("X-API-Key") apiKey: String,
        @Body request: SkysightAuthRequest
    ): Response<SkysightAuthResponse>
}

interface SkysightDataApi {
    @GET("info")
    suspend fun getServerInfo(@Header("X-API-Key") apiKey: String): Response<ServerInfo>
    
    @GET("regions")
    suspend fun getRegions(@Header("X-API-Key") apiKey: String): Response<List<Region>>
    
    @GET("layers")
    suspend fun getLayers(
        @Header("X-API-Key") apiKey: String,
        @Query("region_id") regionId: String
    ): Response<List<LayerInfo>>
    
    @GET("data/last_updated")
    suspend fun getDataLastUpdated(
        @Header("X-API-Key") apiKey: String,
        @Query("region_id") regionId: String
    ): Response<List<LastUpdateInfo>>
}

interface SkysightTileApi {
    @GET("satellite/{z}/{x}/{y}/{year}/{month}/{day}/{time}")
    suspend fun getSatelliteTile(
        @Header("X-API-Key") apiKey: String,
        @Path("z") z: String,
        @Path("x") x: String,
        @Path("y") y: String,
        @Path("year") year: String,
        @Path("month") month: String,
        @Path("day") day: String,
        @Path("time") time: String
    ): Response<okhttp3.ResponseBody>
    
    @GET("rain/{z}/{x}/{y}/{year}/{month}/{day}/{time}")
    suspend fun getRainTile(
        @Header("X-API-Key") apiKey: String,
        @Path("z") z: String,
        @Path("x") x: String,
        @Path("y") y: String,
        @Path("year") year: String,
        @Path("month") month: String,
        @Path("day") day: String,
        @Path("time") time: String
    ): Response<okhttp3.ResponseBody>
}