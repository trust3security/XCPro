package com.trust3.xcpro.variometer.bluetooth.lxnav

import com.trust3.xcpro.bluetooth.BluetoothReadChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LxSentenceSessionTest {

    @Test
    fun chunk_to_line_to_parse_outcome_to_snapshot_composes_successfully() {
        val session = LxSentenceSession()

        val outcomes = session.onChunk(
            chunk(withChecksum("LXWP0,Y,88.4,654.1,1.12") + "\n", 100L)
        )

        assertEquals(1, outcomes.size)
        assertTrue(outcomes.single() is LxParseOutcome.Accepted)
        assertEquals(
            LxDeviceSnapshot(
                airspeedKph = 88.4,
                pressureAltitudeM = 654.1,
                totalEnergyVarioMps = 1.12,
                deviceInfo = null,
                lastAcceptedSentenceId = LxSentenceId.LXWP0,
                lastAcceptedMonoMs = 100L
            ),
            session.currentSnapshot
        )
    }

    @Test
    fun cross_chunk_line_completion_uses_existing_framer() {
        val session = LxSentenceSession()

        val first = session.onChunk(chunk("\$LXWP0,Y,88.", 200L))
        val second = session.onChunk(chunk("4,654.1,1.12\n", 201L))

        assertTrue(first.isEmpty())
        assertEquals(1, second.size)
        assertEquals(201L, session.currentSnapshot.lastAcceptedMonoMs ?: -1L)
        assertEquals(88.4, session.currentSnapshot.airspeedKph ?: Double.NaN, 0.0)
    }

    @Test
    fun multiple_lines_in_one_chunk_are_all_processed() {
        val session = LxSentenceSession()

        val outcomes = session.onChunk(
            chunk(
                text = buildString {
                    append(withChecksum("LXWP0,Y,88.4,654.1,1.12"))
                    append('\n')
                    append(withChecksum("LXWP1,S100,123,1.0,2.0"))
                    append('\n')
                },
                receivedMonoMs = 300L
            )
        )

        assertEquals(2, outcomes.size)
        assertEquals(
            LxDeviceInfo(
                product = "S100",
                serial = "123",
                softwareVersion = "1.0",
                hardwareVersion = "2.0"
            ),
            session.currentSnapshot.deviceInfo
        )
        assertEquals(LxSentenceId.LXWP1, session.currentSnapshot.lastAcceptedSentenceId)
    }

    @Test
    fun accepted_plxvf_sentence_updates_supported_snapshot_fields() {
        val session = LxSentenceSession()

        val outcomes = session.onChunk(
            chunk(withChecksum("PLXVF,,1.00,0.87,-0.12,-0.25,90.2,244.3") + "\n", 400L)
        )

        assertEquals(
            listOf(
                LxParseOutcome.Accepted(
                    PlxVfSentence(
                        provisionalVarioMps = -0.25,
                        indicatedAirspeedKph = 90.2,
                        pressureAltitudeM = 244.3,
                        checksumStatus = ChecksumStatus.VALID,
                        receivedMonoMs = 400L
                    )
                )
            ),
            outcomes
        )
        assertEquals(
            LxDeviceSnapshot(
                airspeedKph = 90.2,
                pressureAltitudeM = 244.3,
                externalVarioMps = -0.25,
                totalEnergyVarioMps = null,
                deviceInfo = null,
                lastAcceptedSentenceId = LxSentenceId.PLXVF,
                lastAcceptedMonoMs = 400L
            ),
            session.currentSnapshot
        )
    }

    @Test
    fun rejected_sentence_does_not_mutate_snapshot() {
        val session = LxSentenceSession()

        val outcomes = session.onChunk(chunk("\$LXWP0,Y,not-a-number,654.1,1.12\n", 500L))

        assertEquals(
            listOf(
                LxParseOutcome.Rejected(
                    reason = LxRejectedReason.MALFORMED_FIELDS,
                    sentenceId = LxSentenceId.LXWP0,
                    receivedMonoMs = 500L,
                    checksumStatus = ChecksumStatus.MISSING
                )
            ),
            outcomes
        )
        assertEquals(LxDeviceSnapshot(), session.currentSnapshot)
    }

    @Test
    fun reset_clears_snapshot_and_partial_framer_state() {
        val session = LxSentenceSession()

        session.onChunk(chunk("\$LXWP0,Y,88.", 600L))
        session.reset()
        val outcomes = session.onChunk(chunk("4,654.1,1.12\n", 601L))

        assertEquals(
            listOf(
                LxParseOutcome.Rejected(
                    reason = LxRejectedReason.MISSING_PREFIX,
                    sentenceId = LxSentenceId.UNKNOWN,
                    receivedMonoMs = 601L,
                    checksumStatus = null
                )
            ),
            outcomes
        )
        assertEquals(LxDeviceSnapshot(), session.currentSnapshot)
    }

    @Test
    fun parser_rejection_stays_in_parser_not_in_framer_path() {
        val session = LxSentenceSession()

        val outcomes = session.onChunk(chunk("NOT_A_SENTENCE\n", 700L))

        assertEquals(1, outcomes.size)
        assertEquals(
            LxParseOutcome.Rejected(
                reason = LxRejectedReason.MISSING_PREFIX,
                sentenceId = LxSentenceId.UNKNOWN,
                receivedMonoMs = 700L,
                checksumStatus = null
            ),
            outcomes.single()
        )
        assertEquals(LxDeviceSnapshot(), session.currentSnapshot)
    }

    @Test
    fun malformed_burst_does_not_prevent_later_valid_recovery() {
        val session = LxSentenceSession()

        val outcomes = session.onChunk(
            chunk(
                text = buildString {
                    append("\$LXWP0,Y,not-a-number,654.1,1.12\n")
                    append("\$LXWP0,Y,88.4,654.1,1.12*00\n")
                    append(withChecksum("LXWP0,Y,90.0,700.0,1.50"))
                    append('\n')
                },
                receivedMonoMs = 800L
            )
        )

        assertEquals(3, outcomes.size)
        assertEquals(90.0, session.currentSnapshot.airspeedKph ?: Double.NaN, 0.0)
        assertEquals(700.0, session.currentSnapshot.pressureAltitudeM ?: Double.NaN, 0.0)
        assertEquals(1.5, session.currentSnapshot.totalEnergyVarioMps ?: Double.NaN, 0.0)
        assertEquals(800L, session.currentSnapshot.lastAcceptedMonoMs ?: -1L)
    }

    private fun chunk(text: String, receivedMonoMs: Long): BluetoothReadChunk =
        BluetoothReadChunk(
            bytes = text.toByteArray(Charsets.US_ASCII),
            receivedMonoMs = receivedMonoMs
        )

    private fun withChecksum(payloadWithoutDollar: String): String {
        val checksum = payloadWithoutDollar.fold(0) { acc, character -> acc xor character.code }
        return "\$$payloadWithoutDollar*${checksum.toString(16).uppercase().padStart(2, '0')}"
    }
}

