package com.example.xcpro

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AirspaceParserTest {

    @Test
    fun validateOpenAirFile_acceptsDcGeometryWithoutDp() {
        val content = """
            AC D
            AN TEST CIRCLE
            V X=4621.000N 00608.000E
            DC 2.5
            AL 0FT
            AH 4500FT
        """.trimIndent()

        val result = validateOpenAirFile(content)

        assertTrue(result.first)
    }

    @Test
    fun parseOpenAirToGeoJson_parsesDecimalMinuteCoordinates() {
        val content = """
            AC D
            AN TEST POLYGON
            AL 0FT
            AH 4500FT
            DP 4621.200N 00608.100E
            DP 4621.400N 00608.300E
            DP 4621.200N 00608.100E
        """.trimIndent()

        val geoJson = JSONObject(parseOpenAirToGeoJson(content, setOf("D")))
        val firstFeature = geoJson.getJSONArray("features").getJSONObject(0)
        val firstPoint = firstFeature
            .getJSONObject("geometry")
            .getJSONArray("coordinates")
            .getJSONArray(0)
            .getJSONArray(0)

        val lon = firstPoint.getDouble(0)
        val lat = firstPoint.getDouble(1)

        assertEquals(6.135, lon, 0.0001)
        assertEquals(46.353333, lat, 0.0001)
    }

    @Test
    fun parseOpenAirToGeoJson_handlesIndentedDirectiveLines() {
        val content = """
               AC D
               AN INDENTED
               AL 0FT
               AH 4500FT
                 DP 4621.200N 00608.100E
                 DP 4621.400N 00608.300E
                 DP 4621.200N 00608.100E
        """.trimIndent()

        val geoJson = JSONObject(parseOpenAirToGeoJson(content, setOf("D")))

        assertEquals(1, geoJson.getJSONArray("features").length())
    }

    @Test
    fun parseOpenAirToGeoJson_supportsDbArcWithVCenter() {
        val content = """
            AC D
            AN ARC
            AL 0FT
            AH 4500FT
            V X=4621.000N 00608.000E
            V D=-
            DB 4621.100N 00608.000E,4621.000N 00608.100E
        """.trimIndent()

        val geoJson = JSONObject(parseOpenAirToGeoJson(content, setOf("D")))
        val featureCount = geoJson.getJSONArray("features").length()
        assertEquals(1, featureCount)

        val coordinateCount = geoJson
            .getJSONArray("features")
            .getJSONObject(0)
            .getJSONObject("geometry")
            .getJSONArray("coordinates")
            .getJSONArray(0)
            .length()
        assertTrue(coordinateCount > 3)
    }
}
