package com.trust3.xcpro.map

class MapLibreCameraControllerProvider(
    private val mapState: MapScreenState
) : MapCameraControllerProvider {
    override fun controllerOrNull(): MapCameraController? {
        return mapState.mapLibreMap?.let { MapLibreMapCameraController(it) }
    }
}
