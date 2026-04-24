package com.trust3.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.trust3.xcpro.map.config.MapFeatureFlags
import dagger.hilt.android.EntryPointAccessors

@Composable
internal fun rememberMapScreenFeatureFlags(): MapFeatureFlags {
    val context = LocalContext.current
    val runtimeEntryPoint = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            MapScreenRuntimeEntryPoint::class.java
        )
    }
    return remember(runtimeEntryPoint) { runtimeEntryPoint.mapFeatureFlags() }
}
