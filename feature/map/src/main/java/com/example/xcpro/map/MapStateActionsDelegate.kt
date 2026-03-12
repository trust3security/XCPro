package com.example.xcpro.map

internal class MapStateActionsDelegate(
    private val mapStateStore: MapStateStore
) : MapStateActions {

    override fun setShowDistanceCircles(show: Boolean) {
        mapStateStore.setShowDistanceCircles(show)
    }

    override fun toggleDistanceCircles() {
        val next = !mapStateStore.showDistanceCircles.value
        mapStateStore.setShowDistanceCircles(next)
    }

    override fun updateCurrentZoom(zoom: Float) {
        mapStateStore.updateCurrentZoom(zoom)
    }

    override fun setTarget(location: MapPoint?, zoom: Float?) {
        mapStateStore.setTarget(location, zoom)
    }

    override fun setCurrentUserLocation(location: MapPoint?) {
        mapStateStore.setCurrentUserLocation(location)
    }

    override fun setHasInitiallyCentered(centered: Boolean) {
        mapStateStore.setHasInitiallyCentered(centered)
    }

    override fun setTrackingLocation(enabled: Boolean) {
        mapStateStore.setTrackingLocation(enabled)
    }

    override fun setShowRecenterButton(show: Boolean) {
        mapStateStore.setShowRecenterButton(show)
    }

    override fun setShowReturnButton(show: Boolean) {
        mapStateStore.setShowReturnButton(show)
    }

    override fun updateLastUserPanTime(timestampMillis: Long) {
        mapStateStore.updateLastUserPanTime(timestampMillis)
    }

    override fun saveLocation(location: MapPoint?, zoom: Double?, bearing: Double?) {
        mapStateStore.saveLocation(location, zoom, bearing)
    }

    override fun updateCameraSnapshot(target: MapPoint?, zoom: Double?, bearing: Double?) {
        mapStateStore.updateCameraSnapshot(target, zoom, bearing)
    }

    override fun setDisplayPoseMode(mode: DisplayPoseMode) {
        mapStateStore.setDisplayPoseMode(mode)
    }

    override fun setDisplaySmoothingProfile(profile: DisplaySmoothingProfile) {
        mapStateStore.setDisplaySmoothingProfile(profile)
    }
}
