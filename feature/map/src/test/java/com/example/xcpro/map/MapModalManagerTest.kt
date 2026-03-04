package com.example.xcpro.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapModalManagerTest {

    private fun createManager(): MapModalManager = MapModalManager(MapScreenState())

    @Test
    fun showGeneralSettingsModal_opensGeneralAndClosesAirspace() {
        val manager = createManager()
        manager.showAirspaceSettingsModal()

        manager.showGeneralSettingsModal()

        assertTrue(manager.showGeneralSettings.value)
        assertFalse(manager.showAirspaceSettings.value)
    }

    @Test
    fun showAirspaceSettingsModal_opensAirspaceAndClosesGeneral() {
        val manager = createManager()
        manager.showGeneralSettingsModal()

        manager.showAirspaceSettingsModal()

        assertTrue(manager.showAirspaceSettings.value)
        assertFalse(manager.showGeneralSettings.value)
    }

    @Test
    fun handleBackGesture_closesGeneralWhenGeneralIsVisible() {
        val manager = createManager()
        manager.showGeneralSettingsModal()

        val consumed = manager.handleBackGesture()

        assertTrue(consumed)
        assertFalse(manager.showGeneralSettings.value)
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
}
