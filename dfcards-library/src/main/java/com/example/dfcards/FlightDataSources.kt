package com.example.dfcards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.trust3.xcpro.core.flight.RealTimeFlightData

/**
 * PHASE 3: FlightDataProvider - simplified interface (fallback removed)
 *
 * Legacy in XCPro: cards ingest via CardIngestionCoordinator + FlightDataViewModel.
 *
 * This composable receives a data provider lambda that emits RealTimeFlightData.
 * The conversion from CompleteFlightData (new system) happens in the app module.
 *
 * FLOW: FlightDataCalculator -> CompleteFlightData -> [Adapter in app module] -> RealTimeFlightData -> Cards
 */
@Composable
@Deprecated(
    "Legacy: XCPro uses CardIngestionCoordinator + FlightDataViewModel.updateCardsWithLiveData",
    level = DeprecationLevel.WARNING
)
fun FlightDataProvider(
    dataProvider: suspend ((RealTimeFlightData) -> Unit) -> Unit,
    onDataReceived: (RealTimeFlightData) -> Unit
) {
    LaunchedEffect(Unit) {
        dataProvider { data ->
            onDataReceived(data)
        }
    }
}

