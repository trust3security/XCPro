package com.trust3.xcpro.map

import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap

class MapLibreMapCameraController(
    private val map: MapLibreMap
) : MapCameraController {
    override val cameraPosition: MapCameraPositionSnapshot
        get() {
            val position = map.cameraPosition
            return MapCameraPositionSnapshot(
                target = position.target,
                zoom = position.zoom,
                bearing = position.bearing,
                tilt = position.tilt
            )
        }

    override fun moveCamera(position: MapCameraPositionSnapshot) {
        val update = CameraUpdateFactory.newCameraPosition(
            org.maplibre.android.camera.CameraPosition.Builder()
                .target(position.target)
                .zoom(position.zoom)
                .bearing(position.bearing)
                .tilt(position.tilt)
                .build()
        )
        map.moveCamera(update)
    }

    override fun animateCamera(
        position: MapCameraPositionSnapshot,
        durationMs: Int,
        callback: MapCameraController.CancelableCallback?
    ) {
        val update = CameraUpdateFactory.newCameraPosition(
            org.maplibre.android.camera.CameraPosition.Builder()
                .target(position.target)
                .zoom(position.zoom)
                .bearing(position.bearing)
                .tilt(position.tilt)
                .build()
        )
        if (callback == null) {
            map.animateCamera(update, durationMs)
            return
        }
        map.animateCamera(
            update,
            durationMs,
            object : MapLibreMap.CancelableCallback {
                override fun onFinish() = callback.onFinish()
                override fun onCancel() = callback.onCancel()
            }
        )
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        map.setPadding(left, top, right, bottom)
    }

    override fun triggerRepaint() {
        map.triggerRepaint()
    }
}
