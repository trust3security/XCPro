package com.example.baseui1

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.example.baseui1.tasks.KeyholeVerification

/**
 * Simple test activity to run KeyholeVerification
 */
class KeyholeTestActivity : Activity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Run keyhole verification
        runKeyholeVerification()
    }
    
    private fun runKeyholeVerification() {
        try {
            Log.d("KeyholeTest", "Starting keyhole verification...")
            println("🔑 KEYHOLE VERIFICATION TEST STARTING...")
            
            val verification = KeyholeVerification()
            val results = verification.verifyKeyholeImplementation()
            
            println("🔑 KEYHOLE VERIFICATION RESULTS:")
            println(results)
            
            Log.d("KeyholeTest", "Keyhole verification completed")
            Log.d("KeyholeTest", results)
            
        } catch (e: Exception) {
            Log.e("KeyholeTest", "Error running keyhole verification", e)
            println("🔑 ERROR: Keyhole verification failed: ${e.message}")
            e.printStackTrace()
        }
    }
}