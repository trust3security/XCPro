package com.example.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.isActive

internal data class PersistentIssueVisibilityState(
    val visible: Boolean,
    val healthySinceMonoMs: Long?
)

internal fun reducePersistentIssueVisibility(
    previous: PersistentIssueVisibilityState,
    enabled: Boolean,
    issueActive: Boolean,
    healthy: Boolean,
    recoveryDwellMs: Long,
    nowMonoMs: Long
): PersistentIssueVisibilityState {
    if (!enabled) {
        return PersistentIssueVisibilityState(visible = false, healthySinceMonoMs = null)
    }
    if (issueActive) {
        return PersistentIssueVisibilityState(visible = true, healthySinceMonoMs = null)
    }
    if (!previous.visible) {
        return previous.copy(healthySinceMonoMs = null)
    }
    if (!healthy) {
        return previous.copy(healthySinceMonoMs = null)
    }
    if (recoveryDwellMs <= 0L) {
        return PersistentIssueVisibilityState(visible = false, healthySinceMonoMs = null)
    }

    val healthySinceMonoMs = previous.healthySinceMonoMs ?: nowMonoMs
    val elapsedMs = nowMonoMs - healthySinceMonoMs
    return if (elapsedMs >= recoveryDwellMs) {
        PersistentIssueVisibilityState(visible = false, healthySinceMonoMs = null)
    } else {
        PersistentIssueVisibilityState(visible = true, healthySinceMonoMs = healthySinceMonoMs)
    }
}

@Composable
internal fun rememberPersistentIssueVisibility(
    enabled: Boolean,
    issueActive: Boolean,
    healthy: Boolean,
    recoveryDwellMs: Long
): Boolean {
    var state by remember(enabled) {
        mutableStateOf(
            PersistentIssueVisibilityState(
                visible = enabled && issueActive,
                healthySinceMonoMs = null
            )
        )
    }
    LaunchedEffect(enabled, issueActive, healthy, recoveryDwellMs) {
        fun updateState(nowMonoMs: Long) {
            state = reducePersistentIssueVisibility(
                previous = state,
                enabled = enabled,
                issueActive = issueActive,
                healthy = healthy,
                recoveryDwellMs = recoveryDwellMs,
                nowMonoMs = nowMonoMs
            )
        }

        updateState(withFrameNanos { it / 1_000_000L })
        if (!state.visible || issueActive || !healthy) {
            return@LaunchedEffect
        }

        while (isActive && state.visible && !issueActive && healthy) {
            updateState(withFrameNanos { it / 1_000_000L })
        }
    }
    return state.visible
}
