package com.example.xcpro

import com.example.xcpro.airspace.AirspaceUseCase
import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AirspaceApplyTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadAndApplyAirspace_noEnabledFiles_clearsExistingOverlay() = runTest {
        val useCase: AirspaceUseCase = mock()
        val map: MapLibreMap = mock()
        val style: Style = mock()

        whenever(useCase.loadAirspaceFiles()).thenReturn(
            Pair(
                listOf(DocumentRef(uri = "file:///tmp/a.txt", displayName = "a.txt")),
                mutableMapOf("a.txt" to false)
            )
        )
        whenever(useCase.loadSelectedClasses()).thenReturn(mutableMapOf("D" to true))
        whenever(map.style).thenReturn(style)

        loadAndApplyAirspace(map, useCase)

        verify(style, times(1)).removeLayer("airspace-layer")
        verify(style, times(1)).removeSource("airspace-source")
        verify(useCase, never()).buildGeoJson(any(), any())
    }

    @Test
    fun loadAndApplyAirspace_allClassesOff_clearsOverlayWithoutRendering() = runTest {
        val useCase: AirspaceUseCase = mock()
        val map: MapLibreMap = mock()
        val style: Style = mock()
        val document = DocumentRef(uri = "file:///tmp/a.txt", displayName = "a.txt")

        whenever(useCase.loadAirspaceFiles()).thenReturn(
            Pair(listOf(document), mutableMapOf("a.txt" to true))
        )
        whenever(useCase.loadSelectedClasses()).thenReturn(mutableMapOf("D" to false))
        whenever(map.style).thenReturn(style)

        loadAndApplyAirspace(map, useCase)

        verify(style, times(1)).removeLayer("airspace-layer")
        verify(style, times(1)).removeSource("airspace-source")
        verify(useCase, never()).buildGeoJson(any(), any())
    }

    @Test
    fun loadAndApplyAirspace_noStoredClassState_usesDefaultsWithoutPersisting() = runTest {
        val useCase: AirspaceUseCase = mock()
        val map: MapLibreMap = mock()
        val style: Style = mock()
        val document = DocumentRef(uri = "file:///tmp/a.txt", displayName = "a.txt")

        whenever(useCase.loadAirspaceFiles()).thenReturn(
            Pair(listOf(document), mutableMapOf("a.txt" to true))
        )
        whenever(useCase.loadSelectedClasses()).thenReturn(null)
        whenever(useCase.parseClasses(eq(listOf(document)))).thenReturn(listOf("D"))
        whenever(useCase.buildGeoJson(eq(listOf(document)), eq(setOf("D")))).thenReturn(
            """{"type":"FeatureCollection","features":[]}"""
        )
        whenever(map.style).thenReturn(style)

        loadAndApplyAirspace(map, useCase)

        verify(useCase, times(1)).buildGeoJson(eq(listOf(document)), eq(setOf("D")))
        verify(useCase, never()).saveSelectedClasses(any())
    }
}
