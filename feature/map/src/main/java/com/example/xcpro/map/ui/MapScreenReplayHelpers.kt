package com.example.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.SpeedMs
import com.example.xcpro.sensors.GPSData
import org.maplibre.android.geometry.LatLng

/**
 * Replay helpers extracted from MapScreenRoot to keep files under the 500-line limit.
 */
@Composable
internal fun rememberReplayGpsLocation(replayFlightData: RealTimeFlightData?): State<GPSData?> =
    remember(replayFlightData) {
        derivedStateOf {
            replayFlightData?.let { sample ->
                if (sample.latitude == 0.0 && sample.longitude == 0.0) {
                    null
                } else {
                    GPSData(
                        latLng = LatLng(sample.latitude, sample.longitude),
                        altitude = AltitudeM(sample.gpsAltitude),
                        speed = SpeedMs(sample.groundSpeed),
                        bearing = sample.track,
                        accuracy = sample.accuracy.toFloat(),
                        timestamp = sample.timestamp
                    )
                }
            }
        }
    }
