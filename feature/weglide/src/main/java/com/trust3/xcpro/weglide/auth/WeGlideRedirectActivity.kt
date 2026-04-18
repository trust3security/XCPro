package com.trust3.xcpro.weglide.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WeGlideRedirectActivity : ComponentActivity() {

    @Inject
    lateinit var authManager: WeGlideAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val redirectUri = intent?.data
        lifecycleScope.launch {
            val result = if (redirectUri == null) {
                Result.failure(IllegalStateException("Missing WeGlide redirect URI"))
            } else {
                authManager.handleAuthorizationRedirect(redirectUri)
            }
            result.exceptionOrNull()?.let { error ->
                Toast.makeText(
                    this@WeGlideRedirectActivity,
                    error.message ?: "WeGlide connection failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
            reopenMainTask()
            finish()
        }
    }

    private fun reopenMainTask() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        startActivity(launchIntent)
    }
}
