package com.example.xcpro.tasks

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

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
    fun `updateTargetPoint delegates update`() {
        val controller = controller()

        controller.updateTargetPoint(2, 51.0, -0.1)

        assertEquals(listOf(Triple(2, 51.0, -0.1)), fakeOperations.updateCalls)
        assertTrue(logs.any { it.contains("Updating AAT target point index=2") })
    }

    @Test
    fun `updateTargetPoint handles multiple updates`() {
        val controller = controller()

        controller.updateTargetPoint(1, 40.0, 10.0)
        controller.updateTargetPoint(2, 41.0, 11.0)

        assertEquals(2, fakeOperations.updateCalls.size)
    }

    @Test
    fun `enterEditMode sets edit state`() {
        val controller = controller()

        controller.enterEditMode(3)

        assertEquals(listOf(3 to true), fakeOperations.editModeCalls)
    }

    @Test
    fun `exitEditMode clears edit state`() {
        val controller = controller()

        controller.exitEditMode()

        assertEquals(listOf(-1 to false), fakeOperations.editModeCalls)
    }

    @Test
    fun `isInEditMode delegates to operations`() {
        val controller = controller()
        fakeOperations.isEditMode = true

        assertTrue(controller.isInEditMode())
    }

    @Test
    fun `editWaypointIndex delegates to operations`() {
        val controller = controller()
        fakeOperations.editIndex = 7

        assertEquals(7, controller.editWaypointIndex())
    }

    @Test
    fun `checkAreaTap delegates to operations`() {
        val controller = controller()
        fakeOperations.areaTapResult = 2 to "area"

        assertEquals(2 to "area", controller.checkAreaTap(1.0, 2.0))
    }

    private fun controller() =
        AATEditController(fakeOperations, log)

    private class FakeAATOperations : AATEditOperations {
        val updateCalls = mutableListOf<Triple<Int, Double, Double>>()
        val editModeCalls = mutableListOf<Pair<Int, Boolean>>()

        var isEditMode: Boolean = false
        var editIndex: Int? = null
        var areaTapResult: Pair<Int, Any>? = null

        override fun updateTargetPoint(index: Int, lat: Double, lon: Double) {
            updateCalls += Triple(index, lat, lon)
        }

        override fun checkAreaTap(lat: Double, lon: Double): Pair<Int, Any>? = areaTapResult

        override fun setEditMode(waypointIndex: Int, enabled: Boolean) {
            editModeCalls += waypointIndex to enabled
            isEditMode = enabled
            editIndex = if (enabled) waypointIndex else null
        }

        override fun isInEditMode(): Boolean = isEditMode

        override fun getEditWaypointIndex(): Int? = editIndex
    }
}
