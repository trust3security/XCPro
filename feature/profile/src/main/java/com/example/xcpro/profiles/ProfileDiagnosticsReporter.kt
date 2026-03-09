package com.example.xcpro.profiles

import android.util.Log

interface ProfileDiagnosticsReporter {
    fun report(event: String, attributes: Map<String, String> = emptyMap())
}

class LogcatProfileDiagnosticsReporter : ProfileDiagnosticsReporter {
    override fun report(event: String, attributes: Map<String, String>) {
        val payload = if (attributes.isEmpty()) {
            "-"
        } else {
            attributes.entries
                .sortedBy { it.key }
                .joinToString(separator = ", ") { entry -> "${entry.key}=${entry.value}" }
        }
        runCatching {
            Log.i(TAG, "$event | $payload")
        }
    }

    private companion object {
        private const val TAG = "ProfileDiagnostics"
    }
}

class NoOpProfileDiagnosticsReporter : ProfileDiagnosticsReporter {
    override fun report(event: String, attributes: Map<String, String>) = Unit
}

