package com.trust3.xcpro.variometer.bluetooth.lxnav

class LxDeviceSnapshotReducer {

    fun empty(): LxDeviceSnapshot = LxDeviceSnapshot()

    fun reduce(
        previous: LxDeviceSnapshot,
        accepted: LxParseOutcome.Accepted
    ): LxDeviceSnapshot {
        val sentence = accepted.sentence
        return when (sentence) {
            is LxWp0Sentence -> previous.copy(
                airspeedKph = sentence.airspeedKph ?: previous.airspeedKph,
                pressureAltitudeM = sentence.pressureAltitudeM ?: previous.pressureAltitudeM,
                totalEnergyVarioMps = sentence.totalEnergyVarioMps ?: previous.totalEnergyVarioMps,
                lastAcceptedSentenceId = sentence.sentenceId,
                lastAcceptedMonoMs = sentence.receivedMonoMs
            )

            is LxWp1Sentence -> previous.copy(
                deviceInfo = mergeDeviceInfo(previous.deviceInfo, sentence.deviceInfo),
                lastAcceptedSentenceId = sentence.sentenceId,
                lastAcceptedMonoMs = sentence.receivedMonoMs
            )
        }
    }

    private fun mergeDeviceInfo(
        previous: LxDeviceInfo?,
        incoming: LxDeviceInfo
    ): LxDeviceInfo? {
        val merged = LxDeviceInfo(
            product = incoming.product ?: previous?.product,
            serial = incoming.serial ?: previous?.serial,
            softwareVersion = incoming.softwareVersion ?: previous?.softwareVersion,
            hardwareVersion = incoming.hardwareVersion ?: previous?.hardwareVersion
        )
        return merged.takeUnless { it.isEmpty() }
    }
}
