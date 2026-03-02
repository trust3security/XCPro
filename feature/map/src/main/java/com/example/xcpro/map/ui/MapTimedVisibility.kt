package com.example.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun rememberTimedVisibility(
    enabled: Boolean,
    readyForAutoDismiss: Boolean,
    autoDismissDelayMs: Long
): Boolean {
    var visible by remember(enabled) { mutableStateOf(enabled) }
    LaunchedEffect(enabled, readyForAutoDismiss, autoDismissDelayMs) {
        if (!enabled) {
            visible = false
            return@LaunchedEffect
        }
        visible = true
        if (!readyForAutoDismiss) return@LaunchedEffect
        delay(autoDismissDelayMs)
        if (isActive) {
            visible = false
        }
    }
    return visible
}
