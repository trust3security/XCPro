package com.trust3.xcpro

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.trust3.xcpro.common.documents.DocumentRef
import java.io.File
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WaypointOverlayRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repository: WaypointOverlayRepository

    @Before
    fun setUp() {
        repository = WaypointOverlayRepository(context)
        cleanupFiles()
    }

    @After
    fun tearDown() {
        cleanupFiles()
    }

    @Test
    fun buildGeoJson_matchesAuthoritativeParser_forQuotedCupRowsAndTaskTrailer() = runTest {
        val file = writeWaypointFile(
            "waypoint_overlay_parity.cup",
            """
            "name","code","country","lat","lon","elev","style","rwdir","rwlen","freq","desc"
            "Airport, One","A1","AU","3351.125S","15112.500E","120m","1","","","123.45","Primary, grass strip"
            "Beta Field","B2","AU","3352.125S","15113.500E","130m","2","","","","Second waypoint"
            -----Related Tasks-----
            ObsZone=ignored
            """.trimIndent()
        )
        val document = file.asDocumentRef()
        val parsedWaypoints = WaypointParser.parseWaypointFile(context, Uri.fromFile(file))

        val geoJson = JSONObject(
            repository.buildGeoJson(
                files = listOf(document),
                checkedStates = mapOf(file.name to true)
            )
        )

        val features = geoJson.getJSONArray("features")
        assertEquals(parsedWaypoints.size, features.length())
        assertEquals(2, features.length())

        val firstFeature = features.getJSONObject(0)
        val firstProperties = firstFeature.getJSONObject("properties")
        val firstCoordinates = firstFeature.getJSONObject("geometry").getJSONArray("coordinates")
        assertEquals(parsedWaypoints[0].name, firstProperties.getString("name"))
        assertEquals(parsedWaypoints[0].elevation, firstProperties.getString("elevation"))
        assertEquals(parsedWaypoints[0].longitude, firstCoordinates.getDouble(0), 1e-6)
        assertEquals(parsedWaypoints[0].latitude, firstCoordinates.getDouble(1), 1e-6)

        val secondFeature = features.getJSONObject(1)
        assertEquals(parsedWaypoints[1].name, secondFeature.getJSONObject("properties").getString("name"))
    }

    @Test
    fun buildGeoJson_disabledFilesProduceNoFeatures() = runTest {
        val file = writeWaypointFile(
            "waypoint_overlay_disabled.cup",
            """
            name,code,country,lat,lon,elev,style,rwdir,rwlen,freq,desc
            Alpha,A1,AU,3351.125S,15112.500E,120m,1,,,,
            """.trimIndent()
        )

        val geoJson = JSONObject(
            repository.buildGeoJson(
                files = listOf(file.asDocumentRef()),
                checkedStates = mapOf(file.name to false)
            )
        )

        assertEquals(0, geoJson.getJSONArray("features").length())
    }

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
