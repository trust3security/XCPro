package com.trust3.xcpro.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Layer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SkySightSatelliteOverlayRenderDiffTest {

    @Test
    fun shouldRebuildSourcesAndLayers_false_whenSignatureAndStyleAreUnchanged() {
        val overlay = SkySightSatelliteOverlay(map = mock<MapLibreMap>())
        val style: Style = mock()
        val frameEpochs = listOf(100L, 200L, 300L)

        setPrivateField(overlay, "lastRenderedFrameEpochs", frameEpochs)
        setPrivateField(overlay, "lastRenderedSatellite", true)
        setPrivateField(overlay, "lastRenderedRadar", false)
        setPrivateField(overlay, "lastRenderedLightning", false)
        setPrivateField(overlay, "lastStyleIdentity", 123)
        whenever(style.getLayer("skysight-sat-layer-0")).thenReturn(mock<Layer>())

        val shouldRebuild = invokeShouldRebuildSourcesAndLayers(
            overlay = overlay,
            style = style,
            frameEpochs = frameEpochs,
            showSatelliteImagery = true,
            showRadar = false,
            showLightning = false,
            styleIdentity = 123
        )

        assertFalse(shouldRebuild)
    }

    @Test
    fun shouldRebuildSourcesAndLayers_true_whenStyleIdentityChanges() {
        val overlay = SkySightSatelliteOverlay(map = mock<MapLibreMap>())
        val style: Style = mock()
        val frameEpochs = listOf(100L, 200L, 300L)

        setPrivateField(overlay, "lastRenderedFrameEpochs", frameEpochs)
        setPrivateField(overlay, "lastRenderedSatellite", true)
        setPrivateField(overlay, "lastRenderedRadar", false)
        setPrivateField(overlay, "lastRenderedLightning", false)
        setPrivateField(overlay, "lastStyleIdentity", 123)

        val shouldRebuild = invokeShouldRebuildSourcesAndLayers(
            overlay = overlay,
            style = style,
            frameEpochs = frameEpochs,
            showSatelliteImagery = true,
            showRadar = false,
            showLightning = false,
            styleIdentity = 456
        )

        assertTrue(shouldRebuild)
    }

    @Test
    fun addLayerBelowAnchors_prefersWindBarbLayerPrefixAnchor() {
        val overlay = SkySightSatelliteOverlay(map = mock<MapLibreMap>())
        val style: Style = mock()
        val windBarbLayer: Layer = mock()
        val insertedLayer: Layer = mock()
        whenever(windBarbLayer.id).thenReturn("forecast-wind-wind-barb-layer-7")
        whenever(style.layers).thenReturn(listOf(windBarbLayer))

        invokeAddLayerBelowAnchors(
            overlay = overlay,
            style = style,
            layer = insertedLayer
        )

        verify(style).addLayerBelow(insertedLayer, "forecast-wind-wind-barb-layer-7")
    }

    private fun invokeShouldRebuildSourcesAndLayers(
        overlay: SkySightSatelliteOverlay,
        style: Style,
        frameEpochs: List<Long>,
        showSatelliteImagery: Boolean,
        showRadar: Boolean,
        showLightning: Boolean,
        styleIdentity: Int
    ): Boolean {
        val method = SkySightSatelliteOverlay::class.java.getDeclaredMethod(
            "shouldRebuildSourcesAndLayers",
            Style::class.java,
            List::class.java,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true
        return method.invoke(
            overlay,
            style,
            frameEpochs,
            showSatelliteImagery,
            showRadar,
            showLightning,
            styleIdentity
        ) as Boolean
    }

    private fun setPrivateField(target: Any, name: String, value: Any?) {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }

    private fun invokeAddLayerBelowAnchors(
        overlay: SkySightSatelliteOverlay,
        style: Style,
        layer: Layer
    ) {
        val method = SkySightSatelliteOverlay::class.java.getDeclaredMethod(
            "addLayerBelowAnchors",
            Style::class.java,
            Layer::class.java
        )
        method.isAccessible = true
        method.invoke(overlay, style, layer)
    }
}
