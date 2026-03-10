package com.example.xcpro.map

import org.maplibre.android.maps.MapLibreMap

internal class MapOverlayRuntimeStateAdapter(
    private val mapState: MapScreenState
) : TrafficOverlayRuntimeState {
    override val mapLibreMap: MapLibreMap?
        get() = mapState.mapLibreMap

    override val blueLocationLayerId: String
        get() = BlueLocationOverlay.LAYER_ID

    override fun bringBlueLocationOverlayToFront() {
        mapState.blueLocationOverlay?.bringToFront()
    }

    override var ognTrafficOverlay: OgnTrafficOverlay?
        get() = mapState.ognTrafficOverlay
        set(value) {
            mapState.ognTrafficOverlay = value
        }

    override var ognTargetRingOverlay: OgnTargetRingOverlay?
        get() = mapState.ognTargetRingOverlay
        set(value) {
            mapState.ognTargetRingOverlay = value
        }

    override var ognTargetLineOverlay: OgnTargetLineOverlay?
        get() = mapState.ognTargetLineOverlay
        set(value) {
            mapState.ognTargetLineOverlay = value
        }

    override var ognThermalOverlay: OgnThermalOverlay?
        get() = mapState.ognThermalOverlay
        set(value) {
            mapState.ognThermalOverlay = value
        }

    override var ognGliderTrailOverlay: OgnGliderTrailOverlay?
        get() = mapState.ognGliderTrailOverlay
        set(value) {
            mapState.ognGliderTrailOverlay = value
        }

    override var adsbTrafficOverlay: AdsbTrafficOverlay?
        get() = mapState.adsbTrafficOverlay
        set(value) {
            mapState.adsbTrafficOverlay = value
        }
}
