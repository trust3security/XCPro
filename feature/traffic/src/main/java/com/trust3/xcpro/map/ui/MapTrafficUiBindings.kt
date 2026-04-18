package com.trust3.xcpro.map.ui

import com.trust3.xcpro.map.AdsbSelectedTargetDetails
import com.trust3.xcpro.map.AdsbTrafficSnapshot
import com.trust3.xcpro.map.Icao24
import com.trust3.xcpro.map.SelectedOgnThermalContext
import com.trust3.xcpro.map.OgnThermalHotspot
import com.trust3.xcpro.map.OgnTrafficSnapshot
import com.trust3.xcpro.map.OgnTrafficTarget

data class MapTrafficUiBinding(
    val ognSnapshot: OgnTrafficSnapshot,
    val ognOverlayEnabled: Boolean,
    val showOgnSciaEnabled: Boolean,
    val showOgnThermalsEnabled: Boolean,
    val ognTargetEnabled: Boolean,
    val ognTargetAircraftKey: String?,
    val adsbSnapshot: AdsbTrafficSnapshot,
    val adsbOverlayEnabled: Boolean,
    val selectedOgnTarget: OgnTrafficTarget?,
    val selectedOgnThermal: OgnThermalHotspot?,
    val selectedOgnThermalDetailsVisible: Boolean,
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
