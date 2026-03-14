package com.example.xcpro.map

import com.example.xcpro.common.orientation.MapOrientationMode
import org.maplibre.android.geometry.LatLng

interface MapLocationOverlayPort {
    fun updateBlueLocation(
        location: LatLng,
        trackBearing: Double,
        iconHeading: Double,
        mapBearing: Double,
        orientationMode: MapOrientationMode
    )

    fun setBlueLocationVisible(visible: Boolean)
}
