package com.example.xcpro.map

import com.example.xcpro.common.orientation.MapOrientationMode
import org.maplibre.android.geometry.LatLng

internal class MapLocationOverlayAdapter(
    private val mapState: MapScreenState
) : MapLocationOverlayPort {
    override fun updateBlueLocation(
        location: LatLng,
        trackBearing: Double,
        iconHeading: Double,
        mapBearing: Double,
        orientationMode: MapOrientationMode
    ) {
        mapState.blueLocationOverlay?.updateLocation(
            location,
            trackBearing,
            iconHeading,
            mapBearing,
            orientationMode
        )
    }

    override fun setBlueLocationVisible(visible: Boolean) {
        mapState.blueLocationOverlay?.setVisible(visible)
    }
}
