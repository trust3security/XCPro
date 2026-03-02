package com.example.xcpro.profiles

import android.content.Context
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class ProfileExportImportTest {

    private val subject = ProfileExportImport(mock(Context::class.java))

    @Test
    fun exportAllProfiles_returnsFailure_whenNoProfilesProvided() = runTest {
        val result = subject.exportAllProfiles(emptyList())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("No profiles") == true)
    }

    @Test
    fun exportAllProfiles_serializesAllProvidedProfiles() = runTest {
        val first = UserProfile(
            name = "Pilot 1",
            aircraftType = AircraftType.SAILPLANE,
            preferences = ProfilePreferences(units = UnitSystem.IMPERIAL),
            createdAt = 111L,
            lastUsed = 222L
        )
        val second = UserProfile(name = "Pilot 2", aircraftType = AircraftType.PARAGLIDER)

        val result = subject.exportAllProfiles(listOf(first, second))

        assertTrue(result.isSuccess)
        val json = result.getOrNull().orEmpty()
        assertFalse(json.isBlank())
        val imported = subject.importProfiles(json)
        assertTrue(imported.isSuccess)
        assertEquals(2, imported.getOrNull()?.size)
        assertEquals("Pilot 1", imported.getOrNull()?.get(0)?.name)
        assertEquals("Pilot 2", imported.getOrNull()?.get(1)?.name)
        assertEquals(UnitSystem.IMPERIAL, imported.getOrNull()?.get(0)?.preferences?.units)
        assertEquals(111L, imported.getOrNull()?.get(0)?.createdAt)
        assertEquals(222L, imported.getOrNull()?.get(0)?.lastUsed)
        assertTrue(imported.getOrNull()?.get(0)?.id != first.id)
    }
}
