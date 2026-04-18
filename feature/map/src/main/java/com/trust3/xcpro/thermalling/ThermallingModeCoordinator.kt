package com.trust3.xcpro.thermalling

import com.trust3.xcpro.common.flight.FlightMode
import com.trust3.xcpro.core.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThermallingModeCoordinator @Inject constructor(
    private val clock: Clock
) {
    private var state = ThermallingModeState()

    fun state(): ThermallingModeState = state

    fun reset() {
        state = ThermallingModeState()
    }

    fun onUserZoomChanged(currentZoom: Float, settings: ThermallingModeSettings) {
        if (!settings.rememberManualThermalZoomInSession) return
        val snapshot = state.sessionSnapshot ?: return
        if (state.phase != ThermallingModePhase.ACTIVE) return

        state = state.copy(
            sessionSnapshot = snapshot.copy(
                activeThermalZoom = clampThermallingZoomLevel(currentZoom)
            )
        )
    }

    fun update(input: ThermallingModeInput): List<ThermallingModeAction> {
        val nowMonoMs = clock.nowMonoMs()
        val policy = resolveThermallingModePolicy(
            settings = input.settings,
            thermalModeVisible = input.thermalModeVisible,
            hasActiveThermallingSession = state.sessionSnapshot != null
        )

        if (policy.shouldBypassAutomation) {
            return handleBypass(input)
        }

        return when (state.phase) {
            ThermallingModePhase.IDLE -> onIdle(input, policy, nowMonoMs)
            ThermallingModePhase.ENTER_PENDING -> onEnterPending(input, policy, nowMonoMs)
            ThermallingModePhase.ACTIVE -> onActive(input)
            ThermallingModePhase.EXIT_PENDING -> onExitPending(input, nowMonoMs)
        }
    }

    private fun onIdle(
        input: ThermallingModeInput,
        policy: ThermallingModePolicyDecision,
        nowMonoMs: Long
    ): List<ThermallingModeAction> {
        if (!input.isCircling) return emptyList()

        val enterDelayMs = input.settings.enterDelaySeconds * 1_000L
        if (enterDelayMs <= 0L) {
            return activate(input, policy)
        }

        state = ThermallingModeState(
            phase = ThermallingModePhase.ENTER_PENDING,
            enterPendingStartMonoMs = nowMonoMs,
            exitPendingStartMonoMs = null,
            sessionSnapshot = null
        )
        return emptyList()
    }

    private fun onEnterPending(
        input: ThermallingModeInput,
        policy: ThermallingModePolicyDecision,
        nowMonoMs: Long
    ): List<ThermallingModeAction> {
        val startedAt = state.enterPendingStartMonoMs ?: nowMonoMs
        if (!input.isCircling) {
            state = ThermallingModeState()
            return emptyList()
        }

        val enterDelayMs = input.settings.enterDelaySeconds * 1_000L
        val elapsedMs = nowMonoMs - startedAt
        if (elapsedMs < enterDelayMs) return emptyList()
        return activate(input, policy)
    }

    private fun onActive(input: ThermallingModeInput): List<ThermallingModeAction> {
        if (input.isCircling) return emptyList()

        val exitDelayMs = input.settings.exitDelaySeconds * 1_000L
        if (exitDelayMs <= 0L) {
            return restoreAndReset(input)
        }

        state = state.copy(
            phase = ThermallingModePhase.EXIT_PENDING,
            enterPendingStartMonoMs = null,
            exitPendingStartMonoMs = clock.nowMonoMs()
        )
        return emptyList()
    }

    private fun onExitPending(
        input: ThermallingModeInput,
        nowMonoMs: Long
    ): List<ThermallingModeAction> {
        if (input.isCircling) {
            state = state.copy(
                phase = ThermallingModePhase.ACTIVE,
                enterPendingStartMonoMs = null,
                exitPendingStartMonoMs = null
            )
            return emptyList()
        }

        val startedAt = state.exitPendingStartMonoMs ?: nowMonoMs
        val exitDelayMs = input.settings.exitDelaySeconds * 1_000L
        val elapsedMs = nowMonoMs - startedAt
        if (elapsedMs < exitDelayMs) return emptyList()
        return restoreAndReset(input)
    }

    private fun activate(
        input: ThermallingModeInput,
        policy: ThermallingModePolicyDecision
    ): List<ThermallingModeAction> {
        val currentZoom = clampThermallingZoomLevel(input.currentZoom)
        val session = ThermallingModeSessionSnapshot(
            preThermalMode = input.currentMode,
            preThermalZoom = currentZoom,
            activeThermalZoom = null,
            contrastMapApplied = input.settings.applyContrastMapOnEnter
        )
        val actions = mutableListOf<ThermallingModeAction>()

        if (policy.shouldSwitchToThermalMode && input.currentMode != FlightMode.THERMAL) {
            actions += ThermallingModeAction.SwitchFlightMode(FlightMode.THERMAL)
        }

        if (policy.shouldApplyZoom) {
            val targetZoom = session.activeThermalZoom ?: input.settings.thermalZoomLevel
            val clampedTarget = clampThermallingZoomLevel(targetZoom)
            if (input.currentZoom != clampedTarget) {
                actions += ThermallingModeAction.SetZoom(clampedTarget)
            }
        }

        if (session.contrastMapApplied) {
            actions += ThermallingModeAction.SetContrastMapEnabled(true)
        }

        state = ThermallingModeState(
            phase = ThermallingModePhase.ACTIVE,
            enterPendingStartMonoMs = null,
            exitPendingStartMonoMs = null,
            sessionSnapshot = session
        )
        return actions
    }

    private fun handleBypass(input: ThermallingModeInput): List<ThermallingModeAction> {
        if (state.sessionSnapshot == null) {
            state = ThermallingModeState()
            return emptyList()
        }

        return restoreAndReset(input)
    }

    private fun restoreAndReset(input: ThermallingModeInput): List<ThermallingModeAction> {
        val snapshot = state.sessionSnapshot
        val actions = mutableListOf<ThermallingModeAction>()
        if (snapshot != null) {
            if (input.settings.restorePreviousModeOnExit && input.currentMode != snapshot.preThermalMode) {
                actions += ThermallingModeAction.SwitchFlightMode(snapshot.preThermalMode)
            }
            if (input.settings.restorePreviousZoomOnExit && input.currentZoom != snapshot.preThermalZoom) {
                actions += ThermallingModeAction.SetZoom(snapshot.preThermalZoom)
            }
            if (snapshot.contrastMapApplied) {
                actions += ThermallingModeAction.SetContrastMapEnabled(false)
            }
        }
        state = ThermallingModeState()
        return actions
    }
}
