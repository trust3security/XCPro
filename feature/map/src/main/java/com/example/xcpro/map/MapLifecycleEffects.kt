package com.example.xcpro.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Compose lifecycle effects for MapScreen integration.
 */
object MapLifecycleEffects {

    @Composable
    fun LifecycleObserverEffect(
        lifecycleManager: MapLifecycleManager
    ) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle

        DisposableEffect(lifecycle, lifecycleManager) {
            val observer = LifecycleEventObserver { _, event ->
                lifecycleManager.handleLifecycleEvent(event)
            }
            lifecycle.addObserver(observer)
            lifecycleManager.syncCurrentOwnerState(lifecycle.currentState)

            onDispose {
                lifecycle.removeObserver(observer)
            }
        }
    }

    @Composable
    fun LocationCleanupEffect(
        locationManager: LocationManager
    ) {
        DisposableEffect(Unit) {
            onDispose {
                locationManager.stopLocationTracking()
            }
        }
    }
}
