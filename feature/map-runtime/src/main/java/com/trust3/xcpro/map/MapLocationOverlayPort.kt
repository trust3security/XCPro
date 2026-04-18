package com.trust3.xcpro.map

import com.trust3.xcpro.common.orientation.MapOrientationMode
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
