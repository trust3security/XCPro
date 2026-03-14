package com.example.xcpro.map

import org.maplibre.android.geometry.LatLng

interface MapCameraUpdateGate {
    fun accept(location: LatLng): Boolean
    fun resetTo(location: LatLng)
}
