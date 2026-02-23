package com.example.xcpro.ogn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun ownshipHexFlows_defaultToNull() = runTest {
        val repository = OgnTrafficPreferencesRepository(context)

        assertNull(repository.ownFlarmHexFlow.first())
        assertNull(repository.ownIcaoHexFlow.first())
    }

    @Test
    fun setOwnFlarmHex_normalizesAndPersists() = runTest {
        val repository = OgnTrafficPreferencesRepository(context)

        repository.setOwnFlarmHex("  ddA85c ")

        assertEquals("DDA85C", repository.ownFlarmHexFlow.first())
    }

    @Test
    fun setOwnIcaoHex_invalidNonBlankIsIgnored() = runTest {
        val repository = OgnTrafficPreferencesRepository(context)
        repository.setOwnIcaoHex("4ca6a4")

        repository.setOwnIcaoHex("not-hex")

        assertEquals("4CA6A4", repository.ownIcaoHexFlow.first())
    }

    @Test
    fun setOwnFlarmHex_blankClearsValue() = runTest {
        val repository = OgnTrafficPreferencesRepository(context)
        repository.setOwnFlarmHex("dda85c")
        repository.setOwnFlarmHex(" ")

        assertNull(repository.ownFlarmHexFlow.first())
    }

}
