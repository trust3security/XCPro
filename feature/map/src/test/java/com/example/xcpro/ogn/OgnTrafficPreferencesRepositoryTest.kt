package com.example.xcpro.ogn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class OgnTrafficPreferencesRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = runBlocking {
        val repository = OgnTrafficPreferencesRepository(context)
        repository.setIconSizePx(OGN_ICON_SIZE_DEFAULT_PX)
        repository.setShowGliderTrailsEnabled(false)
    }

    @After
    fun tearDown() {
        context.filesDir.resolve("datastore")?.takeIf { it.exists() }?.deleteRecursively()
    }

    @Test
    fun iconSizePolicy_matches124To248Contract() {
        assertEquals(124, OGN_ICON_SIZE_MIN_PX)
        assertEquals(124, OGN_ICON_SIZE_DEFAULT_PX)
        assertEquals(248, OGN_ICON_SIZE_MAX_PX)
    }

    @Test
    fun iconSizeFlow_defaultsToConfiguredDefaultPx() = runTest {
        val repository = OgnTrafficPreferencesRepository(context)

        val current = repository.iconSizePxFlow.first()

        assertEquals(OGN_ICON_SIZE_DEFAULT_PX, current)
    }

    @Test
    fun setIconSizePx_clampsBelowMinimum() = runTest {
        val repository = OgnTrafficPreferencesRepository(context)

        repository.setIconSizePx(1)
        val current = repository.iconSizePxFlow.first()

        assertEquals(OGN_ICON_SIZE_MIN_PX, current)
    }

    @Test
    fun setIconSizePx_clampsAboveMaximum() = runTest {
        val repository = OgnTrafficPreferencesRepository(context)

        repository.setIconSizePx(999)
        val current = repository.iconSizePxFlow.first()

        assertEquals(OGN_ICON_SIZE_MAX_PX, current)
    }

    @Test
    fun setIconSizePx_persistsValidValue() = runTest {
        val repository = OgnTrafficPreferencesRepository(context)

        repository.setIconSizePx(240)
        val current = repository.iconSizePxFlow.first()

        assertEquals(240, current)
    }

    @Test
    fun showGliderTrailsEnabled_defaultsToFalse() = runTest {
        val repository = OgnTrafficPreferencesRepository(context)

        val current = repository.showGliderTrailsEnabledFlow.first()

        assertEquals(false, current)
    }

    @Test
    fun setShowGliderTrailsEnabled_persistsValue() = runTest {
        val repository = OgnTrafficPreferencesRepository(context)

        repository.setShowGliderTrailsEnabled(true)
        val current = repository.showGliderTrailsEnabledFlow.first()

        assertEquals(true, current)
    }
}
