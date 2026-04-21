package com.trust3.xcpro.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NmeaLineFramerTest {

    @Test
    fun partial_line_across_two_chunks_emits_when_newline_arrives() {
        val framer = NmeaLineFramer()

        val first = framer.append(chunk("LXWP", 100L))
        val second = framer.append(chunk("0,1*00\n", 200L))

        assertTrue(first.isEmpty())
        assertEquals(listOf(NmeaLine("LXWP0,1*00", 200L)), second)
    }

    @Test
    fun multiple_lines_in_one_chunk_preserve_order() {
        val framer = NmeaLineFramer()

        val lines = framer.append(chunk("A\nB\nC\n", 300L))

        assertEquals(
            listOf(
                NmeaLine("A", 300L),
                NmeaLine("B", 300L),
                NmeaLine("C", 300L)
            ),
            lines
        )
    }

    @Test
    fun crlf_normalization_strips_exactly_one_trailing_carriage_return() {
        val framer = NmeaLineFramer()

        val lines = framer.append(chunk("ABC\r\nDEF\r\r\n", 400L))

        assertEquals(
            listOf(
                NmeaLine("ABC", 400L),
                NmeaLine("DEF\r", 400L)
            ),
            lines
        )
    }

    @Test
    fun trailing_incomplete_fragment_is_retained_until_later_newline() {
        val framer = NmeaLineFramer()

        val first = framer.append(chunk("A\nPART", 500L))
        val second = framer.append(chunk("IAL\n", 600L))

        assertEquals(listOf(NmeaLine("A", 500L)), first)
        assertEquals(listOf(NmeaLine("PARTIAL", 600L)), second)
    }

    @Test
    fun reset_clears_partial_buffered_data_between_sessions() {
        val framer = NmeaLineFramer()

        framer.append(chunk("STALE", 700L))
        framer.reset()
        val lines = framer.append(chunk("FRESH\n", 800L))

        assertEquals(listOf(NmeaLine("FRESH", 800L)), lines)
    }

    @Test
    fun completed_line_uses_timestamp_from_chunk_that_finished_it() {
        val framer = NmeaLineFramer()

        framer.append(chunk("ABC", 900L))
        val lines = framer.append(chunk("DEF\n", 901L))

        assertEquals(listOf(NmeaLine("ABCDEF", 901L)), lines)
    }

    @Test
    fun embedded_control_characters_are_preserved_in_completed_payload() {
        val framer = NmeaLineFramer()
        val chunk = BluetoothReadChunk(
            bytes = byteArrayOf(
                'A'.code.toByte(),
                0x00,
                'B'.code.toByte(),
                '\t'.code.toByte(),
                'C'.code.toByte(),
                '\n'.code.toByte()
            ),
            receivedMonoMs = 1_000L
        )

        val lines = framer.append(chunk)

        assertEquals(listOf(NmeaLine("A\u0000B\tC", 1_000L)), lines)
    }

    @Test
    fun overflow_drops_fragment_until_next_newline_then_recovers() {
        val framer = NmeaLineFramer(maxBufferedBytes = 4)

        val first = framer.append(chunk("ABCD", 1_100L))
        val second = framer.append(chunk("EF", 1_101L))
        val third = framer.append(chunk("GH\nOK\n", 1_102L))

        assertTrue(first.isEmpty())
        assertTrue(second.isEmpty())
        assertEquals(listOf(NmeaLine("OK", 1_102L)), third)
    }

    private fun chunk(text: String, receivedMonoMs: Long): BluetoothReadChunk =
        BluetoothReadChunk(
            bytes = text.toByteArray(Charsets.US_ASCII),
            receivedMonoMs = receivedMonoMs
        )
}
