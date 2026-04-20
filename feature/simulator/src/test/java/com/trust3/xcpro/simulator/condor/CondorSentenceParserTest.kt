package com.trust3.xcpro.simulator.condor

import com.trust3.xcpro.bluetooth.NmeaLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CondorSentenceParserTest {

    private val parser = CondorSentenceParser()

    @Test
    fun lxwp0_parses_condor2_fields_and_reciprocal_wind() {
        val sentence = parser.parse(
            line(withChecksum("LXWP0,Y,222.3,1665.5,1.71,,,,,,239,174,10.1"))
        ) as CondorSentence.LxWp0

        assertEquals(222.3, sentence.airspeedKph, 0.0)
        assertEquals(1665.5, sentence.pressureAltitudeM ?: Double.NaN, 0.0)
        assertEquals(1.71, sentence.totalEnergyVarioMps ?: Double.NaN, 0.0)
        assertEquals(354.0, sentence.windDirectionFromDeg ?: Double.NaN, 0.0)
        assertEquals(2.8055555555555554, sentence.windSpeedMs ?: Double.NaN, 1e-9)
    }

    @Test
    fun lxwp0_does_not_fallback_to_logger_field_for_airspeed() {
        val sentence = parser.parse(
            line(withChecksum("LXWP0,222.3,,1665.5,1.71,,,,,,239,174,10.1"))
        )

        assertNull(sentence)
    }

    private fun line(text: String): NmeaLine =
        NmeaLine(text = text, receivedMonoMs = TEST_RECEIVED_MONO_MS)

    private fun withChecksum(payloadWithoutDollar: String): String {
        val checksum = payloadWithoutDollar.fold(0) { acc, character -> acc xor character.code }
        return "\$$payloadWithoutDollar*${checksum.toString(16).uppercase().padStart(2, '0')}"
    }

    private companion object {
        private const val TEST_RECEIVED_MONO_MS = 1_234L
    }
}
