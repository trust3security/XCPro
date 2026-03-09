package com.example.xcpro.igc.data

import java.io.OutputStream
import java.nio.charset.Charset

/**
 * Deterministic byte writer for IGC records.
 *
 * Contract:
 * - UTF-8 encoding only.
 * - Canonical CRLF line endings.
 * - Reject lines containing embedded CR/LF.
 * - Non-empty output always ends with CRLF.
 */
class IgcTextWriter(
    private val charset: Charset = Charsets.UTF_8
) {

    fun writeLines(
        output: OutputStream,
        lines: Iterable<String>
    ) {
        lines.forEach { line ->
            require(line.none { it == '\r' || it == '\n' }) {
                "IGC record line must not contain CR/LF characters"
            }
            output.write(line.toByteArray(charset))
            output.write(CRLF_BYTES)
        }
    }

    fun toByteArray(lines: Iterable<String>): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        writeLines(buffer, lines)
        return buffer.toByteArray()
    }

    companion object {
        val CRLF_BYTES: ByteArray = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte())
    }
}
