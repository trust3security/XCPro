package com.trust3.xcpro.map

import android.util.Log
import com.example.dfcards.FlightModeSelection
import com.trust3.xcpro.core.flight.RealTimeFlightData
import com.trust3.xcpro.common.flight.FlightMode
import com.trust3.xcpro.common.units.UnitsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
/**
 * Bridge between the map-layer UI and the flight-card SSOT ViewModel. It keeps UI-facing,
 * short-lived state (live vario data, smoothing) while delegating template/card
 * ownership to [FlightDataViewModel].
 */
class FlightDataManager(
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "FlightDataManager"
        private const val UI_NUMERIC_FRAME_MS = 1000L / 10  // ~10 Hz for numeric readouts
        private const val UI_NEEDLE_FRAME_MS = 1000L / 20   // prioritize map interaction smoothness
        private const val ALTITUDE_BUCKET_M = 0.5
        private const val VARIO_BUCKET_MS = 0.1f
        private const val VARIO_NOISE_FLOOR = 1e-3
        private const val WIND_SPEED_BUCKET_KT = 1f
        private const val WIND_DIR_BUCKET_DEG = 5f
        private const val LD_BUCKET = 0.1f
    }

    private val _liveFlightData = MutableStateFlow<RealTimeFlightData?>(null)
    val liveFlightDataFlow: StateFlow<RealTimeFlightData?> = _liveFlightData.asStateFlow()
    val displayVarioFlow: StateFlow<Float> =
        liveFlightDataFlow
            .map { data ->
                data.resolveDisplayVario(
                    varioBucketMs = VARIO_BUCKET_MS,
                    varioNoiseFloor = VARIO_NOISE_FLOOR
                )
            }
            .distinctUntilChanged()
            .throttleFrame(UI_NUMERIC_FRAME_MS)
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0f
            )

    val needleVarioFlow: StateFlow<Float> =
        liveFlightDataFlow
            .map { data ->
                (data?.displayNeedleVario ?: 0.0).toFloat()
            }
            .distinctUntilChanged()
            .throttleFrame(UI_NEEDLE_FRAME_MS)
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0f
            )

    val fastNeedleVarioFlow: StateFlow<Float> =
        liveFlightDataFlow
            .map { data ->
                (data?.displayNeedleVarioFast ?: 0.0).toFloat()
            }
            .distinctUntilChanged()
            .throttleFrame(UI_NEEDLE_FRAME_MS)
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0f
            )

    val audioNeedleVarioFlow: StateFlow<Float> =
        liveFlightDataFlow
            .map { data ->
                (data?.audioVario ?: 0.0).toFloat()
            }
            .distinctUntilChanged()
            .throttleFrame(UI_NEEDLE_FRAME_MS)
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0f
            )

    val teArcVarioFlow: StateFlow<Float?> =
        liveFlightDataFlow
            .map { data -> data?.teVario?.toFloat() }
            .distinctUntilChanged()
            .throttleFrame(UI_NEEDLE_FRAME_MS)
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null
            )

    val baselineDisplayVarioFlow: StateFlow<Float> =
        liveFlightDataFlow
            .map { (it?.baselineDisplayVario ?: 0.0).toFloat().bucket(VARIO_BUCKET_MS) }
            .distinctUntilChanged()
            .throttleFrame(UI_NUMERIC_FRAME_MS)
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0f
            )

    val nettoDisplayFlow: StateFlow<Float> =
        liveFlightDataFlow
            .map { (it?.displayNetto ?: 0.0).toFloat().bucket(VARIO_BUCKET_MS) }
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
            .map { (it?.pilotCurrentLD ?: 0f).bucket(LD_BUCKET) }
            .distinctUntilChanged()
            .throttleFrame(UI_NUMERIC_FRAME_MS)
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0f
            )

    val windIndicatorStateFlow: StateFlow<WindIndicatorState> =
        liveFlightDataFlow
            .scan(WindIndicatorState()) { previous, data ->
                deriveWindIndicatorState(
                    previous = previous,
                    data = data
                )
            }
            .distinctUntilChanged()
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = WindIndicatorState()
            )

    /**
     * Bucketed snapshot for UI/cards; cadence is owned by dfcards tiers.
     */
    val cardFlightDataFlow: StateFlow<RealTimeFlightData?> =
        liveFlightDataFlow
            .map {
                it?.toDisplayBucket(
                    varioBucketMs = VARIO_BUCKET_MS,
                    altitudeBucketM = ALTITUDE_BUCKET_M,
                    windSpeedBucketKt = WIND_SPEED_BUCKET_KT,
                    windDirBucketDeg = WIND_DIR_BUCKET_DEG,
                    ldBucket = LD_BUCKET
                )
            }
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
    // Compatibility shim: MapScreenViewModel mirrors the resolved effective mode here while
    // card/display consumers still depend on FlightDataManager as a sink; remove after those
    // consumers read map-owned effective mode directly.
    internal var effectiveFlightModeSelection: FlightModeSelection = FlightModeSelection.CRUISE
        private set

    private val _showCardLibrary = MutableStateFlow(false)
    val showCardLibraryFlow: StateFlow<Boolean> = _showCardLibrary.asStateFlow()
    val showCardLibrary: Boolean
        get() = _showCardLibrary.value

    private val _unitsPreferences = MutableStateFlow(UnitsPreferences())
    val unitsPreferencesFlow: StateFlow<UnitsPreferences> = _unitsPreferences.asStateFlow()
    val unitsPreferences: UnitsPreferences
        get() = _unitsPreferences.value

    private var bufferedCardSample: RealTimeFlightData? = null

    fun updateEffectiveFlightModeFromEnum(newMode: FlightMode) {
        effectiveFlightModeSelection = newMode.toFlightModeSelection()
        Log.d(TAG, "Effective flight mode updated to: ${effectiveFlightModeSelection.displayName}")
    }

    fun updateUnitsPreferences(preferences: UnitsPreferences) {
        _unitsPreferences.value = preferences
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
        _showCardLibrary.value = true
    }

    fun hideCardLibrary() {
        _showCardLibrary.value = false
    }

}
