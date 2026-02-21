package com.example.xcpro.forecast

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class ToggleForecastPrimaryOverlaySelectionUseCaseTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = runBlocking {
        val repository = ForecastPreferencesRepository(context)
        repository.setOverlayEnabled(false)
        repository.setOpacity(FORECAST_OPACITY_DEFAULT)
        repository.setWindOverlayScale(FORECAST_WIND_OVERLAY_SCALE_DEFAULT)
        repository.setSecondaryPrimaryOverlayEnabled(false)
        repository.setWindOverlayEnabled(false)
        repository.setWindDisplayMode(FORECAST_WIND_DISPLAY_MODE_DEFAULT)
        repository.setSelectedPrimaryParameterId(DEFAULT_FORECAST_PARAMETER_ID)
        repository.setSelectedSecondaryPrimaryParameterId(DEFAULT_FORECAST_SECONDARY_PRIMARY_PARAMETER_ID)
        repository.setSelectedWindParameterId(DEFAULT_FORECAST_WIND_PARAMETER_ID)
        repository.setSelectedTimeUtcMs(null)
        repository.setSelectedRegion(DEFAULT_FORECAST_REGION_CODE)
        repository.setFollowTimeOffsetMinutes(FORECAST_FOLLOW_TIME_OFFSET_MINUTES_DEFAULT)
        repository.setAutoTimeEnabled(FORECAST_AUTO_TIME_DEFAULT)
    }

    @After
    fun tearDown() {
        context.filesDir.resolve("datastore")?.takeIf { it.exists() }?.deleteRecursively()
    }

    @Test
    fun toggle_unselectedParameter_enablesSecondarySelection() = runTest {
        val repository = ForecastPreferencesRepository(context)
        val useCase = ToggleForecastPrimaryOverlaySelectionUseCase(
            preferencesRepository = repository,
            catalogPort = PrimaryOnlyCatalogPort()
        )

        useCase(ForecastParameterId("accrain"))
        val current = repository.currentPreferences()

        assertEquals("wstar_bsratio", current.selectedPrimaryParameterId.value)
        assertTrue(current.secondaryPrimaryOverlayEnabled)
        assertEquals("accrain", current.selectedSecondaryPrimaryParameterId.value)
    }

    @Test
    fun toggle_selectedSecondary_disablesSecondarySelection() = runTest {
        val repository = ForecastPreferencesRepository(context)
        val useCase = ToggleForecastPrimaryOverlaySelectionUseCase(
            preferencesRepository = repository,
            catalogPort = PrimaryOnlyCatalogPort()
        )

        repository.setSelectedPrimaryParameterId(ForecastParameterId("wstar_bsratio"))
        repository.setSelectedSecondaryPrimaryParameterId(ForecastParameterId("accrain"))
        repository.setSecondaryPrimaryOverlayEnabled(true)

        useCase(ForecastParameterId("accrain"))
        val current = repository.currentPreferences()

        assertEquals("wstar_bsratio", current.selectedPrimaryParameterId.value)
        assertFalse(current.secondaryPrimaryOverlayEnabled)
    }

    @Test
    fun toggle_selectedPrimary_withSecondaryPromotesSecondaryToPrimary() = runTest {
        val repository = ForecastPreferencesRepository(context)
        val useCase = ToggleForecastPrimaryOverlaySelectionUseCase(
            preferencesRepository = repository,
            catalogPort = PrimaryOnlyCatalogPort()
        )

        repository.setSelectedPrimaryParameterId(ForecastParameterId("wstar_bsratio"))
        repository.setSelectedSecondaryPrimaryParameterId(ForecastParameterId("accrain"))
        repository.setSecondaryPrimaryOverlayEnabled(true)

        useCase(ForecastParameterId("wstar_bsratio"))
        val current = repository.currentPreferences()

        assertEquals("accrain", current.selectedPrimaryParameterId.value)
        assertFalse(current.secondaryPrimaryOverlayEnabled)
        assertEquals("wstar_bsratio", current.selectedSecondaryPrimaryParameterId.value)
    }

    @Test
    fun toggle_selectedPrimary_withoutSecondary_keepsSingleSelection() = runTest {
        val repository = ForecastPreferencesRepository(context)
        val useCase = ToggleForecastPrimaryOverlaySelectionUseCase(
            preferencesRepository = repository,
            catalogPort = PrimaryOnlyCatalogPort()
        )

        repository.setSelectedPrimaryParameterId(ForecastParameterId("wstar_bsratio"))
        repository.setSecondaryPrimaryOverlayEnabled(false)

        useCase(ForecastParameterId("wstar_bsratio"))
        val current = repository.currentPreferences()

        assertEquals("wstar_bsratio", current.selectedPrimaryParameterId.value)
        assertFalse(current.secondaryPrimaryOverlayEnabled)
    }

    @Test
    fun toggle_thirdParameter_whenTwoAlreadySelected_keepsExistingPair() = runTest {
        val repository = ForecastPreferencesRepository(context)
        val useCase = ToggleForecastPrimaryOverlaySelectionUseCase(
            preferencesRepository = repository,
            catalogPort = PrimaryOnlyCatalogPort()
        )

        repository.setSelectedPrimaryParameterId(ForecastParameterId("wstar_bsratio"))
        repository.setSelectedSecondaryPrimaryParameterId(ForecastParameterId("accrain"))
        repository.setSecondaryPrimaryOverlayEnabled(true)

        useCase(ForecastParameterId("wblmaxmin"))
        val current = repository.currentPreferences()

        assertEquals("wstar_bsratio", current.selectedPrimaryParameterId.value)
        assertTrue(current.secondaryPrimaryOverlayEnabled)
        assertEquals("accrain", current.selectedSecondaryPrimaryParameterId.value)
    }

    private class PrimaryOnlyCatalogPort : ForecastCatalogPort {
        override suspend fun getParameters(): List<ForecastParameterMeta> = listOf(
            ForecastParameterMeta(
                id = ForecastParameterId("wstar_bsratio"),
                name = "Thermal",
                category = "Thermal",
                unitLabel = "m/s"
            ),
            ForecastParameterMeta(
                id = ForecastParameterId("accrain"),
                name = "Rain",
                category = "Precip",
                unitLabel = "mm"
            ),
            ForecastParameterMeta(
                id = ForecastParameterId("wblmaxmin"),
                name = "Convergence",
                category = "Lift",
                unitLabel = "m/s",
                supportsPointValue = false
            )
        )

        override fun getTimeSlots(
            nowUtcMs: Long,
            regionCode: String
        ): List<ForecastTimeSlot> = listOf(ForecastTimeSlot(validTimeUtcMs = nowUtcMs))
    }
}
