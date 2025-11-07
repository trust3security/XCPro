package com.example.xcpro.skysight

import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that adds the X-API-Key header required by SkySight API
 * This allows MapLibre RasterSource to authenticate with SkySight tile endpoints
 */
class SkysightHeaderInterceptor(private val apiKey: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        // Only add header to SkySight API requests
        if (originalRequest.url.host == "skysight.io") {
            val authenticatedRequest = originalRequest.newBuilder()
                .addHeader("X-API-Key", apiKey)
                .build()
            
            android.util.Log.d("SkysightInterceptor", "🔑 Added X-API-Key header for: ${originalRequest.url}")
            return chain.proceed(authenticatedRequest)
        }
        
        // Pass through other requests unchanged
        return chain.proceed(originalRequest)
    }
}