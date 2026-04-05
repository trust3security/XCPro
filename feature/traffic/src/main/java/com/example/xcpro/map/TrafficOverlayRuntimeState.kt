package com.example.xcpro.map

import android.content.Context
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.UnitsPreferences
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

/**
 * Map-free interfaces for traffic overlay runtime state and overlay handles.
 * Keeping this abstraction lets `feature:map` store opaque handles while traffic
 * overlay implementations remain in `feature:traffic`.
 */
interface TrafficOverlayRuntimeState {
    val mapLibreMap: MapLibreMap?
    val blueLocationLayerId: String
    fun bringBlueLocationOverlayToFront()

    var ognTrafficOverlay: OgnTrafficOverlayHandle?
    var ognTargetRingOverlay: OgnTargetRingOverlayHandle?
    var ognTargetLineOverlay: OgnTargetLineOverlayHandle?
    var ognOwnshipTargetBadgeOverlay: OgnOwnshipTargetBadgeOverlayHandle?
    var ognThermalOverlay: OgnThermalOverlayHandle?
    var ognGliderTrailOverlay: OgnGliderTrailOverlayHandle?
    var ognSelectedThermalOverlay: OgnSelectedThermalOverlayHandle?
    var adsbTrafficOverlay: AdsbTrafficOverlayHandle?
}

/** Lightweight coordinate for OGN target visuals while staying out of map model dependency. */
data class OverlayCoordinate(
    val latitude: Double,
    val longitude: Double
)

interface AdsbTrafficOverlayHandle {
    fun initialize()
    fun setIconSizePx(iconSizePx: Int)
    fun setViewportZoom(zoomLevel: Float)
    fun setEmergencyFlashEnabled(enabled: Boolean)
    fun setInteractionReducedMotionActive(active: Boolean)
    fun render(
        targets: List<AdsbTrafficUiModel>,
        selectedTargetId: Icao24?,
        ownshipAltitudeMeters: Double?,
        unitsPreferences: UnitsPreferences,
        iconStyleIdOverrides: Map<String, String>
    )
    fun diagnosticsSnapshot(): AdsbTrafficOverlayDiagnosticsSnapshot
    fun findTargetAt(tap: LatLng): Icao24?
    fun cleanup()
    fun bringToFront()
}

interface OgnTrafficOverlayHandle {
    fun initialize()
    fun setIconSizePx(iconSizePx: Int)
    fun setViewportZoom(zoomLevel: Float)
    fun setUseSatelliteContrastIcons(enabled: Boolean)
    fun render(
        targets: List<OgnTrafficTarget>,
        selectedTargetKey: String?,
        ownshipAltitudeMeters: Double?,
        altitudeUnit: AltitudeUnit,
        unitsPreferences: UnitsPreferences
    )
    fun cleanup()
    fun bringToFront()
    fun findTargetAt(tap: LatLng): String?
}

interface OgnTargetRingOverlayHandle {
    fun initialize()
    fun setIconSizePx(iconSizePx: Int)
    fun render(enabled: Boolean, target: OgnTrafficTarget?)
    fun cleanup()
    fun bringToFront()
    fun findTargetAt(tap: LatLng): String?
}

interface OgnTargetLineOverlayHandle {
    fun initialize()
    fun render(enabled: Boolean, ownshipLocation: OverlayCoordinate?, target: OgnTrafficTarget?)
    fun cleanup()
    fun bringToFront()
}

interface OgnOwnshipTargetBadgeOverlayHandle {
    fun initialize()
    fun render(
        enabled: Boolean,
        ownshipLocation: OverlayCoordinate?,
        target: OgnTrafficTarget?,
        ownshipAltitudeMeters: Double?,
        altitudeUnit: AltitudeUnit,
        unitsPreferences: UnitsPreferences
    )
    fun cleanup()
    fun bringToFront()
}

interface OgnThermalOverlayHandle {
    fun initialize()
    fun render(hotspots: List<OgnThermalHotspot>)
    fun findTargetAt(tap: LatLng): String?
    fun cleanup()
}

interface OgnGliderTrailOverlayHandle {
    fun initialize()
    fun render(segments: List<OgnGliderTrailSegment>)
    fun cleanup()
}

interface OgnSelectedThermalOverlayHandle {
    fun initialize()
    fun render(context: SelectedOgnThermalOverlayContext?)
    fun cleanup()
}

typealias AdsbTrafficOverlayFactory = (Context, MapLibreMap, Int) -> AdsbTrafficOverlayHandle
typealias OgnTrafficOverlayFactory = (Context, MapLibreMap, Int, Boolean) -> OgnTrafficOverlayHandle
typealias OgnTargetRingOverlayFactory = (MapLibreMap, Int) -> OgnTargetRingOverlayHandle
typealias OgnTargetLineOverlayFactory = (MapLibreMap) -> OgnTargetLineOverlayHandle
typealias OgnOwnshipTargetBadgeOverlayFactory = (MapLibreMap) -> OgnOwnshipTargetBadgeOverlayHandle
typealias OgnThermalOverlayFactory = (MapLibreMap) -> OgnThermalOverlayHandle
typealias OgnGliderTrailOverlayFactory = (MapLibreMap) -> OgnGliderTrailOverlayHandle
typealias OgnSelectedThermalOverlayFactory = (MapLibreMap) -> OgnSelectedThermalOverlayHandle

/** Default factories for constructing traffic overlays in callers that still own context. */
object TrafficOverlayFactories {
    fun createAdsbTrafficOverlay(
        context: Context,
        map: MapLibreMap,
        iconSizePx: Int
    ): AdsbTrafficOverlayHandle = AdsbTrafficOverlay(
        context = context,
        map = map,
        initialIconSizePx = iconSizePx
    )

    fun createOgnTrafficOverlay(
        context: Context,
        map: MapLibreMap,
        iconSizePx: Int,
        useSatelliteContrastIcons: Boolean
    ): OgnTrafficOverlayHandle = OgnTrafficOverlay(
        context = context,
        map = map,
        initialIconSizePx = iconSizePx,
        initialUseSatelliteContrastIcons = useSatelliteContrastIcons
    )

    fun createOgnTargetRingOverlay(
        map: MapLibreMap,
        iconSizePx: Int
    ): OgnTargetRingOverlayHandle = OgnTargetRingOverlay(
        map = map,
        initialIconSizePx = iconSizePx
    )

    fun createOgnTargetLineOverlay(map: MapLibreMap): OgnTargetLineOverlayHandle =
        OgnTargetLineOverlay(map = map)

    fun createOgnOwnshipTargetBadgeOverlay(map: MapLibreMap): OgnOwnshipTargetBadgeOverlayHandle =
        OgnOwnshipTargetBadgeOverlay(map = map)

    fun createOgnThermalOverlay(map: MapLibreMap): OgnThermalOverlayHandle =
        OgnThermalOverlay(map = map)

    fun createOgnGliderTrailOverlay(map: MapLibreMap): OgnGliderTrailOverlayHandle =
        OgnGliderTrailOverlay(map = map)

    fun createOgnSelectedThermalOverlay(map: MapLibreMap): OgnSelectedThermalOverlayHandle =
        OgnSelectedThermalOverlay(map = map)
}
