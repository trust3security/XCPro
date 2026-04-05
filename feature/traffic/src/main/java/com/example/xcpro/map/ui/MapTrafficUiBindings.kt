package com.example.xcpro.map.ui

import com.example.xcpro.map.AdsbSelectedTargetDetails
import com.example.xcpro.map.AdsbTrafficSnapshot
import com.example.xcpro.map.Icao24
import com.example.xcpro.map.SelectedOgnThermalContext
import com.example.xcpro.map.OgnThermalHotspot
import com.example.xcpro.map.OgnTrafficSnapshot
import com.example.xcpro.map.OgnTrafficTarget

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
