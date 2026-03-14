package com.example.xcpro.map

import org.maplibre.android.geometry.LatLng

data class MapCameraPositionSnapshot(
    val target: LatLng?,
    val zoom: Double,
    val bearing: Double,
    val tilt: Double
)

interface MapCameraController {
    val cameraPosition: MapCameraPositionSnapshot

    fun moveCamera(position: MapCameraPositionSnapshot)

    fun animateCamera(
        position: MapCameraPositionSnapshot,
        durationMs: Int,
        callback: CancelableCallback? = null
    )

    fun setPadding(left: Int, top: Int, right: Int, bottom: Int)

    fun triggerRepaint()

    interface CancelableCallback {
        fun onFinish()
        fun onCancel()
    }
}
