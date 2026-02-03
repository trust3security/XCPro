import com.example.xcpro.tasks.aat.calculations.AATDistanceCalculator
import com.example.xcpro.tasks.aat.models.AATAreaShape
import com.example.xcpro.tasks.aat.models.AATAssignedArea
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.models.AATWaypointRole
import org.junit.Assert.assertTrue
import org.junit.Test

class AATDistanceCalculatorTest {

    @Test
    fun calculateInteractiveTaskDistance_setsCalculationTime() {
        val calculator = AATDistanceCalculator()

        val waypoints = listOf(
            waypoint("start", 37.0, -122.0, AATWaypointRole.START),
            waypoint("turn", 37.1, -122.2, AATWaypointRole.TURNPOINT),
            waypoint("finish", 37.2, -122.3, AATWaypointRole.FINISH)
        )

        val result = calculator.calculateInteractiveTaskDistance(waypoints)

        assertTrue("expected non-empty segments", result.segments.isNotEmpty())
        assertTrue("expected positive distance", result.totalDistance > 0.0)
        assertTrue("expected non-negative calculation time", result.calculationTime >= 0L)
    }

    private fun waypoint(id: String, lat: Double, lon: Double, role: AATWaypointRole): AATWaypoint {
        val area = AATAssignedArea.createWithStandardizedDefaults(AATAreaShape.CIRCLE, role)
        return AATWaypoint(
            id = id,
            title = id,
            subtitle = "",
            lat = lat,
            lon = lon,
            role = role,
            assignedArea = area,
            targetPoint = AATLatLng(lat, lon),
            isTargetPointCustomized = false
        )
    }
}
