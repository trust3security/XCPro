package com.trust3.xcpro.map

import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

class MapCameraUpdateGateAdapter(
    private val gate: MapLocationFilter,
    private val mapProvider: () -> MapLibreMap?
) : MapCameraUpdateGate {
    override fun accept(location: LatLng): Boolean {
        val map = mapProvider() ?: return false
        return gate.accept(location, map)
    }

    override fun resetTo(location: LatLng) {
        mapProvider()?.let { gate.resetTo(location, it) }
    }
}
