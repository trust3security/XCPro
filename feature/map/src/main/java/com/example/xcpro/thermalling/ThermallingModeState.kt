package com.example.xcpro.thermalling

enum class ThermallingModePhase {
    IDLE,
    ENTER_PENDING,
    ACTIVE,
    EXIT_PENDING
}

data class ThermallingModeSessionSnapshot(
    val preThermalMode: com.example.xcpro.common.flight.FlightMode,
    val preThermalZoom: Float,
    val activeThermalZoom: Float?,
    val contrastMapApplied: Boolean
)

data class ThermallingModeState(
    val phase: ThermallingModePhase = ThermallingModePhase.IDLE,
    val enterPendingStartMonoMs: Long? = null,
    val exitPendingStartMonoMs: Long? = null,
    val sessionSnapshot: ThermallingModeSessionSnapshot? = null
)

data class ThermallingModeInput(
    val isCircling: Boolean,
    val settings: ThermallingModeSettings,
    val thermalModeVisible: Boolean,
    val currentMode: com.example.xcpro.common.flight.FlightMode,
    val currentZoom: Float
)

sealed interface ThermallingModeAction {
    data class SwitchFlightMode(val mode: com.example.xcpro.common.flight.FlightMode) :
        ThermallingModeAction

    data class SetZoom(val zoom: Float) : ThermallingModeAction

    data class SetContrastMapEnabled(val enabled: Boolean) : ThermallingModeAction
}
