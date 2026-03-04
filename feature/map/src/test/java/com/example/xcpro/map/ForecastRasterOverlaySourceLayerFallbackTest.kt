package com.example.xcpro.map

import android.graphics.PointF
import com.example.xcpro.forecast.ForecastLegendSpec
import com.example.xcpro.forecast.ForecastLegendStop
import com.example.xcpro.forecast.ForecastTileFormat
import com.example.xcpro.forecast.ForecastTileSpec
import com.example.xcpro.forecast.ForecastWindDisplayMode
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Projection
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.VectorSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ForecastRasterOverlaySourceLayerFallbackTest {

    @Test
    fun vectorFill_afterConsecutiveMisses_advancesToNextSourceLayerCandidate() {
        val map: MapLibreMap = mock()
        val style: Style = mock()
        val projection: Projection = mock()
        val fillLayer: FillLayer = mock()
        whenever(map.style).thenReturn(style)
        whenever(map.projection).thenReturn(projection)
        whenever(map.cameraPosition).thenReturn(
            CameraPosition.Builder()
                .target(LatLng(0.0, 0.0))
                .zoom(5.0)
                .build()
        )
        whenever(projection.toScreenLocation(any())).thenReturn(PointF(100f, 100f))
        whenever(style.getSource(any())).thenReturn(mock<VectorSource>())
        whenever(style.getLayer(any())).thenAnswer { invocation ->
            when (invocation.getArgument<String>(0)) {
                "forecast-primary-vector-fill-layer" -> fillLayer
                else -> null
            }
        }
        whenever(
            map.queryRenderedFeatures(
                any<PointF>(),
                eq("forecast-primary-vector-fill-layer")
            )
        ).thenReturn(emptyList())

        val overlay = ForecastRasterOverlay(map = map, idNamespace = "primary")
        val tileSpec = ForecastTileSpec(
            urlTemplate = "https://tiles.example/{z}/{x}/{y}.pbf",
            minZoom = 3,
            maxZoom = 5,
            format = ForecastTileFormat.VECTOR_INDEXED_FILL,
            sourceLayer = "bsratio",
            sourceLayerCandidates = listOf("bsratio", "1200"),
            valueProperty = "idx"
        )
        val legend = ForecastLegendSpec(
            unitLabel = "m/s",
            stops = listOf(
                ForecastLegendStop(value = 0.0, argb = 0xFF000000.toInt()),
                ForecastLegendStop(value = 1.0, argb = 0xFFFFFFFF.toInt())
            )
        )

        mockConstruction(VectorSource::class.java).use {
            overlay.render(
                tileSpec = tileSpec,
                opacity = 0.7f,
                windOverlayScale = 1.0f,
                windDisplayMode = ForecastWindDisplayMode.ARROW,
                legendSpec = legend
            )
            overlay.render(
                tileSpec = tileSpec,
                opacity = 0.7f,
                windOverlayScale = 1.0f,
                windDisplayMode = ForecastWindDisplayMode.ARROW,
                legendSpec = legend
            )
        }

        verify(fillLayer, atLeastOnce()).setSourceLayer("1200")
        assertEquals(
            "Forecast primary overlay source-layer fallback engaged ('1200').",
            overlay.runtimeWarningMessage()
        )
    }

    @Test
    fun vectorFill_missThenFeatureLoad_keepsPrimarySourceLayerAndNoWarning() {
        val map: MapLibreMap = mock()
        val style: Style = mock()
        val projection: Projection = mock()
        val fillLayer: FillLayer = mock()
        whenever(map.style).thenReturn(style)
        whenever(map.projection).thenReturn(projection)
        whenever(map.cameraPosition).thenReturn(
            CameraPosition.Builder()
                .target(LatLng(0.0, 0.0))
                .zoom(5.0)
                .build()
        )
        whenever(projection.toScreenLocation(any())).thenReturn(PointF(100f, 100f))
        whenever(style.getSource(any())).thenReturn(mock<VectorSource>())
        whenever(style.getLayer(any())).thenAnswer { invocation ->
            when (invocation.getArgument<String>(0)) {
                "forecast-primary-vector-fill-layer" -> fillLayer
                else -> null
            }
        }
        var probeCalls = 0
        whenever(
            map.queryRenderedFeatures(
                any<PointF>(),
                eq("forecast-primary-vector-fill-layer")
            )
        ).thenAnswer {
            probeCalls += 1
            if (probeCalls <= 5) {
                emptyList<Feature>()
            } else {
                listOf(Feature.fromGeometry(Point.fromLngLat(0.0, 0.0)))
            }
        }

        val overlay = ForecastRasterOverlay(map = map, idNamespace = "primary")
        val tileSpec = ForecastTileSpec(
            urlTemplate = "https://tiles.example/{z}/{x}/{y}.pbf",
            minZoom = 3,
            maxZoom = 5,
            format = ForecastTileFormat.VECTOR_INDEXED_FILL,
            sourceLayer = "bsratio",
            sourceLayerCandidates = listOf("bsratio", "1200"),
            valueProperty = "idx"
        )
        val legend = ForecastLegendSpec(
            unitLabel = "m/s",
            stops = listOf(
                ForecastLegendStop(value = 0.0, argb = 0xFF000000.toInt()),
                ForecastLegendStop(value = 1.0, argb = 0xFFFFFFFF.toInt())
            )
        )

        mockConstruction(VectorSource::class.java).use {
            overlay.render(
                tileSpec = tileSpec,
                opacity = 0.7f,
                windOverlayScale = 1.0f,
                windDisplayMode = ForecastWindDisplayMode.ARROW,
                legendSpec = legend
            )
            overlay.render(
                tileSpec = tileSpec,
                opacity = 0.7f,
                windOverlayScale = 1.0f,
                windDisplayMode = ForecastWindDisplayMode.ARROW,
                legendSpec = legend
            )
        }

        verify(fillLayer, never()).setSourceLayer("1200")
        assertNull(overlay.runtimeWarningMessage())
    }

    @Test
    fun vectorFill_fallbackExhausted_setsExhaustedWarning() {
        val map: MapLibreMap = mock()
        val style: Style = mock()
        val projection: Projection = mock()
        val fillLayer: FillLayer = mock()
        whenever(map.style).thenReturn(style)
        whenever(map.projection).thenReturn(projection)
        whenever(map.cameraPosition).thenReturn(
            CameraPosition.Builder()
                .target(LatLng(0.0, 0.0))
                .zoom(5.0)
                .build()
        )
        whenever(projection.toScreenLocation(any())).thenReturn(PointF(100f, 100f))
        whenever(style.getSource(any())).thenReturn(mock<VectorSource>())
        whenever(style.getLayer(any())).thenAnswer { invocation ->
            when (invocation.getArgument<String>(0)) {
                "forecast-primary-vector-fill-layer" -> fillLayer
                else -> null
            }
        }
        whenever(
            map.queryRenderedFeatures(
                any<PointF>(),
                eq("forecast-primary-vector-fill-layer")
            )
        ).thenReturn(emptyList())

        val overlay = ForecastRasterOverlay(map = map, idNamespace = "primary")
        val tileSpec = ForecastTileSpec(
            urlTemplate = "https://tiles.example/{z}/{x}/{y}.pbf",
            minZoom = 3,
            maxZoom = 5,
            format = ForecastTileFormat.VECTOR_INDEXED_FILL,
            sourceLayer = "bsratio",
            sourceLayerCandidates = listOf("bsratio", "1200"),
            valueProperty = "idx"
        )
        val legend = ForecastLegendSpec(
            unitLabel = "m/s",
            stops = listOf(
                ForecastLegendStop(value = 0.0, argb = 0xFF000000.toInt()),
                ForecastLegendStop(value = 1.0, argb = 0xFFFFFFFF.toInt())
            )
        )

        mockConstruction(VectorSource::class.java).use {
            repeat(4) {
                overlay.render(
                    tileSpec = tileSpec,
                    opacity = 0.7f,
                    windOverlayScale = 1.0f,
                    windDisplayMode = ForecastWindDisplayMode.ARROW,
                    legendSpec = legend
                )
            }
        }

        verify(fillLayer, atLeastOnce()).setSourceLayer("1200")
        assertEquals(
            "Forecast primary overlay source-layer fallback exhausted (bsratio, 1200).",
            overlay.runtimeWarningMessage()
        )
    }

    @Test
    fun windArrow_existingLayer_isReinsertedToPreserveZOrder() {
        val map: MapLibreMap = mock()
        val style: Style = mock()
        val symbolLayer: SymbolLayer = mock()
        whenever(map.style).thenReturn(style)
        whenever(style.getSource(any())).thenReturn(mock<VectorSource>())
        whenever(style.getImage(any())).thenReturn(null)
        whenever(style.getLayer(any())).thenAnswer { invocation ->
            when (invocation.getArgument<String>(0)) {
                "forecast-wind-wind-symbol-layer" -> symbolLayer
                else -> null
            }
        }

        val overlay = ForecastRasterOverlay(map = map, idNamespace = "wind")
        val legend = ForecastLegendSpec(
            unitLabel = "kt",
            stops = listOf(
                ForecastLegendStop(value = 0.0, argb = 0xFF000000.toInt()),
                ForecastLegendStop(value = 10.0, argb = 0xFFFFFFFF.toInt())
            )
        )
        val first = ForecastTileSpec(
            urlTemplate = "https://tiles.example/wind/first/{z}/{x}/{y}.pbf",
            minZoom = 3,
            maxZoom = 16,
            format = ForecastTileFormat.VECTOR_WIND_POINTS,
            sourceLayer = "sfcwind0",
            sourceLayerCandidates = listOf("sfcwind0"),
            speedProperty = "spd",
            directionProperty = "dir"
        )
        val second = first.copy(
            urlTemplate = "https://tiles.example/wind/second/{z}/{x}/{y}.pbf",
            sourceLayer = "bltopwind",
            sourceLayerCandidates = listOf("bltopwind")
        )

        mockConstruction(VectorSource::class.java).use {
            overlay.render(
                tileSpec = first,
                opacity = 0.7f,
                windOverlayScale = 1.0f,
                windDisplayMode = ForecastWindDisplayMode.ARROW,
                legendSpec = legend
            )
            overlay.render(
                tileSpec = second,
                opacity = 0.7f,
                windOverlayScale = 1.0f,
                windDisplayMode = ForecastWindDisplayMode.ARROW,
                legendSpec = legend
            )
        }

        verify(style, atLeastOnce()).removeLayer(eq("forecast-wind-wind-symbol-layer"))
        verify(style, atLeastOnce()).addLayer(any())
    }
}
