package com.example.xcpro.map

interface MapCameraControllerProvider {
    fun controllerOrNull(): MapCameraController?
}

class MapLibreCameraControllerProvider(
    private val mapState: MapScreenState
) : MapCameraControllerProvider {
    override fun controllerOrNull(): MapCameraController? {
        return mapState.mapLibreMap?.let { MapLibreMapCameraController(it) }
    }
}
