package com.example.xcpro.skysight

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object SkysightAutoTest {
    private const val TAG = "SkysightTest"
    
    fun runNetworkTests(context: Context) {
        val scope = CoroutineScope(Dispatchers.IO)
        
        Log.d(TAG, "🔍 Starting Skysight network tests...")
        
        scope.launch {
            // Test 1: Basic connectivity to skysight.io
            testBasicConnectivity()
            
            // Test 2: API endpoint accessibility
            testApiEndpoint()
            
            // Test 3: Authentication with dummy data
            testAuthentication(context)
        }
    }
    
    private suspend fun testBasicConnectivity() {
        try {
            Log.d(TAG, "🌐 Testing basic connectivity to skysight.io...")
            val url = URL("https://skysight.io")
            val connection = url.openConnection() as HttpsURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.requestMethod = "GET"
            
            val responseCode = connection.responseCode
            Log.d(TAG, "✅ Basic connectivity test: HTTP $responseCode")
            connection.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Basic connectivity failed: ${e.message}", e)
        }
    }
    
    private suspend fun testApiEndpoint() {
        try {
            Log.d(TAG, "🔗 Testing API endpoint accessibility...")
            val url = URL("https://skysight.io/api/info")
            val connection = url.openConnection() as HttpsURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.requestMethod = "GET"
            
            val responseCode = connection.responseCode
            Log.d(TAG, "🔗 API endpoint test: HTTP $responseCode")
            
            if (responseCode == 401) {
                Log.d(TAG, "✅ Expected 401 - API requires authentication")
            }
            
            connection.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "❌ API endpoint test failed: ${e.message}", e)
        }
    }
    
    private suspend fun testAuthentication(context: Context) {
        try {
            Log.d(TAG, "🔐 Testing authentication flow...")
            val client = SkysightClient.getInstance(context)
            
            // Check if user is already authenticated
            if (client.isAuthenticated.value) {
                Log.d(TAG, "✅ User already authenticated with Skysight")
            } else {
                Log.d(TAG, "ℹ️ User needs to authenticate via Settings → SkySight")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Authentication check exception: ${e.message}", e)
        }
    }
}