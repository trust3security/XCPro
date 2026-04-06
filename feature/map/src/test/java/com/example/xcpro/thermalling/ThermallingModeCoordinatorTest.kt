package com.example.xcpro.thermalling

import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.core.time.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermallingModeCoordinatorTest {

    @Test
    fun enterDelay_respected_and_actions_fire_once() {
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val coordinator = ThermallingModeCoordinator(clock)
        val settings = enabledSettings(enterDelaySeconds = 2, exitDelaySeconds = 8)

        val first = coordinator.update(
            input(
                isCircling = true,
                settings = settings,
                currentMode = FlightMode.CRUISE,
                currentZoom = 10f,
                thermalModeVisible = true
            )
        )
        assertTrue(first.isEmpty())
        assertEquals(ThermallingModePhase.ENTER_PENDING, coordinator.state().phase)

        clock.advanceMonoMs(1_500L)
        val second = coordinator.update(
            input(
                isCircling = true,
                settings = settings,
                currentMode = FlightMode.CRUISE,
                currentZoom = 10f,
                thermalModeVisible = true
            )
        )
        assertTrue(second.isEmpty())
        assertEquals(ThermallingModePhase.ENTER_PENDING, coordinator.state().phase)

        clock.advanceMonoMs(500L)
        val third = coordinator.update(
            input(
                isCircling = true,
                settings = settings,
                currentMode = FlightMode.CRUISE,
                currentZoom = 10f,
                thermalModeVisible = true
            )
        )
        assertEquals(
            listOf(
                ThermallingModeAction.SwitchFlightMode(FlightMode.THERMAL),
                ThermallingModeAction.SetZoom(13.0f)
            ),
            third
        )
        assertEquals(ThermallingModePhase.ACTIVE, coordinator.state().phase)

        val idempotent = coordinator.update(
            input(
                isCircling = true,
                settings = settings,
                currentMode = FlightMode.THERMAL,
                currentZoom = 13.0f,
                thermalModeVisible = true
            )
        )
        assertTrue(idempotent.isEmpty())
        assertEquals(ThermallingModePhase.ACTIVE, coordinator.state().phase)
    }

    @Test
    fun enterPending_breakBeforeThreshold_returnsIdle_withoutActions() {
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val coordinator = ThermallingModeCoordinator(clock)
        val settings = enabledSettings(enterDelaySeconds = 3)

        coordinator.update(
            input(
                isCircling = true,
                settings = settings,
                currentMode = FlightMode.CRUISE,
                currentZoom = 10f,
                thermalModeVisible = true
            )
        )
        clock.advanceMonoMs(1_000L)

        val actions = coordinator.update(
            input(
                isCircling = false,
                settings = settings,
                currentMode = FlightMode.CRUISE,
                currentZoom = 10f,
                thermalModeVisible = true
            )
        )
        assertTrue(actions.isEmpty())
        assertEquals(ThermallingModePhase.IDLE, coordinator.state().phase)
        assertNull(coordinator.state().sessionSnapshot)
    }

    @Test
    fun exitDelay_respected_and_restore_happens_once() {
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val coordinator = ThermallingModeCoordinator(clock)
        val settings = enabledSettings(enterDelaySeconds = 0, exitDelaySeconds = 4)

        val enter = coordinator.update(
            input(
                isCircling = true,
                settings = settings,
                currentMode = FlightMode.CRUISE,
                currentZoom = 11f,
                thermalModeVisible = true
            )
        )
        assertEquals(
            listOf(
                ThermallingModeAction.SwitchFlightMode(FlightMode.THERMAL),
                ThermallingModeAction.SetZoom(13.0f)
            ),
            enter
        )
        assertEquals(ThermallingModePhase.ACTIVE, coordinator.state().phase)

        val startExit = coordinator.update(
            input(
                isCircling = false,
                settings = settings,
                currentMode = FlightMode.THERMAL,
                currentZoom = 13f,
                thermalModeVisible = true
            )
        )
        assertTrue(startExit.isEmpty())
        assertEquals(ThermallingModePhase.EXIT_PENDING, coordinator.state().phase)

        clock.advanceMonoMs(3_900L)
        val tooSoon = coordinator.update(
            input(
                isCircling = false,
                settings = settings,
                currentMode = FlightMode.THERMAL,
                currentZoom = 13f,
                thermalModeVisible = true
            )
        )
        assertTrue(tooSoon.isEmpty())
        assertEquals(ThermallingModePhase.EXIT_PENDING, coordinator.state().phase)

        clock.advanceMonoMs(100L)
        val restore = coordinator.update(
            input(
                isCircling = false,
                settings = settings,
                currentMode = FlightMode.THERMAL,
                currentZoom = 13f,
                thermalModeVisible = true
            )
        )
        assertEquals(
            listOf(
                ThermallingModeAction.SwitchFlightMode(FlightMode.CRUISE),
                ThermallingModeAction.SetZoom(11.0f)
            ),
            restore
        )
        assertEquals(ThermallingModePhase.IDLE, coordinator.state().phase)
        assertNull(coordinator.state().sessionSnapshot)

        val idempotent = coordinator.update(
            input(
                isCircling = false,
                settings = settings,
                currentMode = FlightMode.CRUISE,
                currentZoom = 11f,
                thermalModeVisible = true
            )
        )
        assertTrue(idempotent.isEmpty())
    }

    @Test
    fun exitPending_resumeCircling_returnsToActive_withoutRestore() {
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val coordinator = ThermallingModeCoordinator(clock)
        val settings = enabledSettings(enterDelaySeconds = 0, exitDelaySeconds = 8)

        coordinator.update(
            input(
                isCircling = true,
                settings = settings,
                currentMode = FlightMode.CRUISE,
                currentZoom = 10f,
                thermalModeVisible = true
            )
        )
        coordinator.update(
            input(
                isCircling = false,
                settings = settings,
                currentMode = FlightMode.THERMAL,
                currentZoom = 13f,
                thermalModeVisible = true
            )
        )
        assertEquals(ThermallingModePhase.EXIT_PENDING, coordinator.state().phase)

        val resume = coordinator.update(
            input(
                isCircling = true,
                settings = settings,
                currentMode = FlightMode.THERMAL,
                currentZoom = 13f,
                thermalModeVisible = true
            )
        )
        assertTrue(resume.isEmpty())
        assertEquals(ThermallingModePhase.ACTIVE, coordinator.state().phase)
        assertEquals(FlightMode.CRUISE, coordinator.state().sessionSnapshot?.preThermalMode)
    }

    @Test
    fun thermalHidden_withFallback_runsZoomOnly_withoutModeSwitch() {
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val coordinator = ThermallingModeCoordinator(clock)
        val settings = enabledSettings(
            enterDelaySeconds = 0,
            switchToThermalMode = true,
            applyZoomOnEnter = true,
            zoomOnlyFallbackWhenThermalHidden = true
        )

        val actions = coordinator.update(
            input(
                isCircling = true,
                settings = settings,
                currentMode = FlightMode.CRUISE,
                currentZoom = 9f,
                thermalModeVisible = false
            )
        )
        assertEquals(listOf(ThermallingModeAction.SetZoom(13.0f)), actions)
        assertEquals(ThermallingModePhase.ACTIVE, coordinator.state().phase)
    }

    @Test
    fun settingsDisabled_midSession_restoresAndResets() {
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val coordinator = ThermallingModeCoordinator(clock)
        val activeSettings = enabledSettings(enterDelaySeconds = 0, exitDelaySeconds = 8)

        coordinator.update(
            input(
                isCircling = true,
                settings = activeSettings,
                currentMode = FlightMode.CRUISE,
                currentZoom = 12f,
                thermalModeVisible = true
            )
        )
        assertEquals(ThermallingModePhase.ACTIVE, coordinator.state().phase)

        val disabled = activeSettings.copy(enabled = false)
        val resetActions = coordinator.update(
            input(
                isCircling = true,
                settings = disabled,
                currentMode = FlightMode.THERMAL,
                currentZoom = 13f,
                thermalModeVisible = true
            )
        )
        assertEquals(
            listOf(
                ThermallingModeAction.SwitchFlightMode(FlightMode.CRUISE),
                ThermallingModeAction.SetZoom(12.0f)
            ),
            resetActions
        )
        assertEquals(ThermallingModePhase.IDLE, coordinator.state().phase)
    }

    @Test
    fun rememberManualZoom_updatesSessionAndUsedForReentryWithinSession() {
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val coordinator = ThermallingModeCoordinator(clock)
        val settings = enabledSettings(
            enterDelaySeconds = 0,
            exitDelaySeconds = 8,
            rememberManualThermalZoomInSession = true
        )

        coordinator.update(
            input(
                isCircling = true,
                settings = settings,
                currentMode = FlightMode.CRUISE,
                currentZoom = 10f,
                thermalModeVisible = true
            )
        )
        coordinator.onUserZoomChanged(14.7f, settings)
        assertEquals(14.7f, coordinator.state().sessionSnapshot?.activeThermalZoom)

        coordinator.update(
            input(
                isCircling = false,
                settings = settings,
                currentMode = FlightMode.THERMAL,
                currentZoom = 14.7f,
                thermalModeVisible = true
            )
        )
        val resume = coordinator.update(
            input(
                isCircling = true,
                settings = settings,
                currentMode = FlightMode.THERMAL,
                currentZoom = 14.7f,
                thermalModeVisible = true
            )
        )
        assertTrue(resume.isEmpty())
        assertEquals(ThermallingModePhase.ACTIVE, coordinator.state().phase)
    }

    @Test
    fun contrastMap_settingAddsEnterAndExitActions() {
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val coordinator = ThermallingModeCoordinator(clock)
        val settings = enabledSettings(
            enterDelaySeconds = 0,
            exitDelaySeconds = 0,
            applyContrastMapOnEnter = true
        )

        val enter = coordinator.update(
            input(
                isCircling = true,
                settings = settings,
                currentMode = FlightMode.CRUISE,
                currentZoom = 10f,
                thermalModeVisible = true
            )
        )
        assertEquals(
            listOf(
                ThermallingModeAction.SwitchFlightMode(FlightMode.THERMAL),
                ThermallingModeAction.SetZoom(13.0f),
                ThermallingModeAction.SetContrastMapEnabled(true)
            ),
            enter
        )

        val exit = coordinator.update(
            input(
                isCircling = false,
                settings = settings,
                currentMode = FlightMode.THERMAL,
                currentZoom = 13f,
                thermalModeVisible = true
            )
        )
        assertEquals(
            listOf(
                ThermallingModeAction.SwitchFlightMode(FlightMode.CRUISE),
                ThermallingModeAction.SetZoom(10.0f),
                ThermallingModeAction.SetContrastMapEnabled(false)
            ),
            exit
        )
    }

    private fun input(
        isCircling: Boolean,
        settings: ThermallingModeSettings,
        currentMode: FlightMode,
        currentZoom: Float,
        thermalModeVisible: Boolean
    ): ThermallingModeInput = ThermallingModeInput(
        isCircling = isCircling,
        settings = settings,
        thermalModeVisible = thermalModeVisible,
        currentMode = currentMode,
        currentZoom = currentZoom
    )

    private fun enabledSettings(
        enterDelaySeconds: Int = 5,
        exitDelaySeconds: Int = 8,
        switchToThermalMode: Boolean = true,
        applyZoomOnEnter: Boolean = true,
        applyContrastMapOnEnter: Boolean = false,
        zoomOnlyFallbackWhenThermalHidden: Boolean = true,
        rememberManualThermalZoomInSession: Boolean = true
    ): ThermallingModeSettings = ThermallingModeSettings(
        enabled = true,
        switchToThermalMode = switchToThermalMode,
        zoomOnlyFallbackWhenThermalHidden = zoomOnlyFallbackWhenThermalHidden,
        enterDelaySeconds = enterDelaySeconds,
        exitDelaySeconds = exitDelaySeconds,
        applyZoomOnEnter = applyZoomOnEnter,
        applyContrastMapOnEnter = applyContrastMapOnEnter,
        thermalZoomLevel = 13.0f,
        rememberManualThermalZoomInSession = rememberManualThermalZoomInSession,
        restorePreviousModeOnExit = true,
        restorePreviousZoomOnExit = true
    )
}
