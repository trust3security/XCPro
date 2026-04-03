package com.example.xcpro.map.ui

import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.map.AdsbSelectedTargetDetails
import com.example.xcpro.map.AdsbTrafficSnapshot
import com.example.xcpro.map.AdsbTrafficUiModel
import com.example.xcpro.map.Icao24
import com.example.xcpro.map.OgnDisplayUpdateMode
import com.example.xcpro.map.OgnGliderTrailSegment
import com.example.xcpro.map.SelectedOgnThermalContext
import com.example.xcpro.map.OgnThermalHotspot
import com.example.xcpro.map.OgnTrafficSnapshot
import com.example.xcpro.map.OgnTrafficTarget

data class MapTrafficUiBinding(
    val ognTargets: List<OgnTrafficTarget>,
    val ognSnapshot: OgnTrafficSnapshot,
    val ognOverlayEnabled: Boolean,
    val ognIconSizePx: Int,
    val ognDisplayUpdateMode: OgnDisplayUpdateMode,
    val ognThermalHotspots: List<OgnThermalHotspot>,
    val showOgnSciaEnabled: Boolean,
    val ognTargetEnabled: Boolean,
    val ognTargetAircraftKey: String?,
    val ognResolvedTarget: OgnTrafficTarget?,
    val showOgnThermalsEnabled: Boolean,
    val ognGliderTrailSegments: List<OgnGliderTrailSegment>,
    val ownshipAltitudeMeters: Double?,
    val ognAltitudeUnit: AltitudeUnit,
    val adsbTargets: List<AdsbTrafficUiModel>,
    val adsbSnapshot: AdsbTrafficSnapshot,
    val adsbOverlayEnabled: Boolean,
    val adsbIconSizePx: Int,
    val adsbEmergencyFlashEnabled: Boolean,
    val adsbDefaultMediumUnknownIconEnabled: Boolean,
    val selectedOgnTarget: OgnTrafficTarget?,
    val selectedOgnThermal: OgnThermalHotspot?,
    val selectedOgnThermalContext: SelectedOgnThermalContext?,
    val selectedAdsbTarget: AdsbSelectedTargetDetails?
)

data class MapTrafficUiActions(
    val onToggleOgnTraffic: () -> Unit,
    val onToggleOgnScia: () -> Unit,
    val onToggleOgnThermals: () -> Unit,
    val onSetOgnTarget: (String, Boolean) -> Unit,
    val onToggleAdsbTraffic: () -> Unit,
    val onOgnTargetSelected: (String) -> Unit,
    val onOgnThermalSelected: (String) -> Unit,
    val onAdsbTargetSelected: (Icao24) -> Unit,
    val onDismissOgnTargetDetails: () -> Unit,
    val onDismissOgnThermalDetails: () -> Unit,
    val onDismissAdsbTargetDetails: () -> Unit
)
