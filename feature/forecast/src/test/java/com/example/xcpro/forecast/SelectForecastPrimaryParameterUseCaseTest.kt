package com.example.xcpro.forecast

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class SelectForecastPrimaryParameterUseCaseTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = runBlocking(Dispatchers.IO) {
        val repository = ForecastPreferencesRepository(context)
        repository.setOverlayEnabled(false)
        repository.setOpacity(FORECAST_OPACITY_DEFAULT)
        repository.setWindOverlayScale(FORECAST_WIND_OVERLAY_SCALE_DEFAULT)
        repository.setWindOverlayEnabled(false)
        repository.setWindDisplayMode(FORECAST_WIND_DISPLAY_MODE_DEFAULT)
        repository.setSelectedPrimaryParameterId(DEFAULT_FORECAST_PARAMETER_ID)
        repository.setSelectedWindParameterId(DEFAULT_FORECAST_WIND_PARAMETER_ID)
        repository.setSelectedTimeUtcMs(null)
        repository.setSelectedRegion(DEFAULT_FORECAST_REGION_CODE)
        repository.setFollowTimeOffsetMinutes(FORECAST_FOLLOW_TIME_OFFSET_MINUTES_DEFAULT)
        repository.setAutoTimeEnabled(FORECAST_AUTO_TIME_DEFAULT)
    }

    @Test
    fun select_validPrimary_setsRequestedAsPrimary() = runTest {
        val repository = ForecastPreferencesRepository(context)
        val useCase = SelectForecastPrimaryParameterUseCase(
            preferencesRepository = repository,
            catalogPort = PrimaryCatalogPort()
        )

        useCase(ForecastParameterId("accrain"))

        assertEquals("accrain", repository.currentPreferences().selectedPrimaryParameterId.value)
    }

    @Test
    fun select_selectedPrimary_keepsSingleSelection() = runTest {
        val repository = ForecastPreferencesRepository(context)
        val useCase = SelectForecastPrimaryParameterUseCase(
            preferencesRepository = repository,
            catalogPort = PrimaryCatalogPort()
        )

        repository.setSelectedPrimaryParameterId(ForecastParameterId("wstar_bsratio"))
        useCase(ForecastParameterId("wstar_bsratio"))

        assertEquals("wstar_bsratio", repository.currentPreferences().selectedPrimaryParameterId.value)
    }

    @Test
    fun select_unknownPrimary_ignoresRequest() = runTest {
        val repository = ForecastPreferencesRepository(context)
        val useCase = SelectForecastPrimaryParameterUseCase(
            preferencesRepository = repository,
            catalogPort = PrimaryCatalogPort()
        )

        repository.setSelectedPrimaryParameterId(ForecastParameterId("wstar_bsratio"))
        useCase(ForecastParameterId("unknown"))

        assertEquals("wstar_bsratio", repository.currentPreferences().selectedPrimaryParameterId.value)
    }

    @Test
    fun select_windParameter_ignoresRequest() = runTest {
        val repository = ForecastPreferencesRepository(context)
        val useCase = SelectForecastPrimaryParameterUseCase(
            preferencesRepository = repository,
            catalogPort = PrimaryCatalogPort()
        )

        repository.setSelectedPrimaryParameterId(ForecastParameterId("dwcrit"))
        useCase(ForecastParameterId("sfcwind0"))

        assertEquals("dwcrit", repository.currentPreferences().selectedPrimaryParameterId.value)
    }

    @Test
    fun select_skySightHiddenPrimary_setsRequestedAsPrimary() = runTest {
        val repository = ForecastPreferencesRepository(context)
        val useCase = SelectForecastPrimaryParameterUseCase(
            preferencesRepository = repository,
            catalogPort = PrimaryCatalogPort()
        )

        repository.setSelectedPrimaryParameterId(ForecastParameterId("dwcrit"))
        useCase(ForecastParameterId("wblmaxmin"))

        assertEquals("wblmaxmin", repository.currentPreferences().selectedPrimaryParameterId.value)
    }

    @Test
    fun select_rainParameter_updatesPrimarySelection() = runTest {
        val repository = ForecastPreferencesRepository(context)
        val useCase = SelectForecastPrimaryParameterUseCase(
            preferencesRepository = repository,
            catalogPort = PrimaryCatalogPort()
        )

        repository.setSelectedPrimaryParameterId(ForecastParameterId("dwcrit"))
        useCase(ForecastParameterId("accrain"))

        assertEquals("accrain", repository.currentPreferences().selectedPrimaryParameterId.value)
    }

    private class PrimaryCatalogPort : ForecastCatalogPort {
        override suspend fun getParameters(): List<ForecastParameterMeta> = listOf(
            ForecastParameterMeta(
                id = ForecastParameterId("wstar_bsratio"),
                name = "Thermal",
                category = "Thermal",
                unitLabel = "m/s"
            ),
            ForecastParameterMeta(
                id = ForecastParameterId("dwcrit"),
                name = "Thermal Height",
                category = "Thermal",
                unitLabel = "m"
            ),
            ForecastParameterMeta(
                id = ForecastParameterId("wblmaxmin"),
                name = "Convergence",
                category = "Lift",
                unitLabel = "m/s",
                supportsPointValue = false
            ),
            ForecastParameterMeta(
                id = ForecastParameterId("accrain"),
                name = "Rain",
                category = "Precip",
                unitLabel = "mm"
            ),
            ForecastParameterMeta(
                id = ForecastParameterId("sfcwind0"),
                name = "Surface Wind",
                category = "wind",
                unitLabel = "kt"
            )
        )

        override fun getTimeSlots(
            nowUtcMs: Long,
            regionCode: String
        ): List<ForecastTimeSlot> = listOf(ForecastTimeSlot(validTimeUtcMs = nowUtcMs))
    }
}
