package com.trust3.xcpro.bluetooth

/**
 * Pure ASCII line framer for newline-delimited sentence streams.
 *
 * Responsibilities:
 * - buffer partial input across chunks
 * - split on '\n'
 * - strip exactly one trailing '\r' from each completed line
 * - attach the timestamp from the chunk that completed the line
 */
class NmeaLineFramer(
    private val maxBufferedBytes: Int = DEFAULT_MAX_BUFFERED_BYTES
) {
    private val buffer = StringBuilder()
    private var discardingUntilNewline = false

    init {
        require(maxBufferedBytes > 0) { "maxBufferedBytes must be > 0" }
    }

    fun append(chunk: BluetoothReadChunk): List<NmeaLine> {
        if (chunk.bytes.isEmpty()) return emptyList()

        val decoded = String(chunk.bytes, Charsets.US_ASCII)
        val completedLines = ArrayList<NmeaLine>()

        for (character in decoded) {
            if (discardingUntilNewline) {
                if (character == '\n') {
                    discardingUntilNewline = false
                    buffer.setLength(0)
                }
                continue
            }

            if (character == '\n') {
                completedLines += completeBufferedLine(receivedMonoMs = chunk.receivedMonoMs)
                continue
            }

            buffer.append(character)
            if (buffer.length > maxBufferedBytes) {
                buffer.setLength(0)
                discardingUntilNewline = true
            }
        }

        return completedLines
    }

    fun reset() {
        buffer.setLength(0)
        discardingUntilNewline = false
    }

    private fun completeBufferedLine(receivedMonoMs: Long): NmeaLine {
        val text = if (buffer.isNotEmpty() && buffer[buffer.length - 1] == '\r') {
            buffer.substring(0, buffer.length - 1)
        } else {
            buffer.toString()
        }
        buffer.setLength(0)
        return NmeaLine(text = text, receivedMonoMs = receivedMonoMs)
    }

    companion object {
        const val DEFAULT_MAX_BUFFERED_BYTES: Int = 4 * 1024
    }
}
