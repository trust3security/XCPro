package com.trust3.xcpro.map.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trust3.xcpro.common.units.UnitsFormatter
import com.trust3.xcpro.common.units.UnitsPreferences
import com.trust3.xcpro.common.units.VerticalSpeedMs
import com.trust3.xcpro.map.BuildConfig
import com.trust3.xcpro.map.FlightDataManager
import com.trust3.xcpro.replay.SessionState
import com.trust3.xcpro.replay.SessionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive

@Composable
internal fun ReplayDiagnosticsLogger(
    replayState: StateFlow<SessionState>,
    flightDataManager: FlightDataManager,
    unitsPreferences: UnitsPreferences
) {
    if (!BuildConfig.DEBUG) return

    val replaySession by replayState.collectAsStateWithLifecycle()

    LaunchedEffect(replaySession.status, unitsPreferences) {
        Log.d("REPLAY_UI", "status=${replaySession.status} speed=${replaySession.speedMultiplier}")
        if (replaySession.status != SessionStatus.PLAYING) return@LaunchedEffect

        while (isActive && replayState.value.status == SessionStatus.PLAYING) {
            val live = flightDataManager.liveFlightData
            val displayMs = live?.displayVario ?: Double.NaN
            val displayUnits = if (displayMs.isFinite()) {
                unitsPreferences.verticalSpeed.fromSi(VerticalSpeedMs(displayMs))
            } else {
                Double.NaN
            }
            val label = if (displayMs.isFinite()) {
                UnitsFormatter.verticalSpeed(
                    VerticalSpeedMs(displayMs),
                    unitsPreferences
                ).text
            } else {
                "--"
            }
            Log.d(
                "REPLAY_UI",
                "displayMs=${"%.3f".format(displayMs)} displayUi=${"%.3f".format(displayUnits)} " +
                    "label=$label units=${unitsPreferences.verticalSpeed} " +
                    "valid=${live?.varioValid} src=${live?.varioSource} baseDisp=${live?.baselineDisplayVario}"
            )
            delay(1_000L)
        }
    }
}
