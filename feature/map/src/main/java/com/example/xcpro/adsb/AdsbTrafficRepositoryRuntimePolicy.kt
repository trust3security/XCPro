package com.example.xcpro.adsb

// Shared ADS-B runtime policy constants used by loop/polling paths.
internal const val MAX_DISPLAYED_TARGETS = 30
internal const val STALE_AFTER_SEC = 60
internal const val EXPIRY_AFTER_SEC = 120

internal const val POLL_INTERVAL_HOT_MS = 10_000L
internal const val POLL_INTERVAL_WARM_MS = 20_000L
internal const val POLL_INTERVAL_COLD_MS = 30_000L
internal const val POLL_INTERVAL_QUIET_MS = 40_000L
internal const val POLL_INTERVAL_MAX_MS = 60_000L

internal const val MOVEMENT_FAST_POLL_THRESHOLD_METERS = 500.0
internal const val EMPTY_STREAK_WARM_POLLS = 1
internal const val EMPTY_STREAK_COLD_POLLS = 3
internal const val EMPTY_STREAK_QUIET_POLLS = 6

internal const val CREDIT_FLOOR_GUARDED = 500
internal const val CREDIT_FLOOR_LOW = 200
internal const val CREDIT_FLOOR_CRITICAL = 50
internal const val BUDGET_FLOOR_GUARDED_MS = 20_000L
internal const val BUDGET_FLOOR_LOW_MS = 30_000L
internal const val BUDGET_FLOOR_CRITICAL_MS = 60_000L

internal const val ANONYMOUS_POLL_FLOOR_MS = 30_000L
internal const val AUTH_FAILED_POLL_FLOOR_MS = 45_000L

internal const val REQUEST_HISTORY_WINDOW_MS = 60L * 60L * 1_000L
internal const val REQUESTS_PER_HOUR_GUARDED = 120
internal const val REQUESTS_PER_HOUR_LOW = 180
internal const val REQUESTS_PER_HOUR_CRITICAL = 300

internal const val RECONNECT_BACKOFF_START_MS = 2_000L
internal const val RECONNECT_BACKOFF_MAX_MS = 60_000L

internal const val NETWORK_WAIT_HOUSEKEEPING_TICK_MS = 1_000L

internal const val OWN_ALTITUDE_RESELECT_MIN_INTERVAL_MS = 1_000L
internal const val OWN_ALTITUDE_RESELECT_MAX_INTERVAL_MS = 10_000L
internal const val OWN_ALTITUDE_RESELECT_MIN_DELTA_METERS = 1.0
internal const val OWN_ALTITUDE_RESELECT_FORCE_DELTA_METERS = 25.0
internal const val OWNSHIP_REFERENCE_STALE_AFTER_MS = 120_000L

internal sealed interface CenterWaitState<out T> {
    data object Waiting : CenterWaitState<Nothing>
    data object Disabled : CenterWaitState<Nothing>
    data class Ready<T>(val center: T) : CenterWaitState<T>
}

internal sealed interface NetworkWaitState {
    data object Offline : NetworkWaitState
    data object Disabled : NetworkWaitState
    data object Online : NetworkWaitState
}
