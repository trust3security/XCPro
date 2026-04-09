package com.example.xcpro.variometer.bluetooth.lxnav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LxDeviceSnapshotReducerTest {

    private val reducer = LxDeviceSnapshotReducer()

    @Test
    fun merge_lxwp0_then_lxwp1() {
        val afterWp0 = reducer.reduce(
            previous = reducer.empty(),
            accepted = accepted(
                LxWp0Sentence(
                    airspeedKph = 88.4,
                    pressureAltitudeM = 654.1,
                    totalEnergyVarioMps = 1.12,
                    checksumStatus = ChecksumStatus.MISSING,
                    receivedMonoMs = 100L
                )
            )
        )

        val afterWp1 = reducer.reduce(
            previous = afterWp0,
            accepted = accepted(
                LxWp1Sentence(
                    deviceInfo = LxDeviceInfo(
                        product = "S100",
                        serial = "123",
                        softwareVersion = "1.0",
                        hardwareVersion = "2.0"
                    ),
                    checksumStatus = ChecksumStatus.MISSING,
                    receivedMonoMs = 200L
                )
            )
        )

        assertEquals(88.4, afterWp1.airspeedKph ?: Double.NaN, 0.0)
        assertEquals(654.1, afterWp1.pressureAltitudeM ?: Double.NaN, 0.0)
        assertEquals(1.12, afterWp1.totalEnergyVarioMps ?: Double.NaN, 0.0)
        assertEquals(
            LxDeviceInfo(
                product = "S100",
                serial = "123",
                softwareVersion = "1.0",
                hardwareVersion = "2.0"
            ),
            afterWp1.deviceInfo
        )
    }

    @Test
    fun blank_lxwp0_fields_do_not_clear_prior_values() {
        val previous = LxDeviceSnapshot(
            airspeedKph = 88.4,
            pressureAltitudeM = 654.1,
            totalEnergyVarioMps = 1.12
        )

        val next = reducer.reduce(
            previous = previous,
            accepted = accepted(
                LxWp0Sentence(
                    airspeedKph = null,
                    pressureAltitudeM = 700.0,
                    totalEnergyVarioMps = null,
                    checksumStatus = ChecksumStatus.MISSING,
                    receivedMonoMs = 300L
                )
            )
        )

        assertEquals(88.4, next.airspeedKph ?: Double.NaN, 0.0)
        assertEquals(700.0, next.pressureAltitudeM ?: Double.NaN, 0.0)
        assertEquals(1.12, next.totalEnergyVarioMps ?: Double.NaN, 0.0)
    }

    @Test
    fun blank_lxwp1_fields_preserve_prior_device_info_values() {
        val previous = LxDeviceSnapshot(
            deviceInfo = LxDeviceInfo(
                product = "S100",
                serial = "123",
                softwareVersion = "1.0",
                hardwareVersion = "2.0"
            )
        )

        val next = reducer.reduce(
            previous = previous,
            accepted = accepted(
                LxWp1Sentence(
                    deviceInfo = LxDeviceInfo(
                        product = null,
                        serial = "456",
                        softwareVersion = null,
                        hardwareVersion = null
                    ),
                    checksumStatus = ChecksumStatus.MISSING,
                    receivedMonoMs = 400L
                )
            )
        )

        assertEquals(
            LxDeviceInfo(
                product = "S100",
                serial = "456",
                softwareVersion = "1.0",
                hardwareVersion = "2.0"
            ),
            next.deviceInfo
        )
    }

    @Test
    fun last_accepted_sentence_id_updates_correctly() {
        val next = reducer.reduce(
            previous = reducer.empty(),
            accepted = accepted(
                LxWp1Sentence(
                    deviceInfo = LxDeviceInfo(product = "S100"),
                    checksumStatus = ChecksumStatus.MISSING,
                    receivedMonoMs = 500L
                )
            )
        )

        assertEquals(LxSentenceId.LXWP1, next.lastAcceptedSentenceId)
    }

    @Test
    fun last_accepted_mono_ms_updates_correctly() {
        val next = reducer.reduce(
            previous = reducer.empty(),
            accepted = accepted(
                LxWp0Sentence(
                    airspeedKph = 80.0,
                    pressureAltitudeM = null,
                    totalEnergyVarioMps = null,
                    checksumStatus = ChecksumStatus.MISSING,
                    receivedMonoMs = 600L
                )
            )
        )

        assertEquals(600L, next.lastAcceptedMonoMs ?: -1L)
    }

    @Test
    fun empty_snapshot_behavior_is_default_empty_snapshot() {
        val empty = reducer.empty()

        assertEquals(LxDeviceSnapshot(), empty)
        assertNull(empty.deviceInfo)
        assertNull(empty.lastAcceptedSentenceId)
        assertNull(empty.lastAcceptedMonoMs)
    }

    private fun accepted(sentence: ParsedLxSentence): LxParseOutcome.Accepted =
        LxParseOutcome.Accepted(sentence)
}
