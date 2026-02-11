package com.example.xcpro.adsb

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class AdsbTrafficPreferencesRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = runBlocking {
        val repository = AdsbTrafficPreferencesRepository(context)
        repository.setIconSizePx(ADSB_ICON_SIZE_DEFAULT_PX)
    }

    @After
    fun tearDown() {
        context.filesDir.resolve("datastore")?.takeIf { it.exists() }?.deleteRecursively()
    }

    @Test
    fun iconSizeFlow_defaultsTo56Px() = runTest {
        val repository = AdsbTrafficPreferencesRepository(context)

        val current = repository.iconSizePxFlow.first()

        assertEquals(ADSB_ICON_SIZE_DEFAULT_PX, current)
    }

    @Test
    fun setIconSizePx_clampsBelowMinimum() = runTest {
        val repository = AdsbTrafficPreferencesRepository(context)

        repository.setIconSizePx(1)
        val current = repository.iconSizePxFlow.first()

        assertEquals(ADSB_ICON_SIZE_MIN_PX, current)
    }

    @Test
    fun setIconSizePx_clampsAboveMaximum() = runTest {
        val repository = AdsbTrafficPreferencesRepository(context)

        repository.setIconSizePx(500)
        val current = repository.iconSizePxFlow.first()

        assertEquals(ADSB_ICON_SIZE_MAX_PX, current)
    }

    @Test
    fun setIconSizePx_persistsValidValue() = runTest {
        val repository = AdsbTrafficPreferencesRepository(context)

        repository.setIconSizePx(92)
        val current = repository.iconSizePxFlow.first()

        assertEquals(92, current)
    }
}
