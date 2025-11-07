package com.example.xcpro.map.domain

/**
 * Typed error hierarchy used by the map feature to avoid leaking raw strings through state.
 */
sealed interface MapError {
    val recoveryHint: String?
}

sealed class MapWaypointError : MapError {
    abstract override val recoveryHint: String?

    data class LoadFailed(val cause: Throwable) : MapWaypointError() {
        override val recoveryHint: String? = cause.message
    }

    data object Empty : MapWaypointError() {
        override val recoveryHint: String? = "No waypoints available for this profile."
    }
}

/**
 * Helper used by UI layers to convert errors into presentable text.
 */
fun MapError.toUserMessage(default: String): String = when (this) {
    is MapWaypointError.LoadFailed -> recoveryHint ?: default
    is MapWaypointError.Empty -> recoveryHint ?: default
}
