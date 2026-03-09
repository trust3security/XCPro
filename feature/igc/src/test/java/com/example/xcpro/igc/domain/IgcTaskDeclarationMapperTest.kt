package com.example.xcpro.igc.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IgcTaskDeclarationMapperTest {

    private val mapper = IgcTaskDeclarationMapper()

    @Test
    fun map_validSnapshot_emitsHeaderAndWaypoints() {
        val snapshot = IgcTaskDeclarationSnapshot(
            taskId = "TASK-01",
            capturedAtUtcMs = 1_741_483_200_000L,
            waypoints = listOf(
                IgcTaskDeclarationWaypoint("START", -33.865, 151.209),
                IgcTaskDeclarationWaypoint("TP1", -33.900, 151.250),
                IgcTaskDeclarationWaypoint("FINISH", -33.920, 151.280)
            )
        )

        val lines = mapper.map(snapshot)
        assertEquals(4, lines.size)
        assertTrue(lines.first().startsWith("C"))
        assertTrue(lines[1].startsWith("C3351900S15112540E"))
        assertTrue(lines.last().contains("FINISH"))
    }

    @Test
    fun map_requiresAtLeastTwoValidWaypoints() {
        val singleWaypoint = IgcTaskDeclarationSnapshot(
            taskId = "TASK-01",
            capturedAtUtcMs = 1_741_483_200_000L,
            waypoints = listOf(
                IgcTaskDeclarationWaypoint("START", -33.865, 151.209)
            )
        )

        assertTrue(mapper.map(singleWaypoint).isEmpty())
    }

    @Test
    fun map_ignoresInvalidCoordinates_andFallsBackToEmptyWhenTooFewRemain() {
        val snapshot = IgcTaskDeclarationSnapshot(
            taskId = "TASK-02",
            capturedAtUtcMs = 1_741_483_200_000L,
            waypoints = listOf(
                IgcTaskDeclarationWaypoint("A", -33.865, 151.209),
                IgcTaskDeclarationWaypoint("B", Double.NaN, 151.220)
            )
        )

        assertTrue(mapper.map(snapshot).isEmpty())
    }
}
