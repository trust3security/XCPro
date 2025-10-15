package com.example.xcpro.skysight

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import retrofit2.HttpException

object SkysightDebugHelper {
    
    fun testConnection(context: Context, scope: CoroutineScope) {
        val client = SkysightClient.getInstance(context)
        
        scope.launch {
            try {
                android.util.Log.d("SkysightDebug", "🔍 Testing Skysight API connection...")
                
                // Test with dummy credentials to see what error we get
                val result = client.authenticate("test@example.com", "testpassword")
                
                result.onSuccess {
                    android.util.Log.d("SkysightDebug", "✅ Authentication successful!")
                }.onFailure { error ->
                    when (error) {
                        is HttpException -> {
                            android.util.Log.e("SkysightDebug", "❌ HTTP Error: ${error.code()}")
                            android.util.Log.e("SkysightDebug", "Response: ${error.response()?.errorBody()?.string()}")
                        }
                        else -> {
                            android.util.Log.e("SkysightDebug", "❌ Error: ${error.message}")
                        }
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("SkysightDebug", "❌ Exception during test: ${e.message}", e)
            }
        }
    }
    
    fun validateCredentials(username: String, password: String): String? {
        return when {
            username.isBlank() -> "Username cannot be empty"
            password.isBlank() -> "Password cannot be empty"
            !username.contains("@") -> "Username should be an email address"
            password.length < 3 -> "Password too short"
            else -> null
        }
    }
}