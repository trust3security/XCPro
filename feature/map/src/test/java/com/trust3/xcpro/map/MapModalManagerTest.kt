package com.trust3.xcpro.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapModalManagerTest {

    private fun createManager(): MapModalManager = MapModalManager(MapScreenState())

    @Test
    fun showAirspaceSettingsModal_opensAirspace() {
        val manager = createManager()

        manager.showAirspaceSettingsModal()

        assertTrue(manager.showAirspaceSettings.value)
    }

    @Test
    fun handleBackGesture_closesAirspaceWhenAirspaceIsVisible() {
        val manager = createManager()
        manager.showAirspaceSettingsModal()

        val consumed = manager.handleBackGesture()

        assertTrue(consumed)
        assertFalse(manager.showAirspaceSettings.value)
    }

    @Test
    fun handleBackGesture_returnsFalseWhenNoModalIsVisible() {
        val manager = createManager()

        val consumed = manager.handleBackGesture()

        assertFalse(consumed)
        assertFalse(manager.isAnyModalOpen())
    }

    @Test
    fun isAnyModalOpen_returnsTrueWhenAirspaceVisible() {
        val manager = createManager()

        manager.showAirspaceSettingsModal()

        assertTrue(manager.isAnyModalOpen())
    }
}
