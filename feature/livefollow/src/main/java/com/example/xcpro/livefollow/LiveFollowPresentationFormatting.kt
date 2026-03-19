package com.example.xcpro.livefollow

import java.util.Locale

internal fun String.toDisplayLabel(): String {
    return split('_')
        .filter { it.isNotEmpty() }
        .joinToString(separator = " ") { token ->
            token.lowercase(Locale.US).replaceFirstChar { char ->
                char.titlecase(Locale.US)
            }
        }
}

internal fun formatAgeLabel(ageMs: Long?): String? {
    val safeAgeMs = ageMs?.coerceAtLeast(0L) ?: return null
    return when {
        safeAgeMs < 1_000L -> "< 1 s"
        safeAgeMs < 60_000L -> "${safeAgeMs / 1_000L} s"
        else -> "${safeAgeMs / 60_000L} min"
    }
}
