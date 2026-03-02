package com.example.xcpro

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.xcpro.common.documents.DocumentRef
import java.io.File
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AirspaceRepositoryTest {

    private lateinit var appContext: Context
    private lateinit var repository: AirspaceRepository
    private lateinit var airspaceFile: File
    private lateinit var document: DocumentRef

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        repository = AirspaceRepository(appContext)
        airspaceFile = File(appContext.filesDir, "airspace_collision_${System.nanoTime()}.txt")
        airspaceFile.writeText(TEST_OPEN_AIR)
        document = DocumentRef(
            uri = Uri.fromFile(airspaceFile).toString(),
            displayName = airspaceFile.name
        )
    }

    @After
    fun tearDown() {
        airspaceFile.delete()
    }

    @Test
    fun buildGeoJson_distinguishesSelectedClassSetsEvenWhenElementHashesCollide() = runTest {
        assertEquals("FB".hashCode(), "Ea".hashCode())

        val fbGeoJson = JSONObject(repository.buildGeoJson(listOf(document), setOf("FB")))
        val eaGeoJson = JSONObject(repository.buildGeoJson(listOf(document), setOf("Ea")))

        val fbFeature = fbGeoJson.getJSONArray("features").getJSONObject(0)
        val eaFeature = eaGeoJson.getJSONArray("features").getJSONObject(0)

        assertEquals("FB", fbFeature.getJSONObject("properties").getString("class"))
        assertEquals("Ea", eaFeature.getJSONObject("properties").getString("class"))
        assertTrue(fbGeoJson.getJSONArray("features").length() == 1)
        assertTrue(eaGeoJson.getJSONArray("features").length() == 1)
        assertNotEquals(fbGeoJson.toString(), eaGeoJson.toString())
    }

    private companion object {
        val TEST_OPEN_AIR = """
            AC FB
            AN HASH COLLISION FB
            AL 0FT
            AH 4500FT
            DP 4621.200N 00608.100E
            DP 4621.400N 00608.300E
            DP 4621.200N 00608.100E

            AC Ea
            AN HASH COLLISION EA
            AL 0FT
            AH 4500FT
            DP 4622.200N 00609.100E
            DP 4622.400N 00609.300E
            DP 4622.200N 00609.100E
        """.trimIndent()
    }
}
