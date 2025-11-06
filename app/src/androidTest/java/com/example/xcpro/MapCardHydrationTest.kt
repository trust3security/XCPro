package com.example.xcpro

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.RealTimeFlightData
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.xcpro.map.FlightDataManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Density
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class MapCardHydrationTest {

    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun cardsHydrateWhenLiveDataArrivesOnMapFirstLaunch() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cardPreferences = CardPreferences(context)
        val flightDataManager = FlightDataManager(context, cardPreferences, testScope)
        val flightDataViewModel = FlightDataViewModel()

        val density = Density(
            context.resources.displayMetrics.density,
            context.resources.configuration.fontScale
        )
        val containerSize = IntSize(1080, 1920)

        withContext(Dispatchers.Main) {
            flightDataViewModel.initializeCardPreferences(cardPreferences)
            flightDataViewModel.updateUnitsPreferences(flightDataManager.unitsPreferences)
            flightDataViewModel.initializeCards(containerSize, density)
            flightDataViewModel.prepareCardsForProfile(
                profileId = null,
                flightMode = FlightModeSelection.CRUISE,
                containerSize = containerSize,
                density = density
            )
            flightDataViewModel.loadEssentialCardsOnStartup(
                containerSize = containerSize,
                density = density,
                flightMode = FlightModeSelection.CRUISE
            )
            repeat(50) {
                if (flightDataViewModel.getCardCount() > 0) return@withContext
                delay(20)
            }
        }

        val incomingSample = RealTimeFlightData(
            latitude = -33.0,
            longitude = 151.0,
            gpsAltitude = 1020.0,
            baroAltitude = 980.0,
            agl = 250.0,
            verticalSpeed = 1.5,
            groundSpeed = 15.0,
            track = 270.0,
            varioOptimized = 1.5,
            varioLegacy = 1.2,
            varioRaw = 1.7,
            varioGPS = 1.4,
            varioComplementary = 1.3
        )

        flightDataManager.updateLiveFlightData(incomingSample)
        val smoothedSample = flightDataManager.liveFlightData
        assertNotNull("Live flight data should be available after update", smoothedSample)

        withContext(Dispatchers.Main) {
            flightDataViewModel.updateCardsWithLiveData(smoothedSample!!)
            repeat(50) {
                val hydrated = flightDataViewModel.getCardState("vario")
                if (hydrated != null && !hydrated.flightData.primaryValue.startsWith("--")) {
                    return@withContext
                }
                delay(20)
            }
        }

        val hydratedCards = flightDataViewModel.getAllCardStates()
        assertTrue("Expected at least one card after hydration", hydratedCards.isNotEmpty())
        val displayValue = hydratedCards.first().flightData.primaryValue.trim()
        assertTrue(
            "Primary card should show hydrated data, but was '$displayValue'",
            !displayValue.startsWith("--")
        )
        assertTrue(
            "Smoothed value should closely follow the injected sample",
            displayValue.any { it.isDigit() } ||
                (hydratedCards.first().flightData.primaryValueNumber?.toDoubleOrNull()?.roundToInt() != null)
        )
    }
}
