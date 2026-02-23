package com.example.xcpro.ogn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OgnTrailSelectionPreferencesRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        context.filesDir.resolve("datastore")?.takeIf { it.exists() }?.deleteRecursively()
    }

    @Test
    fun setAircraftSelected_normalizesAndPersists() = runTest {
        val repository = OgnTrailSelectionPreferencesRepository(context)

        repository.setAircraftSelected("  ab12cd  ", selected = true)
        val keys = repository.selectedAircraftKeysFlow.first()

        assertEquals(setOf("AB12CD"), keys)
    }

    @Test
    fun setAircraftSelected_falseClearsNormalizedKey() = runTest {
        val repository = OgnTrailSelectionPreferencesRepository(context)
        repository.setAircraftSelected(" ab12cd ", selected = true)
        repository.setAircraftSelected("AB12CD", selected = true)

        repository.setAircraftSelected("ab12cd", selected = false)
        val keys = repository.selectedAircraftKeysFlow.first()

        assertEquals(emptySet<String>(), keys)
    }

    @Test
    fun setAircraftSelected_canonicalKeyReplacesLegacyAlias() = runTest {
        val repository = OgnTrailSelectionPreferencesRepository(context)
        repository.setAircraftSelected("AB12CD", selected = true)

        repository.setAircraftSelected("FLARM:AB12CD", selected = true)
        val keys = repository.selectedAircraftKeysFlow.first()

        assertEquals(setOf("FLARM:AB12CD"), keys)
    }

    @Test
    fun removeAircraftKeys_removesMatchingAliases() = runTest {
        val repository = OgnTrailSelectionPreferencesRepository(context)
        repository.setAircraftSelected("FLARM:AB12CD", selected = true)

        repository.removeAircraftKeys(setOf("AB12CD"))
        val keys = repository.selectedAircraftKeysFlow.first()

        assertEquals(emptySet<String>(), keys)
    }
}
