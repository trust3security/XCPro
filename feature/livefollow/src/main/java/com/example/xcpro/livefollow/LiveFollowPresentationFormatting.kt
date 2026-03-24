package com.example.xcpro.livefollow

import com.example.xcpro.livefollow.model.LiveFollowTransportAvailability
import com.example.xcpro.livefollow.watch.LiveFollowTaskRenderPolicy
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

internal fun liveFollowTransportLabel(
    availability: LiveFollowTransportAvailability
): String = availability.state.name.toDisplayLabel()

fun liveFollowTaskAttachmentMessage(
    taskRenderPolicy: LiveFollowTaskRenderPolicy
): String? {
    return when (taskRenderPolicy) {
        LiveFollowTaskRenderPolicy.AVAILABLE -> null
        LiveFollowTaskRenderPolicy.BLOCKED_AMBIGUOUS ->
            "Task attach blocked while identity is ambiguous."

        LiveFollowTaskRenderPolicy.READ_ONLY_UNAVAILABLE ->
            "Watched pilot has no shared task right now."

        LiveFollowTaskRenderPolicy.HIDDEN -> null
    }
}
