package com.example.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.map.MapScreenViewModel
import com.example.xcpro.map.MapStateReader
import com.example.xcpro.map.MapStateStore
import com.example.xcpro.map.trail.TrailSettings
import com.example.xcpro.replay.SessionState
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.sensors.GpsStatus
import com.example.xcpro.sensors.domain.FlyingState

internal data class MapScreenBindings(
    val gpsStatus: GpsStatus,
    val showRecenterButton: Boolean,
    val showReturnButton: Boolean,
    val currentMode: FlightMode,
    val currentZoom: Float,
    val replaySession: SessionState,
    val suppressLiveGps: Boolean,
    val allowSensorStart: Boolean,
    val locationForUi: GPSData?,
    val trailSettings: TrailSettings,
    val flightState: FlyingState,
    val liveFlightData: RealTimeFlightData?,
    val isAATEditMode: Boolean,
    val savedLocation: MapStateStore.MapPoint?,
    val savedZoom: Double?,
    val savedBearing: Double?,
    val hasInitiallyCentered: Boolean,
    val showDistanceCircles: Boolean,
    val cardHydrationReady: Boolean
)

@Composable
internal fun rememberMapScreenBindings(
    mapViewModel: MapScreenViewModel,
    mapStateReader: MapStateReader,
    flightDataManager: FlightDataManager
): MapScreenBindings {
    val gpsStatus by mapViewModel.gpsStatusFlow.collectAsStateWithLifecycle()
    val showRecenterButton by mapStateReader.showRecenterButton.collectAsStateWithLifecycle()
    val showReturnButton by mapStateReader.showReturnButton.collectAsStateWithLifecycle()
    val currentMode by mapStateReader.currentMode.collectAsStateWithLifecycle()
    val currentZoom by mapStateReader.currentZoom.collectAsStateWithLifecycle()
    val replaySession by mapViewModel.replaySessionState.collectAsStateWithLifecycle()
    val suppressLiveGps by mapViewModel.suppressLiveGps.collectAsStateWithLifecycle()
    val allowSensorStart by mapViewModel.allowSensorStart.collectAsStateWithLifecycle()
    val locationForUi by mapViewModel.mapLocation.collectAsStateWithLifecycle()
    val trailSettings by mapStateReader.trailSettings.collectAsStateWithLifecycle()
    val flightState by mapViewModel.varioServiceManager.flightStateSource.flightState.collectAsStateWithLifecycle()
    val liveFlightData by flightDataManager.liveFlightDataFlow.collectAsStateWithLifecycle()
    val isAATEditMode by mapViewModel.isAATEditMode.collectAsStateWithLifecycle()
    val savedLocation by mapStateReader.savedLocation.collectAsStateWithLifecycle()
    val savedZoom by mapStateReader.savedZoom.collectAsStateWithLifecycle()
    val savedBearing by mapStateReader.savedBearing.collectAsStateWithLifecycle()
    val hasInitiallyCentered by mapStateReader.hasInitiallyCentered.collectAsStateWithLifecycle()
    val showDistanceCircles by mapStateReader.showDistanceCircles.collectAsStateWithLifecycle()
    val cardHydrationReady by mapViewModel.cardHydrationReady.collectAsStateWithLifecycle()

    return MapScreenBindings(
        gpsStatus = gpsStatus,
        showRecenterButton = showRecenterButton,
        showReturnButton = showReturnButton,
        currentMode = currentMode,
        currentZoom = currentZoom,
        replaySession = replaySession,
        suppressLiveGps = suppressLiveGps,
        allowSensorStart = allowSensorStart,
        locationForUi = locationForUi,
        trailSettings = trailSettings,
        flightState = flightState,
        liveFlightData = liveFlightData,
        isAATEditMode = isAATEditMode,
        savedLocation = savedLocation,
        savedZoom = savedZoom,
        savedBearing = savedBearing,
        hasInitiallyCentered = hasInitiallyCentered,
        showDistanceCircles = showDistanceCircles,
        cardHydrationReady = cardHydrationReady
    )
}
