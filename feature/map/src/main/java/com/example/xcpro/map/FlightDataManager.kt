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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
        private const val UI_NUMERIC_FRAME_MS = 1000L / 12  // ~12 Hz for numbers
        private const val ALTITUDE_BUCKET_M = 0.5
        private const val VARIO_BUCKET_MS = 0.1f
        private const val WIND_SPEED_BUCKET_KT = 1f
        private const val WIND_DIR_BUCKET_DEG = 5f
        private const val LD_BUCKET = 0.1f
    }

    private val _liveFlightData = MutableStateFlow<RealTimeFlightData?>(null)
    val liveFlightDataFlow: StateFlow<RealTimeFlightData?> = _liveFlightData.asStateFlow()
    val displayVarioFlow: StateFlow<Float> =
        liveFlightDataFlow
            .map { (it?.displayVario ?: 0.0).toFloat().bucket(VARIO_BUCKET_MS) }
            .distinctUntilChanged()
            .throttleFrame(UI_NUMERIC_FRAME_MS)
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0f
            )

    val nettoDisplayFlow: StateFlow<Float> =
        liveFlightDataFlow
            .map { (it?.netto ?: 0f).bucket(VARIO_BUCKET_MS) }
            .distinctUntilChanged()
            .throttleFrame(UI_NUMERIC_FRAME_MS)
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0f
            )

    val altitudeDisplayFlow: StateFlow<Double> =
        liveFlightDataFlow
            .map { (it?.baroAltitude ?: 0.0).bucket(ALTITUDE_BUCKET_M) }
            .distinctUntilChanged()
            .throttleFrame(UI_NUMERIC_FRAME_MS)
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0.0
            )

    val windSpeedDisplayFlow: StateFlow<Float> =
        liveFlightDataFlow
            .map { (it?.windSpeed ?: 0f).bucket(WIND_SPEED_BUCKET_KT) }
            .distinctUntilChanged()
            .throttleFrame(UI_NUMERIC_FRAME_MS)
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0f
            )

    val windDirectionDisplayFlow: StateFlow<Float> =
        liveFlightDataFlow
            .map { (it?.windDirection ?: 0f).bucket(WIND_DIR_BUCKET_DEG) }
            .distinctUntilChanged()
            .throttleFrame(UI_NUMERIC_FRAME_MS)
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0f
            )

    val glideRatioDisplayFlow: StateFlow<Float> =
        liveFlightDataFlow
            .map { (it?.currentLD ?: 0f).bucket(LD_BUCKET) }
            .distinctUntilChanged()
            .throttleFrame(UI_NUMERIC_FRAME_MS)
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0f
            )

    /**
     * Throttled, bucketed snapshot for UI/cards; raw flow remains unthrottled for map/audio.
     */
    val cardFlightDataFlow: StateFlow<RealTimeFlightData?> =
        liveFlightDataFlow
            .map { it?.toDisplayBucket() }
            .throttleFrame(UI_NUMERIC_FRAME_MS)
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null
            )
    val latitudeFlow: StateFlow<Double> =
        liveFlightDataFlow
            .map { it?.latitude ?: 0.0 }
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0.0
            )

    /**
     * Latest flight data sample after UI smoothing; this is what downstream collectors should consume
     * when they want values that match on-screen overlays.
     */
    var liveFlightData: RealTimeFlightData?
        get() = _liveFlightData.value
        private set(value) {
            _liveFlightData.value = value
        }
    /**
     * Raw sample emitted by the calculation pipeline before any additional smoothing. Exposed so
     * analytics or diagnostics can compare against the UI-filtered stream without re-wiring the
     * calculator.
     */
    var rawFlightData: RealTimeFlightData? = null
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

    private fun RealTimeFlightData.toDisplayBucket(): RealTimeFlightData =
        copy(
            displayVario = displayVario.bucket(VARIO_BUCKET_MS.toDouble()),
            netto = netto.bucket(VARIO_BUCKET_MS),
            displayNetto = displayNetto.bucket(VARIO_BUCKET_MS.toDouble()),
            baroAltitude = baroAltitude.bucket(ALTITUDE_BUCKET_M),
            gpsAltitude = gpsAltitude.bucket(ALTITUDE_BUCKET_M),
            agl = agl.bucket(ALTITUDE_BUCKET_M),
            windSpeed = windSpeed.bucket(WIND_SPEED_BUCKET_KT),
            windDirection = windDirection.bucket(WIND_DIR_BUCKET_DEG),
            currentLD = currentLD.bucket(LD_BUCKET)
        )

}

