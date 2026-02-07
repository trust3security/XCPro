package com.example.xcpro.map

/**
 * Write-only facade for MapStateStore mutations. UI/runtime managers should call these
 * so the ViewModel remains the single writer of map state.
 */
interface MapStateActions {
    fun setShowDistanceCircles(show: Boolean)
    fun toggleDistanceCircles()
    fun updateCurrentZoom(zoom: Float)
    fun setTarget(location: MapStateStore.MapPoint?, zoom: Float?)
    fun setCurrentUserLocation(location: MapStateStore.MapPoint?)
    fun setHasInitiallyCentered(centered: Boolean)
    fun setTrackingLocation(enabled: Boolean)
    fun setShowRecenterButton(show: Boolean)
    fun setShowReturnButton(show: Boolean)
    fun updateLastUserPanTime(timestampMillis: Long)
    fun saveLocation(location: MapStateStore.MapPoint?, zoom: Double?, bearing: Double?)
    fun updateCameraSnapshot(target: MapStateStore.MapPoint?, zoom: Double?, bearing: Double?)
    fun setDisplayPoseMode(mode: DisplayPoseMode)
    fun setDisplaySmoothingProfile(profile: DisplaySmoothingProfile)
}
