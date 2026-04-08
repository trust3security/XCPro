package com.example.xcpro

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.testing.MainDispatcherRule
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WaypointApplyTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        cleanupFiles()
    }

    @After
    fun tearDown() {
        cleanupFiles()
    }

    @Test
    fun loadAndApplyWaypoints_scaleAboveThreshold_clearsExistingOverlay() = runTest {
        val map: MapLibreMap = mock()
        val style: Style = mock()

        whenever(map.cameraPosition).thenReturn(
            CameraPosition.Builder()
                .target(LatLng(0.0, 0.0))
                .zoom(1.0)
                .build()
        )
        whenever(map.style).thenReturn(style)

        loadAndApplyWaypoints(
            context = context,
            map = map,
            waypointFiles = listOf(sampleDocument("waypoint_overlay_scale_gate.cup")),
            checkedStates = mapOf("waypoint_overlay_scale_gate.cup" to true)
        )

        verify(style, times(1)).removeLayer("waypoint-layer")
        verify(style, times(1)).removeSource("waypoint-source")
    }

    @Test
    fun loadAndApplyWaypoints_enabledFile_delegatesToOverlayRepository() = runTest {
        val file = writeWaypointFile(
            "waypoint_overlay_apply_enabled.cup",
            """
            "name","code","country","lat","lon","elev","style","rwdir","rwlen","freq","desc"
            "Airport, One","A1","AU","3351.125S","15112.500E","120m","1","","","123.45","Primary, grass strip"
            """.trimIndent()
        )
        val map: MapLibreMap = mock()
        val style: Style = mock()
        val repository: WaypointOverlayRepository = mock()

        whenever(map.cameraPosition).thenReturn(
            CameraPosition.Builder()
                .target(LatLng(-33.85, 151.21))
                .zoom(11.0)
                .build()
        )
        whenever(map.style).thenReturn(style)
        whenever(repository.buildGeoJson(any(), any())).thenReturn(
            """{"type":"FeatureCollection","features":[]}"""
        )

        loadAndApplyWaypoints(
            context = context,
            map = map,
            waypointFiles = listOf(file.asDocumentRef()),
            checkedStates = mapOf(file.name to true),
            repository = repository
        )

        verify(repository, times(1)).buildGeoJson(
            eq(listOf(file.asDocumentRef())),
            eq(mapOf(file.name to true))
        )
    }

    private fun sampleDocument(fileName: String): DocumentRef =
        DocumentRef(uri = Uri.fromFile(File(context.filesDir, fileName)).toString(), displayName = fileName)

    private fun writeWaypointFile(fileName: String, content: String): File {
        val file = File(context.filesDir, fileName)
        file.writeText(content)
        return file
    }

    private fun File.asDocumentRef(): DocumentRef =
        DocumentRef(uri = Uri.fromFile(this).toString(), displayName = name)

    private fun cleanupFiles() {
        context.filesDir
            .listFiles()
            ?.filter { it.name.startsWith("waypoint_overlay_") }
            ?.forEach { it.delete() }
    }
}
