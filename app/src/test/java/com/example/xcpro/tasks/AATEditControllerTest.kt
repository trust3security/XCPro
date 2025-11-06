package com.example.xcpro.tasks

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.maplibre.android.maps.MapLibreMap

class AATEditControllerTest {

    private lateinit var fakeOperations: FakeAATOperations
    private val logs = mutableListOf<String>()
    private val log: (String) -> Unit = { logs += it }

    @Before
    fun setUp() {
        fakeOperations = FakeAATOperations()
        logs.clear()
    }

    @Test
    fun `updateTargetPoint plots when map available`() {
        val map = Mockito.mock(MapLibreMap::class.java)
        val controller = controller { map }

        controller.updateTargetPoint(2, 51.0, -0.1)

        assertEquals(listOf(Triple(2, 51.0, -0.1)), fakeOperations.updateCalls)
        assertEquals(listOf(map), fakeOperations.plotCalls)
        assertTrue(logs.any { it.contains("Updating AAT target point index=2") })
    }

    @Test
    fun `updateTargetPoint logs when map missing`() {
        val controller = controller { null }

        controller.updateTargetPoint(1, 40.0, 10.0)

        assertEquals(1, fakeOperations.updateCalls.size)
        assertTrue(fakeOperations.plotCalls.isEmpty())
        assertTrue(logs.any { it.contains("Cannot re-plot AAT task") })
    }

    @Test
    fun `enterEditMode applies overlay and plotting`() {
        val map = Mockito.mock(MapLibreMap::class.java)
        val controller = controller { map }

        controller.enterEditMode(3)

        assertEquals(listOf(3 to true), fakeOperations.editModeCalls)
        assertEquals(listOf(map to 3), fakeOperations.overlayCalls)
        assertEquals(listOf(map), fakeOperations.plotCalls)
    }

    @Test
    fun `exitEditMode clears overlay`() {
        val map = Mockito.mock(MapLibreMap::class.java)
        val controller = controller { map }

        controller.exitEditMode()

        assertEquals(listOf(-1 to false), fakeOperations.editModeCalls)
        assertEquals(listOf(map), fakeOperations.clearOverlayCalls)
    }

    @Test
    fun `isInEditMode delegates to operations`() {
        val controller = controller { null }
        fakeOperations.isEditMode = true

        assertTrue(controller.isInEditMode())
    }

    @Test
    fun `editWaypointIndex delegates to operations`() {
        val controller = controller { null }
        fakeOperations.editIndex = 7

        assertEquals(7, controller.editWaypointIndex())
    }

    @Test
    fun `checkAreaTap delegates to operations`() {
        val controller = controller { null }
        fakeOperations.areaTapResult = 2 to "area"

        assertEquals(2 to "area", controller.checkAreaTap(1.0, 2.0))
    }

    @Test
    fun `checkTargetPointHit returns null when map missing`() {
        val controller = controller { null }

        assertNull(controller.checkTargetPointHit(10f, 20f))
        assertTrue(logs.any { it.contains("Cannot check target point hit") })
        assertTrue(fakeOperations.targetPointHitCalls.isEmpty())
    }

    @Test
    fun `checkTargetPointHit delegates when map exists`() {
        val map = Mockito.mock(MapLibreMap::class.java)
        fakeOperations.targetPointHitResult = 5
        val controller = controller { map }

        val result = controller.checkTargetPointHit(12f, 34f)

        assertEquals(5, result)
        assertEquals(listOf(Triple(map, 12f, 34f)), fakeOperations.targetPointHitCalls)
    }

    private fun controller(mapProvider: () -> MapLibreMap?) =
        AATEditController(fakeOperations, mapProvider, log)

    private class FakeAATOperations : AATEditOperations {
        val updateCalls = mutableListOf<Triple<Int, Double, Double>>()
        val editModeCalls = mutableListOf<Pair<Int, Boolean>>()
        val overlayCalls = mutableListOf<Pair<MapLibreMap, Int>>()
        val plotCalls = mutableListOf<MapLibreMap>()
        val clearOverlayCalls = mutableListOf<MapLibreMap>()
        val targetPointHitCalls = mutableListOf<Triple<MapLibreMap, Float, Float>>()

        var isEditMode: Boolean = false
        var editIndex: Int? = null
        var areaTapResult: Pair<Int, Any>? = null
        var targetPointHitResult: Int? = null

        override fun updateTargetPoint(index: Int, lat: Double, lon: Double) {
            updateCalls += Triple(index, lat, lon)
        }

        override fun checkAreaTap(lat: Double, lon: Double): Pair<Int, Any>? = areaTapResult

        override fun setEditMode(waypointIndex: Int, enabled: Boolean) {
            editModeCalls += waypointIndex to enabled
            isEditMode = enabled
            editIndex = if (enabled) waypointIndex else null
        }

        override fun plotAATEditOverlay(map: MapLibreMap, waypointIndex: Int) {
            overlayCalls += map to waypointIndex
        }

        override fun plotAATOnMap(map: MapLibreMap) {
            plotCalls += map
        }

        override fun clearAATEditOverlay(map: MapLibreMap) {
            clearOverlayCalls += map
        }

        override fun isInEditMode(): Boolean = isEditMode

        override fun getEditWaypointIndex(): Int? = editIndex

        override fun checkTargetPointHit(
            map: MapLibreMap,
            screenX: Float,
            screenY: Float
        ): Int? {
            targetPointHitCalls += Triple(map, screenX, screenY)
            return targetPointHitResult
        }
    }
}
