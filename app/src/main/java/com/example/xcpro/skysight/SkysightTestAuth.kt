package com.example.xcpro.skysight

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object SkysightTestAuth {
    
    fun testAuthentication(context: Context, scope: CoroutineScope) {
        val client = SkysightClient.getInstance(context)
        
        // Replace with your actual Skysight credentials
        val username = "your_email@example.com"
        val password = "your_password"
        
        scope.launch {
            val result = client.authenticate(username, password)
            
            result.onSuccess {
                println("✅ Skysight authentication successful!")
                client.loadRegions()
            }.onFailure { error ->
                println("❌ Skysight authentication failed: ${error.message}")
            }
        }
    }
}