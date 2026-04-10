package com.example.xcpro.variometer.bluetooth.lxnav

import com.example.xcpro.variometer.bluetooth.NmeaLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LxSentenceParserTest {

    private val parser = LxSentenceParser()

    @Test
    fun sentence_id_classification_covers_supported_unsupported_and_unknown() {
        assertEquals(
            LxSentenceId.LXWP0,
            (parseAccepted("LXWP0,Y,88.4,654.1,1.12").sentence as LxWp0Sentence).sentenceId
        )
        assertEquals(
            LxSentenceId.LXWP1,
            (parseAccepted("LXWP1,S100,123,1.0,2.0").sentence as LxWp1Sentence).sentenceId
        )

        val lxwp2 = parseOutcome("LXWP2,1,1,1") as LxParseOutcome.KnownUnsupported
        val lxwp3 = parseOutcome("LXWP3,1") as LxParseOutcome.KnownUnsupported
        val plxvf = parseOutcome("PLXVF,,1.00,0.87,-0.12,-0.25,90.2,244.3") as LxParseOutcome.KnownUnsupported
        val plxvs = parseOutcome("PLXVS,23.1,0,12.3") as LxParseOutcome.KnownUnsupported
        val unknown = parseOutcome("PTEST,1,2,3") as LxParseOutcome.UnknownSentence

        assertEquals(LxSentenceId.LXWP2, lxwp2.sentenceId)
        assertEquals(LxSentenceId.LXWP3, lxwp3.sentenceId)
        assertEquals(LxSentenceId.PLXVF, plxvf.sentenceId)
        assertEquals(LxSentenceId.PLXVS, plxvs.sentenceId)
        assertEquals(LxSentenceId.UNKNOWN, unknown.sentenceId)
        assertEquals("PTEST", unknown.rawSentenceId)
    }

    @Test
    fun checksum_valid_acceptance_marks_valid() {
        val outcome = parser.parse(line(withChecksum("LXWP1,S100,123,1.0,2.0")))

        assertTrue(outcome is LxParseOutcome.Accepted)
        assertEquals(ChecksumStatus.VALID, outcome.checksumStatus)
    }

    @Test
    fun checksum_missing_acceptance_marks_missing() {
        val outcome = parser.parse(line("\$LXWP1,S100,123,1.0,2.0"))

        assertTrue(outcome is LxParseOutcome.Accepted)
        assertEquals(ChecksumStatus.MISSING, outcome.checksumStatus)
    }

    @Test
    fun checksum_invalid_rejects_sentence() {
        val outcome = parser.parse(line("\$LXWP0,Y,88.4,654.1,1.12*00"))

        assertEquals(
            LxParseOutcome.Rejected(
                reason = LxRejectedReason.INVALID_CHECKSUM,
                sentenceId = LxSentenceId.LXWP0,
                receivedMonoMs = TEST_RECEIVED_MONO_MS,
                checksumStatus = ChecksumStatus.INVALID
            ),
            outcome
        )
    }

    @Test
    fun malformed_checksum_trailer_rejects_sentence() {
        val outcome = parser.parse(line("\$LXWP0,Y,88.4,654.1,1.12*G1"))

        assertEquals(
            LxParseOutcome.Rejected(
                reason = LxRejectedReason.MALFORMED_CHECKSUM,
                sentenceId = LxSentenceId.LXWP0,
                receivedMonoMs = TEST_RECEIVED_MONO_MS,
                checksumStatus = ChecksumStatus.MALFORMED
            ),
            outcome
        )
    }

    @Test
    fun valid_lxwp0_parse_reads_supported_fields_only() {
        val outcome = parser.parse(line(withChecksum("LXWP0,Y,88.4,654.1,1.12,,,,,,090,250,12.3")))
        val sentence = (outcome as LxParseOutcome.Accepted).sentence as LxWp0Sentence

        assertEquals(88.4, sentence.airspeedKph ?: Double.NaN, 0.0)
        assertEquals(654.1, sentence.pressureAltitudeM ?: Double.NaN, 0.0)
        assertEquals(1.12, sentence.totalEnergyVarioMps ?: Double.NaN, 0.0)
        assertEquals(ChecksumStatus.VALID, sentence.checksumStatus)
    }

    @Test
    fun valid_lxwp1_parse_reads_supported_fields_only() {
        val outcome = parser.parse(line(withChecksum("LXWP1,S100,123,1.0,2.0,ignored-license")))
        val sentence = (outcome as LxParseOutcome.Accepted).sentence as LxWp1Sentence

        assertEquals(
            LxDeviceInfo(
                product = "S100",
                serial = "123",
                softwareVersion = "1.0",
                hardwareVersion = "2.0"
            ),
            sentence.deviceInfo
        )
    }

    @Test
    fun malformed_non_blank_numeric_field_rejects_whole_lxwp0() {
        val outcome = parser.parse(line("\$LXWP0,Y,not-a-number,654.1,1.12"))

        assertEquals(
            LxParseOutcome.Rejected(
                reason = LxRejectedReason.MALFORMED_FIELDS,
                sentenceId = LxSentenceId.LXWP0,
                receivedMonoMs = TEST_RECEIVED_MONO_MS,
                checksumStatus = ChecksumStatus.MISSING
            ),
            outcome
        )
    }

    @Test
    fun known_unsupported_sentences_are_classified_and_ignored_safely() {
        val outcome = parser.parse(line(withChecksum("PLXVF,,1.00,0.87,-0.12,-0.25,90.2,244.3")))

        assertEquals(
            LxParseOutcome.KnownUnsupported(
                sentenceId = LxSentenceId.PLXVF,
                receivedMonoMs = TEST_RECEIVED_MONO_MS,
                checksumStatus = ChecksumStatus.VALID
            ),
            outcome
        )
    }

    @Test
    fun unknown_sentence_is_classified_safely() {
        val outcome = parser.parse(line(withChecksum("PGRMZ,1,2,3")))

        assertEquals(LxSentenceId.UNKNOWN, outcome.sentenceId)
        assertTrue(outcome is LxParseOutcome.UnknownSentence)
        assertEquals(ChecksumStatus.VALID, outcome.checksumStatus)
    }

    @Test
    fun line_without_leading_dollar_is_rejected() {
        val outcome = parser.parse(NmeaLine("LXWP0,Y,88.4,654.1,1.12", TEST_RECEIVED_MONO_MS))

        assertEquals(
            LxParseOutcome.Rejected(
                reason = LxRejectedReason.MISSING_PREFIX,
                sentenceId = LxSentenceId.UNKNOWN,
                receivedMonoMs = TEST_RECEIVED_MONO_MS,
                checksumStatus = null
            ),
            outcome
        )
    }

    @Test
    fun blank_supported_fields_are_accepted_as_missing_values() {
        val outcome = parser.parse(line("\$LXWP0,Y,,654.1,"))
        val sentence = (outcome as LxParseOutcome.Accepted).sentence as LxWp0Sentence

        assertNull(sentence.airspeedKph)
        assertEquals(654.1, sentence.pressureAltitudeM ?: Double.NaN, 0.0)
        assertNull(sentence.totalEnergyVarioMps)
    }

    private fun parseAccepted(payloadWithoutDollar: String): LxParseOutcome.Accepted =
        parser.parse(line("\$$payloadWithoutDollar")) as LxParseOutcome.Accepted

    private fun parseOutcome(payloadWithoutDollar: String): LxParseOutcome =
        parser.parse(line("\$$payloadWithoutDollar"))

    private fun line(text: String): NmeaLine =
        NmeaLine(text = text, receivedMonoMs = TEST_RECEIVED_MONO_MS)

    private fun withChecksum(payloadWithoutDollar: String): String {
        val checksum = payloadWithoutDollar.fold(0) { acc, character -> acc xor character.code }
        return "\$$payloadWithoutDollar*${checksum.toString(16).uppercase().padStart(2, '0')}"
    }

    companion object {
        private const val TEST_RECEIVED_MONO_MS = 1_234L
    }
}
