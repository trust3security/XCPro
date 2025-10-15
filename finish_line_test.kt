// Test script to verify FAI finish line compliance
// Run this with: kotlinc finish_line_test.kt -cp . && kotlin FinishLineTestKt

import kotlin.math.*

data class TestWaypoint(
    val title: String,
    val lat: Double,
    val lon: Double,
    val gateWidth: Double = 1.0 // km
)

data class TestContext(
    val previousWaypoint: TestWaypoint?,
    val waypointIndex: Int = 1
)

/**
 * Test implementation of FinishLineDisplay logic
 */
class FinishLineTest {

    fun testFAICompliance() {
        println("=== FAI FINISH LINE COMPLIANCE TEST ===")

        // Test Case 1: North-South task leg (bearing = 0°)
        testScenario(
            "North-South Leg",
            previous = TestWaypoint("TP1", 50.0, 0.0),
            finish = TestWaypoint("FINISH", 51.0, 0.0),
            expectedBearing = 0.0,
            expectedPerpBearing = 90.0
        )

        // Test Case 2: East-West task leg (bearing = 90°)
        testScenario(
            "East-West Leg",
            previous = TestWaypoint("TP1", 50.0, 0.0),
            finish = TestWaypoint("FINISH", 50.0, 1.0),
            expectedBearing = 90.0,
            expectedPerpBearing = 180.0
        )

        // Test Case 3: Diagonal task leg (bearing = 45°)
        testScenario(
            "Diagonal Leg",
            previous = TestWaypoint("TP1", 50.0, 0.0),
            finish = TestWaypoint("FINISH", 50.7071, 0.7071),
            expectedBearing = 45.0,
            expectedPerpBearing = 135.0
        )

        // Test Case 4: Southwest bearing (bearing = 225°)
        testScenario(
            "Southwest Leg",
            previous = TestWaypoint("TP1", 51.0, 1.0),
            finish = TestWaypoint("FINISH", 50.0, 0.0),
            expectedBearing = 225.0,
            expectedPerpBearing = 315.0
        )

        println("=== END FAI COMPLIANCE TEST ===")
    }

    private fun testScenario(
        scenarioName: String,
        previous: TestWaypoint,
        finish: TestWaypoint,
        expectedBearing: Double,
        expectedPerpBearing: Double
    ) {
        println("\n--- $scenarioName ---")

        // Calculate bearing from previous to finish (final task leg)
        val actualBearing = calculateBearing(previous.lat, previous.lon, finish.lat, finish.lon)

        // Calculate perpendicular bearing (finish line orientation)
        val actualPerpBearing = (actualBearing + 90.0) % 360.0

        // Log results
        println("Previous waypoint: ${previous.title} at (${previous.lat}, ${previous.lon})")
        println("Finish waypoint: ${finish.title} at (${finish.lat}, ${finish.lon})")
        println("Final task leg bearing: ${String.format("%.2f", actualBearing)}° (expected: ${expectedBearing}°)")
        println("Finish line perpendicular bearing: ${String.format("%.2f", actualPerpBearing)}° (expected: ${expectedPerpBearing}°)")

        // Validate FAI compliance
        val bearingError = Math.abs(actualBearing - expectedBearing)
        val perpError = Math.abs(actualPerpBearing - expectedPerpBearing)

        if (bearingError < 1.0) {
            println("FAI Compliance: ✓ Task leg bearing correct")
        } else {
            println("FAI Warning: ✗ Task leg bearing error: ${String.format("%.2f", bearingError)}°")
        }

        if (perpError < 1.0) {
            println("FAI Compliance: ✓ Finish line perpendicular to task leg")
        } else {
            println("FAI Warning: ✗ Perpendicular bearing error: ${String.format("%.2f", perpError)}°")
        }

        // Test line endpoints
        val gateWidthMeters = finish.gateWidth * 1000.0
        val halfWidth = gateWidthMeters / 2.0

        val point1 = calculateDestination(finish.lat, finish.lon, halfWidth, actualPerpBearing)
        val point2 = calculateDestination(finish.lat, finish.lon, halfWidth, (actualPerpBearing + 180.0) % 360.0)

        println("Finish line Point1: (${String.format("%.6f", point1.first)}, ${String.format("%.6f", point1.second)})")
        println("Finish line Point2: (${String.format("%.6f", point2.first)}, ${String.format("%.6f", point2.second)})")

        // Validate line length
        val calculatedLength = calculateDistance(point1.first, point1.second, point2.first, point2.second)
        println("Line length: ${String.format("%.2f", calculatedLength)}m (expected: ${gateWidthMeters}m)")

        if (Math.abs(calculatedLength - gateWidthMeters) < 1.0) {
            println("FAI Compliance: ✓ Line length correct")
        } else {
            println("FAI Warning: ✗ Line length error: ${String.format("%.2f", Math.abs(calculatedLength - gateWidthMeters))}m")
        }
    }

    /**
     * Calculate bearing between two points
     */
    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)

        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)

        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360.0) % 360.0
    }

    /**
     * Calculate destination point from start point, bearing, and distance
     */
    private fun calculateDestination(lat: Double, lon: Double, distanceMeters: Double, bearingDegrees: Double): Pair<Double, Double> {
        val R = 6371000.0 // Earth's radius in meters
        val bearing = Math.toRadians(bearingDegrees)
        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)
        val angularDistance = distanceMeters / R

        val newLatRad = asin(sin(latRad) * cos(angularDistance) + cos(latRad) * sin(angularDistance) * cos(bearing))
        val newLonRad = lonRad + atan2(sin(bearing) * sin(angularDistance) * cos(latRad), cos(angularDistance) - sin(latRad) * sin(newLatRad))

        return Pair(Math.toDegrees(newLatRad), Math.toDegrees(newLonRad))
    }

    /**
     * Calculate distance between two points
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // Earth's radius in meters
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLat = Math.toRadians(lat2 - lat1)
        val deltaLon = Math.toRadians(lon2 - lon1)

        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(deltaLon / 2) * sin(deltaLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return R * c
    }
}

fun main() {
    val test = FinishLineTest()
    test.testFAICompliance()
}