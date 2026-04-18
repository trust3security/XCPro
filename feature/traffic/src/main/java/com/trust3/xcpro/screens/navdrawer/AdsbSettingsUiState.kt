package com.trust3.xcpro.screens.navdrawer

import com.trust3.xcpro.adsb.ADSB_EMERGENCY_FLASH_ENABLED_DEFAULT
import com.trust3.xcpro.adsb.ADSB_ICON_SIZE_DEFAULT_PX
import com.trust3.xcpro.adsb.ADSB_MAX_DISTANCE_DEFAULT_KM
import com.trust3.xcpro.adsb.ADSB_VERTICAL_FILTER_ABOVE_DEFAULT_METERS
import com.trust3.xcpro.adsb.ADSB_VERTICAL_FILTER_BELOW_DEFAULT_METERS
import com.trust3.xcpro.common.units.UnitsPreferences

data class AdsbSettingsUiState(
    val iconSizePx: Int = ADSB_ICON_SIZE_DEFAULT_PX,
    val maxDistanceKm: Int = ADSB_MAX_DISTANCE_DEFAULT_KM,
    val verticalAboveMeters: Double = ADSB_VERTICAL_FILTER_ABOVE_DEFAULT_METERS,
    val verticalBelowMeters: Double = ADSB_VERTICAL_FILTER_BELOW_DEFAULT_METERS,
    val emergencyFlashEnabled: Boolean = ADSB_EMERGENCY_FLASH_ENABLED_DEFAULT,
    val emergencyAudioEnabled: Boolean = false,
    val emergencyAudioMasterEnabled: Boolean = true,
    val emergencyAudioShadowMode: Boolean = false,
    val emergencyAudioRollbackLatched: Boolean = false,
    val emergencyAudioRollbackReason: String? = null,
    val units: UnitsPreferences = UnitsPreferences()
)

