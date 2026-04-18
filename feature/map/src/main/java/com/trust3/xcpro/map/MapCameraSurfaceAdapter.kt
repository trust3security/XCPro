package com.trust3.xcpro.map

import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng

internal class MapCameraSurfaceAdapter(
    private val mapState: MapScreenState
) : MapCameraSurfacePort {

    override fun cameraPoseOrNull(): MapCameraPose? {
        val position = mapState.mapLibreMap?.cameraPosition ?: return null
        return position.toMapCameraPose()
    }

    override fun viewportMetricsOrNull(): MapCameraViewportMetrics? {
        val mapView = mapState.mapView ?: return null
        return MapCameraViewportMetrics(
            widthPx = mapView.width,
            heightPx = mapView.height,
            pixelRatio = mapView.pixelRatio
        )
    }

    override fun metersPerPixelAtLatitude(latitude: Double): Double? {
        return mapState.mapLibreMap?.projection?.getMetersPerPixelAtLatitude(latitude)
    }

    override fun moveTo(target: MapPoint, zoom: Double) {
        mapState.mapLibreMap?.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(target.latitude, target.longitude),
                zoom
            )
        )
    }

    override fun moveTo(pose: MapCameraPose) {
        val target = pose.target ?: return
        val cameraPosition = CameraPosition.Builder()
            .target(LatLng(target.latitude, target.longitude))
            .zoom(pose.zoom)
            .bearing(pose.bearing)
            .tilt(pose.tilt)
            .build()
        mapState.mapLibreMap?.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
    }
}

private fun CameraPosition.toMapCameraPose(): MapCameraPose {
    return MapCameraPose(
        target = target?.let { MapPoint(it.latitude, it.longitude) },
        zoom = zoom,
        bearing = bearing,
        tilt = tilt
    )
}
