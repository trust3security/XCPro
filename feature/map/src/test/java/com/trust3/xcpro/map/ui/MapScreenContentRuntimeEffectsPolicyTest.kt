package com.trust3.xcpro.map.ui

import com.trust3.xcpro.forecast.ForecastOverlayUiState
import com.trust3.xcpro.forecast.ForecastParameterId
import com.trust3.xcpro.forecast.ForecastTileSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapScreenContentRuntimeEffectsPolicyTest {

    @Test
    fun computeForecastOverlayRuntimeDispatch_rainViewerEnabled_suppressesSkySightRainPrimary() {
        val state = ForecastOverlayUiState(
            enabled = true,
            selectedPrimaryParameterId = ForecastParameterId("accrain"),
            primaryTileSpec = tileSpec("https://tiles.example/accrain/{z}/{x}/{y}.pbf")
        )

        val dispatch = computeForecastOverlayRuntimeDispatch(
            forecastOverlayState = state,
            rainViewerEnabled = true
        )

        assertTrue(dispatch.shouldClear)
        assertNull(dispatch.setConfig)
        assertEquals(SKY_SIGHT_RAIN_ARBITRATION_WARNING, dispatch.arbitrationWarningMessage)
    }

    @Test
    fun computeForecastOverlayRuntimeDispatch_rainViewerDisabled_keepsSkySightRainPrimary() {
        val state = ForecastOverlayUiState(
            enabled = true,
            selectedPrimaryParameterId = ForecastParameterId("accrain"),
            primaryTileSpec = tileSpec("https://tiles.example/accrain/{z}/{x}/{y}.pbf")
        )

        val dispatch = computeForecastOverlayRuntimeDispatch(
            forecastOverlayState = state,
            rainViewerEnabled = false
        )

        assertFalse(dispatch.shouldClear)
        assertNotNull(dispatch.setConfig)
        assertTrue(dispatch.setConfig!!.enabled)
        assertNull(dispatch.arbitrationWarningMessage)
    }

    @Test
    fun computeForecastOverlayRuntimeDispatch_loadingWithoutTiles_doesNotClearOverlay() {
        val state = ForecastOverlayUiState(
            enabled = true,
            selectedPrimaryParameterId = ForecastParameterId("wstar_bsratio"),
            primaryTileSpec = null,
            isLoading = true
        )

        val dispatch = computeForecastOverlayRuntimeDispatch(
            forecastOverlayState = state,
            rainViewerEnabled = false
        )

        assertFalse(dispatch.shouldClear)
        assertNull(dispatch.setConfig)
    }

    @Test
    fun computeForecastOverlayRuntimeDispatch_notLoadingWithoutTiles_clearsOverlay() {
        val state = ForecastOverlayUiState(
            enabled = true,
            selectedPrimaryParameterId = ForecastParameterId("wstar_bsratio"),
            primaryTileSpec = null,
            isLoading = false
        )

        val dispatch = computeForecastOverlayRuntimeDispatch(
            forecastOverlayState = state,
            rainViewerEnabled = false
        )

        assertTrue(dispatch.shouldClear)
        assertNull(dispatch.setConfig)
    }

    @Test
    fun computeForecastOverlayRuntimeDispatch_rainSuppressed_keepsWindOverlayActive() {
        val state = ForecastOverlayUiState(
            enabled = true,
            selectedPrimaryParameterId = ForecastParameterId("accrain"),
            primaryTileSpec = tileSpec("https://tiles.example/accrain/{z}/{x}/{y}.pbf"),
            windOverlayEnabled = true,
            windTileSpec = tileSpec("https://tiles.example/wind/{z}/{x}/{y}.pbf")
        )

        val dispatch = computeForecastOverlayRuntimeDispatch(
            forecastOverlayState = state,
            rainViewerEnabled = true
        )

        assertFalse(dispatch.shouldClear)
        assertNotNull(dispatch.setConfig)
        assertFalse(dispatch.setConfig!!.enabled)
        assertTrue(dispatch.setConfig!!.windOverlayEnabled)
        assertNotNull(dispatch.setConfig!!.windTileSpec)
    }

    @Test
    fun computeSkySightRainSuppressionWarning_returnsNullForNonRainParameter() {
        val state = ForecastOverlayUiState(
            enabled = true,
            selectedPrimaryParameterId = ForecastParameterId("zsfclcl"),
            primaryTileSpec = tileSpec("https://tiles.example/zsfclcl/{z}/{x}/{y}.pbf")
        )

        val warning = computeSkySightRainSuppressionWarning(
            forecastOverlayState = state,
            rainViewerEnabled = true
        )

        assertNull(warning)
    }

    private fun tileSpec(urlTemplate: String): ForecastTileSpec = ForecastTileSpec(
        urlTemplate = urlTemplate,
        minZoom = 3,
        maxZoom = 5
    )
}
