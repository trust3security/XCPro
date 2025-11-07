package com.example.xcpro.skysight

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object SkysightCredentialsTest {
    private const val TAG = "SkysightCredentialsTest"
    
    fun testWithRealCredentials(context: Context) {
        val scope = CoroutineScope(Dispatchers.IO)
        
        Log.d(TAG, "🔐 Testing Skysight with real API key...")
        
        scope.launch {
            val client = SkysightClient.getInstance(context)
            
            // Test with the email from Skysight support
            // You'll need to replace "YOUR_ACTUAL_PASSWORD" with your real password
            val result = client.authenticate("matthew@skysight.io", "YOUR_ACTUAL_PASSWORD")
            
            result.onSuccess {
                Log.d(TAG, "🎉 SUCCESS! Skysight authentication worked!")
                Log.d(TAG, "📡 Loading regions...")
                client.loadRegions()
            }.onFailure { error ->
                Log.e(TAG, "❌ Authentication failed: ${error.message}")
                Log.d(TAG, "💡 Make sure to replace 'YOUR_ACTUAL_PASSWORD' with your real Skysight password")
            }
        }
    }
}