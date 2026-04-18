package com.trust3.xcpro.variometer.bluetooth.lxnav

import com.trust3.xcpro.variometer.bluetooth.BluetoothReadChunk
import com.trust3.xcpro.variometer.bluetooth.NmeaLineFramer

class LxSentenceSession(
    private val parser: LxSentenceParser = LxSentenceParser(),
    private val framer: NmeaLineFramer = NmeaLineFramer(),
    private val reducer: LxDeviceSnapshotReducer = LxDeviceSnapshotReducer()
) {
    var currentSnapshot: LxDeviceSnapshot = reducer.empty()
        private set

    fun onChunk(chunk: BluetoothReadChunk): List<LxParseOutcome> {
        val outcomes = ArrayList<LxParseOutcome>()
        for (line in framer.append(chunk)) {
            val outcome = parser.parse(line)
            outcomes += outcome
            if (outcome is LxParseOutcome.Accepted) {
                currentSnapshot = reducer.reduce(currentSnapshot, outcome)
            }
        }
        return outcomes
    }

    fun reset() {
        currentSnapshot = reducer.empty()
        framer.reset()
    }
}
