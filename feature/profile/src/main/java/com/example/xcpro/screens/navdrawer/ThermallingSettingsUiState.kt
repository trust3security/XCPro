package com.example.xcpro.screens.navdrawer

import com.example.xcpro.thermalling.THERMALLING_APPLY_ZOOM_ON_ENTER_DEFAULT
import com.example.xcpro.thermalling.THERMALLING_APPLY_CONTRAST_MAP_ON_ENTER_DEFAULT
import com.example.xcpro.thermalling.THERMALLING_ENABLED_DEFAULT
import com.example.xcpro.thermalling.THERMALLING_ENTER_DELAY_DEFAULT_SECONDS
import com.example.xcpro.thermalling.THERMALLING_EXIT_DELAY_DEFAULT_SECONDS
import com.example.xcpro.thermalling.THERMALLING_REMEMBER_MANUAL_ZOOM_IN_SESSION_DEFAULT
import com.example.xcpro.thermalling.THERMALLING_RESTORE_PREVIOUS_MODE_ON_EXIT_DEFAULT
import com.example.xcpro.thermalling.THERMALLING_RESTORE_PREVIOUS_ZOOM_ON_EXIT_DEFAULT
import com.example.xcpro.thermalling.THERMALLING_SWITCH_TO_THERMAL_MODE_DEFAULT
import com.example.xcpro.thermalling.THERMALLING_ZOOM_LEVEL_DEFAULT
import com.example.xcpro.thermalling.THERMALLING_ZOOM_ONLY_FALLBACK_WHEN_THERMAL_HIDDEN_DEFAULT

data class ThermallingSettingsUiState(
    val enabled: Boolean = THERMALLING_ENABLED_DEFAULT,
    val switchToThermalMode: Boolean = THERMALLING_SWITCH_TO_THERMAL_MODE_DEFAULT,
    val zoomOnlyFallbackWhenThermalHidden: Boolean =
        THERMALLING_ZOOM_ONLY_FALLBACK_WHEN_THERMAL_HIDDEN_DEFAULT,
    val enterDelaySeconds: Int = THERMALLING_ENTER_DELAY_DEFAULT_SECONDS,
    val exitDelaySeconds: Int = THERMALLING_EXIT_DELAY_DEFAULT_SECONDS,
    val applyZoomOnEnter: Boolean = THERMALLING_APPLY_ZOOM_ON_ENTER_DEFAULT,
    val applyContrastMapOnEnter: Boolean = THERMALLING_APPLY_CONTRAST_MAP_ON_ENTER_DEFAULT,
    val thermalZoomLevel: Float = THERMALLING_ZOOM_LEVEL_DEFAULT,
    val rememberManualThermalZoomInSession: Boolean =
        THERMALLING_REMEMBER_MANUAL_ZOOM_IN_SESSION_DEFAULT,
    val restorePreviousModeOnExit: Boolean = THERMALLING_RESTORE_PREVIOUS_MODE_ON_EXIT_DEFAULT,
    val restorePreviousZoomOnExit: Boolean = THERMALLING_RESTORE_PREVIOUS_ZOOM_ON_EXIT_DEFAULT
)
