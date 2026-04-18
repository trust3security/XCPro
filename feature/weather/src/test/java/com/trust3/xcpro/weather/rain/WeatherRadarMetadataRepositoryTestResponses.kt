package com.trust3.xcpro.weather.rain

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

internal fun metadataResponse(
    request: Request,
    generatedEpochSec: Long,
    etag: String? = null,
    lastModified: String? = null
): Response {
    val responseBuilder = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(
            """
            {
              "generated": $generatedEpochSec,
              "host": "https://tilecache.rainviewer.com",
              "radar": {
                "past": [
                  {"time": 1771554600, "path": "/v2/radar/1771554600"}
                ]
              }
            }
            """.trimIndent().toResponseBody("application/json".toMediaType())
        )
    if (!etag.isNullOrBlank()) {
        responseBuilder.header("ETag", etag)
    }
    if (!lastModified.isNullOrBlank()) {
        responseBuilder.header("Last-Modified", lastModified)
    }
    return responseBuilder.build()
}

internal fun noFramesMetadataResponse(
    request: Request,
    generatedEpochSec: Long
): Response =
    Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(
            """
            {
              "generated": $generatedEpochSec,
              "host": "https://tilecache.rainviewer.com",
              "radar": {
                "past": []
              }
            }
            """.trimIndent().toResponseBody("application/json".toMediaType())
        )
        .build()

internal fun notModifiedResponse(request: Request): Response =
    Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(304)
        .message("Not Modified")
        .body("".toResponseBody("application/json".toMediaType()))
        .build()
