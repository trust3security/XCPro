package com.example.xcpro.adsb

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
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
    fun setUp() = runBlocking(Dispatchers.IO) {
        val repository = AdsbTrafficPreferencesRepository(context)
        repository.setIconSizePx(ADSB_ICON_SIZE_DEFAULT_PX)
        repository.setMaxDistanceKm(ADSB_MAX_DISTANCE_DEFAULT_KM)
        repository.setVerticalAboveMeters(ADSB_VERTICAL_FILTER_ABOVE_DEFAULT_METERS)
        repository.setVerticalBelowMeters(ADSB_VERTICAL_FILTER_BELOW_DEFAULT_METERS)
    }

    @Test
    fun iconSizePolicy_matches124To248Contract() {
        assertEquals(124, ADSB_ICON_SIZE_MIN_PX)
        assertEquals(124, ADSB_ICON_SIZE_DEFAULT_PX)
        assertEquals(248, ADSB_ICON_SIZE_MAX_PX)
    }

    @Test
    fun iconSizeFlow_defaultsToConfiguredDefaultPx() = runTest {
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

        repository.setIconSizePx(160)
        val current = repository.iconSizePxFlow.first()

        assertEquals(160, current)
    }

    @Test
    fun maxDistance_defaultsTo10Km() = runTest {
        val repository = AdsbTrafficPreferencesRepository(context)

        val current = repository.maxDistanceKmFlow.first()

        assertEquals(ADSB_MAX_DISTANCE_DEFAULT_KM, current)
    }

    @Test
    fun setMaxDistance_clampsAndPersists() = runTest {
        val repository = AdsbTrafficPreferencesRepository(context)

        repository.setMaxDistanceKm(0)
        assertEquals(ADSB_MAX_DISTANCE_MIN_KM, repository.maxDistanceKmFlow.first())

        repository.setMaxDistanceKm(999)
        assertEquals(ADSB_MAX_DISTANCE_MAX_KM, repository.maxDistanceKmFlow.first())

        repository.setMaxDistanceKm(27)
        assertEquals(27, repository.maxDistanceKmFlow.first())
    }

    @Test
    fun verticalFilters_defaultToConfiguredFeetEquivalents() = runTest {
        val repository = AdsbTrafficPreferencesRepository(context)

        assertEquals(
            ADSB_VERTICAL_FILTER_ABOVE_DEFAULT_METERS,
            repository.verticalAboveMetersFlow.first(),
            1e-6
        )
        assertEquals(
            ADSB_VERTICAL_FILTER_BELOW_DEFAULT_METERS,
            repository.verticalBelowMetersFlow.first(),
            1e-6
        )
    }

    @Test
    fun setVerticalFilters_clampAndPersist() = runTest {
        val repository = AdsbTrafficPreferencesRepository(context)

        repository.setVerticalAboveMeters(-10.0)
        assertEquals(
            ADSB_VERTICAL_FILTER_MIN_METERS,
            repository.verticalAboveMetersFlow.first(),
            1e-6
        )

        repository.setVerticalBelowMeters(999_999.0)
        assertEquals(
            ADSB_VERTICAL_FILTER_MAX_METERS,
            repository.verticalBelowMetersFlow.first(),
            1e-6
        )

        repository.setVerticalAboveMeters(1234.5)
        repository.setVerticalBelowMeters(678.9)
        assertEquals(1234.5, repository.verticalAboveMetersFlow.first(), 1e-6)
        assertEquals(678.9, repository.verticalBelowMetersFlow.first(), 1e-6)
    }
}
