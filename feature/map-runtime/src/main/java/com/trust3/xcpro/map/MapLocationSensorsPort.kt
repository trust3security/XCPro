package com.trust3.xcpro.map

interface MapLocationSensorsPort {
    fun onLocationPermissionsResult(fineLocationGranted: Boolean)
    fun requestLocationPermissions(permissionRequester: MapLocationPermissionRequester)
    fun stopLocationTracking(force: Boolean = false)
    fun restartSensorsIfNeeded()
    fun isGpsEnabled(): Boolean
}
