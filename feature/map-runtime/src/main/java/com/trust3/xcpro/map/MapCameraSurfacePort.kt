package com.trust3.xcpro.map

data class MapCameraPose(
    val target: MapPoint?,
    val zoom: Double,
    val bearing: Double,
    val tilt: Double
)

data class MapCameraViewportMetrics(
    val widthPx: Int,
    val heightPx: Int,
    val pixelRatio: Float
)

interface MapCameraSurfacePort {
    fun cameraPoseOrNull(): MapCameraPose?
    fun viewportMetricsOrNull(): MapCameraViewportMetrics?
    fun metersPerPixelAtLatitude(latitude: Double): Double?
    fun moveTo(target: MapPoint, zoom: Double)
    fun moveTo(pose: MapCameraPose)
}
