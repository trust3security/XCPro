package com.example.xcpro.map

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.common.units.UnitsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
/**
 * Bridge between the map-layer UI and the flight-card SSOT ViewModel. It keeps UI-facing,
 * short-lived state (live vario data, smoothing, visibility) while delegating template/card
 * ownership to [FlightDataViewModel].
 */
class FlightDataManager(
    private val context: Context,
  	private val cardPreferences: CardPreferences,
  	private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "FlightDataManager"
    }

    /**
     * Latest flight data sample after UI smoothing; this is what downstream collectors should consume
     * when they want values that match on-screen overlays.
     */
    var liveFlightData by mutableStateOf<RealTimeFlightData?>(null)
        private set
    /**
     * Raw sample emitted by the calculation pipeline before any additional smoothing. Exposed so
     * analytics or diagnostics can compare against the UI-filtered stream without re-wiring the
     * calculator.
     */
    var rawFlightData by mutableStateOf<RealTimeFlightData?>(null)
        private set
    var currentFlightMode by mutableStateOf(FlightModeSelection.CRUISE)
        private set

    var showCardLibrary by mutableStateOf(false)
        private set

    var visibleModes by mutableStateOf(listOf(FlightMode.CRUISE))
        private set

    var unitsPreferences by mutableStateOf(UnitsPreferences())
        private set

    private var bufferedCardSample: RealTimeFlightData? = null

    fun mapToFlightModeSelection(mode: FlightMode): FlightModeSelection =
        when (mode) {
            FlightMode.CRUISE -> FlightModeSelection.CRUISE
            FlightMode.THERMAL -> FlightModeSelection.THERMAL
            FlightMode.FINAL_GLIDE -> FlightModeSelection.FINAL_GLIDE
        }

    fun mapToFlightMode(modeSelection: FlightModeSelection): FlightMode =
        when (modeSelection) {
            FlightModeSelection.CRUISE -> FlightMode.CRUISE
            FlightModeSelection.THERMAL -> FlightMode.THERMAL
            FlightModeSelection.FINAL_GLIDE -> FlightMode.FINAL_GLIDE
        }

    fun updateFlightMode(newMode: FlightModeSelection) {
        currentFlightMode = newMode
        Log.d(TAG, "Flight mode updated to: ${newMode.displayName}")
    }

    fun updateFlightModeFromEnum(newMode: FlightMode) {
        currentFlightMode = mapToFlightModeSelection(newMode)
        Log.d(TAG, "Flight mode updated from enum to: ${currentFlightMode.displayName}")
    }

    suspend fun loadVisibleModes(profileId: String?, profileName: String?) {
        if (profileId == null) {
            Log.d(TAG, "No profileId provided - keeping existing visible modes")
            return
        }

        val visibilities = cardPreferences.getProfileAllFlightModeVisibilities(profileId).first()
        val filtered = mutableListOf<FlightMode>()
        filtered.add(FlightMode.CRUISE)
        if (visibilities["THERMAL"] != false) filtered.add(FlightMode.THERMAL)
        if (visibilities["FINAL_GLIDE"] != false) filtered.add(FlightMode.FINAL_GLIDE)
        visibleModes = filtered
        Log.d(TAG, "Visible modes for profile '$profileName': ${filtered.map { it.name }}")
    }

    fun updateUnitsPreferences(preferences: UnitsPreferences) {
        unitsPreferences = preferences
        Log.d(TAG, "Units updated to $preferences")
    }

    fun updateLiveFlightData(newData: RealTimeFlightData?) {
        if (newData == null) {
            liveFlightData = null
            rawFlightData = null
            bufferedCardSample = null
            return
        }

        rawFlightData = newData
        liveFlightData = newData
        bufferedCardSample = liveFlightData
    }

    fun consumeBufferedCardSample(): RealTimeFlightData? =
        bufferedCardSample?.also { bufferedCardSample = null }

    fun showCardLibrary() {
        showCardLibrary = true
    }

    fun hideCardLibrary() {
        showCardLibrary = false
    }

    fun isCurrentModeVisible(currentMode: FlightMode): Boolean =
        currentMode in visibleModes

    fun getFallbackMode(): FlightMode = FlightMode.CRUISE

}

