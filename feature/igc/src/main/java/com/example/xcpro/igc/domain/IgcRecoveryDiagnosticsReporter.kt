package com.example.xcpro.igc.domain

import android.util.Log

interface IgcRecoveryDiagnosticsReporter {
    fun report(event: String, attributes: Map<String, String> = emptyMap())
}

class LogcatIgcRecoveryDiagnosticsReporter : IgcRecoveryDiagnosticsReporter {
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
        private const val TAG = "IgcRecoveryDiagnostics"
    }
}

object NoOpIgcRecoveryDiagnosticsReporter : IgcRecoveryDiagnosticsReporter {
    override fun report(event: String, attributes: Map<String, String>) = Unit
}
