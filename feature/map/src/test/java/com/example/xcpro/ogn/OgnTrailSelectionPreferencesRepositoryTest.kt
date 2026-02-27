package com.example.xcpro.ogn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OgnTrailSelectionPreferencesRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repository: OgnTrailSelectionPreferencesRepository

    @Before
    fun setUp() = runBlocking(Dispatchers.IO) {
        repository = OgnTrailSelectionPreferencesRepository(context)
        repository.clearSelectedAircraft()
    }

    @Test
    fun setAircraftSelected_normalizesAndPersists() = runTest {
        repository.setAircraftSelected("  ab12cd  ", selected = true)
        val keys = repository.selectedAircraftKeysFlow.first()

        assertEquals(setOf("AB12CD"), keys)
    }

    @Test
    fun setAircraftSelected_falseClearsNormalizedKey() = runTest {
        repository.setAircraftSelected(" ab12cd ", selected = true)
        repository.setAircraftSelected("AB12CD", selected = true)

        repository.setAircraftSelected("ab12cd", selected = false)
        val keys = repository.selectedAircraftKeysFlow.first()

        assertEquals(emptySet<String>(), keys)
    }

    @Test
    fun setAircraftSelected_canonicalKeyReplacesLegacyAlias() = runTest {
        repository.setAircraftSelected("AB12CD", selected = true)

        repository.setAircraftSelected("FLARM:AB12CD", selected = true)
        val keys = repository.selectedAircraftKeysFlow.first()

        assertEquals(setOf("FLARM:AB12CD"), keys)
    }

    @Test
    fun removeAircraftKeys_removesMatchingAliases() = runTest {
        repository.setAircraftSelected("FLARM:AB12CD", selected = true)

        repository.removeAircraftKeys(setOf("AB12CD"))
        val keys = repository.selectedAircraftKeysFlow.first()

        assertEquals(emptySet<String>(), keys)
    }
}
