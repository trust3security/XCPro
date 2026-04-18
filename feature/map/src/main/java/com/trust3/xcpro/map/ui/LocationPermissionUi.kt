package com.trust3.xcpro.map.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.trust3.xcpro.map.MapLocationPermissionRequester
import com.trust3.xcpro.map.MapLocationRuntimePort

/**
 * Compose helper that wires the location permission launcher to LocationManager callbacks.
 */
@Composable
fun rememberLocationPermissionRequester(
    locationManager: MapLocationRuntimePort
): MapLocationPermissionRequester {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        locationManager.onLocationPermissionsResult(fineLocationGranted)
    }
    return MapLocationPermissionRequester {
        launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
    }
}
