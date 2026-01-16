package com.example.xcpro.map.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.example.xcpro.map.LocationManager

/**
 * Compose helper that wires the location permission launcher to LocationManager callbacks.
 */
@Composable
fun rememberLocationPermissionLauncher(
    locationManager: LocationManager
): ActivityResultLauncher<Array<String>> {
    return rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        locationManager.onLocationPermissionsResult(fineLocationGranted)
    }
}
