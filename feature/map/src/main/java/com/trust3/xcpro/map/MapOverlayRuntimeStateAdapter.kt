package com.trust3.xcpro.map

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

    override var ognTrafficOverlay: OgnTrafficOverlayHandle?
        get() = mapState.ognTrafficOverlay
        set(value) {
            mapState.ognTrafficOverlay = value
        }

    override var ognTargetRingOverlay: OgnTargetRingOverlayHandle?
        get() = mapState.ognTargetRingOverlay
        set(value) {
            mapState.ognTargetRingOverlay = value
        }

    override var ognTargetLineOverlay: OgnTargetLineOverlayHandle?
        get() = mapState.ognTargetLineOverlay
        set(value) {
            mapState.ognTargetLineOverlay = value
        }

    override var ognOwnshipTargetBadgeOverlay: OgnOwnshipTargetBadgeOverlayHandle?
        get() = mapState.ognOwnshipTargetBadgeOverlay
        set(value) {
            mapState.ognOwnshipTargetBadgeOverlay = value
        }

    override var ognThermalOverlay: OgnThermalOverlayHandle?
        get() = mapState.ognThermalOverlay
        set(value) {
            mapState.ognThermalOverlay = value
        }

    override var ognGliderTrailOverlay: OgnGliderTrailOverlayHandle?
        get() = mapState.ognGliderTrailOverlay
        set(value) {
            mapState.ognGliderTrailOverlay = value
        }

    override var ognSelectedThermalOverlay: OgnSelectedThermalOverlayHandle?
        get() = mapState.ognSelectedThermalOverlay
        set(value) {
            mapState.ognSelectedThermalOverlay = value
        }

    override var adsbTrafficOverlay: AdsbTrafficOverlayHandle?
        get() = mapState.adsbTrafficOverlay
        set(value) {
            mapState.adsbTrafficOverlay = value
        }
}
