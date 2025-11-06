package com.example.xcpro.map

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.FlightMode
import com.example.xcpro.common.units.UnitsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlin.collections.ArrayDeque

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
    private data class VarioSample(val timestamp: Long, val value: Double)

    companion object {
        private const val TAG = "FlightDataManager"
        private const val DEFAULT_SMOOTHING_ALPHA = 0.25f
        private const val AVERAGE_WINDOW_MS = 5_000L
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
    var smoothedVerticalSpeed by mutableStateOf<Double?>(null)
        private set
    var rawVerticalSpeed by mutableStateOf<Double?>(null)
        private set
    var averagedVerticalSpeed by mutableStateOf<Double?>(null)
        private set

    private var smoothingAlpha: Double = DEFAULT_SMOOTHING_ALPHA.toDouble()

    var currentFlightMode by mutableStateOf(FlightModeSelection.CRUISE)
        private set

    var showCardLibrary by mutableStateOf(false)
        private set

    var visibleModes by mutableStateOf(listOf(FlightMode.CRUISE))
        private set

    var unitsPreferences by mutableStateOf(UnitsPreferences())
        private set

    private val recentVarioSamples = ArrayDeque<VarioSample>()
    private var recentVarioSum = 0.0

    init {
        coroutineScope.launch {
            cardPreferences.getVarioSmoothingAlpha().collect { alpha ->
                smoothingAlpha = alpha.toDouble().coerceIn(0.05, 0.95)
                smoothedVerticalSpeed = null
            }
        }
    }

    fun mapToFlightModeSelection(mode: FlightMode): FlightModeSelection =
        when (mode) {
            FlightMode.CRUISE -> FlightModeSelection.CRUISE
            FlightMode.THERMAL -> FlightModeSelection.THERMAL
            FlightMode.FINAL_GLIDE -> FlightModeSelection.FINAL_GLIDE
            FlightMode.HAWK -> FlightModeSelection.HAWK
        }

    fun mapToFlightMode(modeSelection: FlightModeSelection): FlightMode =
        when (modeSelection) {
            FlightModeSelection.CRUISE -> FlightMode.CRUISE
            FlightModeSelection.THERMAL -> FlightMode.THERMAL
            FlightModeSelection.FINAL_GLIDE -> FlightMode.FINAL_GLIDE
            FlightModeSelection.HAWK -> FlightMode.HAWK
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
        if (visibilities["HAWK"] != false) filtered.add(FlightMode.HAWK)
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
            smoothedVerticalSpeed = null
            rawVerticalSpeed = null
            averagedVerticalSpeed = null
            recentVarioSamples.clear()
            recentVarioSum = 0.0
            return
        }

        rawFlightData = newData
        val rawVs = newData.verticalSpeed
        rawVerticalSpeed = rawVs
        val previous = smoothedVerticalSpeed ?: rawVs
        val smoothed = previous + smoothingAlpha * (rawVs - previous)
        smoothedVerticalSpeed = smoothed
        liveFlightData = newData.copy(verticalSpeed = smoothed)

        updateAveragedVerticalSpeed(rawVs)
    }

    fun showCardLibrary() {
        showCardLibrary = true
    }

    fun hideCardLibrary() {
        showCardLibrary = false
    }

    fun isCurrentModeVisible(currentMode: FlightMode): Boolean =
        currentMode in visibleModes

    fun getFallbackMode(): FlightMode = FlightMode.CRUISE

    private fun updateAveragedVerticalSpeed(rawVs: Double) {
        val now = System.currentTimeMillis()
        recentVarioSamples.addLast(VarioSample(now, rawVs))
        recentVarioSum += rawVs

        while (recentVarioSamples.isNotEmpty() && now - recentVarioSamples.first().timestamp > AVERAGE_WINDOW_MS) {
            val removed = recentVarioSamples.removeFirst()
            recentVarioSum -= removed.value
        }

        averagedVerticalSpeed = if (recentVarioSamples.isNotEmpty()) {
            recentVarioSum / recentVarioSamples.size
        } else {
            null
        }
    }

}
