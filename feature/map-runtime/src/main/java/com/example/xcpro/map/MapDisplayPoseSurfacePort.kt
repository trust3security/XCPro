package com.example.xcpro.map

interface MapDisplayPoseSurfacePort {
    fun isMapReady(): Boolean
    fun currentCameraBearing(): Double?
    fun distancePerPixelMetersAt(latitude: Double): Double?
}
