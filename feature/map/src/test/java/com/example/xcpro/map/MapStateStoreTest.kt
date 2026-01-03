package com.example.xcpro.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapStateStoreTest {

    @Test
    fun updateSafeContainerSize_ignoresZero() {
        val store = MapStateStore(initialStyleName = "Topo")

        store.updateSafeContainerSize(MapStateStore.MapSize.Zero)

        assertEquals(MapStateStore.MapSize.Zero, store.safeContainerSize.value)
    }

    @Test
    fun updateMapStyleName_reportsChanges() {
        val store = MapStateStore(initialStyleName = "Topo")

        assertFalse(store.updateMapStyleName("Topo"))
        assertTrue(store.updateMapStyleName("Satellite"))
        assertEquals("Satellite", store.mapStyleName.value)
    }

    @Test
    fun setShowDistanceCircles_updatesState() {
        val store = MapStateStore(initialStyleName = "Topo")

        store.setShowDistanceCircles(true)

        assertTrue(store.showDistanceCircles.value)
    }
}
