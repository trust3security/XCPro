package com.example.xcpro.map

import org.junit.Assert.assertTrue
import org.junit.Test

class MapRuntimePortsContractTest {

    @Test
    fun mapCameraManager_implementsCameraRuntimePort() {
        assertTrue(MapCameraRuntimePort::class.java.isAssignableFrom(MapCameraManager::class.java))
    }

    @Test
    fun locationManager_implementsLocationRuntimePort() {
        assertTrue(MapLocationRuntimePort::class.java.isAssignableFrom(LocationManager::class.java))
    }

    @Test
    fun mapLifecycleManager_implementsLifecycleRuntimePort() {
        assertTrue(MapLifecycleRuntimePort::class.java.isAssignableFrom(MapLifecycleManager::class.java))
    }
}
