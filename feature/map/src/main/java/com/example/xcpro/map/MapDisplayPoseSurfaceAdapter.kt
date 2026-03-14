package com.example.xcpro.map

internal class MapDisplayPoseSurfaceAdapter(
    private val mapState: MapScreenState
) : MapDisplayPoseSurfacePort {
    override fun isMapReady(): Boolean = mapState.mapLibreMap != null

    override fun currentCameraBearing(): Double? = mapState.mapLibreMap?.cameraPosition?.bearing
}
