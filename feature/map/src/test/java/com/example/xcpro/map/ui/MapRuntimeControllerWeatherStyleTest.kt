package com.example.xcpro.map.ui

import com.example.xcpro.map.MapCommand
import com.example.xcpro.map.MapOverlayManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class MapRuntimeControllerWeatherStyleTest {

    @Test
    fun applyStyle_beforeMapReady_replaysWhenMapBecomesReady() {
        val overlayManager: MapOverlayManager = mock()
        val map: MapLibreMap = mock()
        val callbacks = mutableListOf<Style.OnStyleLoaded>()
        val controller = MapRuntimeController(overlayManager)

        doAnswer { invocation ->
            callbacks += invocation.getArgument<Style.OnStyleLoaded>(1)
            null
        }.`when`(map).setStyle(any<String>(), any<Style.OnStyleLoaded>())

        controller.apply(MapCommand.SetStyle("Terrain"))
        verify(map, never()).setStyle(any<String>(), any<Style.OnStyleLoaded>())

        controller.onMapReady(map)
        verify(map, times(1)).setStyle(any<String>(), any<Style.OnStyleLoaded>())
        assertEquals(1, callbacks.size)

        callbacks.single().onStyleLoaded(mock())
        verify(overlayManager, times(1)).onMapStyleChanged(map)
    }

    @Test
    fun applyStyle_ignoresStaleCallback_andAppliesLatestOnly() {
        val overlayManager: MapOverlayManager = mock()
        val map: MapLibreMap = mock()
        val callbacks = mutableListOf<Style.OnStyleLoaded>()
        val controller = MapRuntimeController(overlayManager)

        doAnswer { invocation ->
            callbacks += invocation.getArgument<Style.OnStyleLoaded>(1)
            null
        }.`when`(map).setStyle(any<String>(), any<Style.OnStyleLoaded>())

        controller.onMapReady(map)
        controller.apply(MapCommand.SetStyle("Terrain"))
        controller.apply(MapCommand.SetStyle("Satellite"))

        assertEquals(2, callbacks.size)
        callbacks.first().onStyleLoaded(mock())
        verify(overlayManager, never()).onMapStyleChanged(anyOrNull())

        callbacks.last().onStyleLoaded(mock())
        verify(overlayManager, times(1)).onMapStyleChanged(map)
    }

    @Test
    fun clearMap_invalidatesInFlightStyleCallback() {
        val overlayManager: MapOverlayManager = mock()
        val map: MapLibreMap = mock()
        val callbacks = mutableListOf<Style.OnStyleLoaded>()
        val controller = MapRuntimeController(overlayManager)

        doAnswer { invocation ->
            callbacks += invocation.getArgument<Style.OnStyleLoaded>(1)
            null
        }.`when`(map).setStyle(any<String>(), any<Style.OnStyleLoaded>())

        controller.onMapReady(map)
        controller.apply(MapCommand.SetStyle("Terrain"))
        assertEquals(1, callbacks.size)

        controller.clearMap()
        callbacks.single().onStyleLoaded(mock())

        verify(overlayManager, never()).onMapStyleChanged(anyOrNull())
    }

    @Test
    fun applyFitCurrentTask_beforeMapReady_replaysWhenMapBecomesReady() {
        val overlayManager: MapOverlayManager = mock()
        val map: MapLibreMap = mock()
        var fitCount = 0
        val controller = MapRuntimeController(
            overlayManager = overlayManager,
            fitCurrentTask = { fitCount += 1 }
        )

        controller.apply(MapCommand.FitCurrentTask)
        assertEquals(0, fitCount)

        controller.onMapReady(map)

        assertEquals(1, fitCount)
    }

    @Test
    fun clearMap_dropsQueuedFitCurrentTask() {
        val overlayManager: MapOverlayManager = mock()
        val map: MapLibreMap = mock()
        var fitCount = 0
        val controller = MapRuntimeController(
            overlayManager = overlayManager,
            fitCurrentTask = { fitCount += 1 }
        )

        controller.apply(MapCommand.FitCurrentTask)
        controller.clearMap()
        controller.onMapReady(map)

        assertEquals(0, fitCount)
    }
}
