package com.trust3.xcpro.hawk

import kotlinx.coroutines.flow.Flow

/**
 * Narrow runtime read port exposing only the HAWK audio-vario sample stream
 * needed by the flight fusion pipeline.
 */
interface HawkAudioVarioReadPort {
    val audioVarioMps: Flow<Double?>
}
