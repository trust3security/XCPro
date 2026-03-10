package com.example.xcpro.igc.domain

import javax.inject.Inject

/**
 * Deterministic signer for validator-backed IGC `G` record profiles.
 *
 * The current production profile targets the `XCS` compatibility algorithm so
 * generated files can be validated by ecosystems that accept that recorder code.
 */
class IgcGRecordSigner @Inject constructor() {

    fun sign(
        lines: List<String>,
        profile: IgcSecuritySignatureProfile
    ): List<String> {
        val unsignedLines = stripExistingGRecords(lines)
        return when (profile) {
            IgcSecuritySignatureProfile.NONE -> unsignedLines
            IgcSecuritySignatureProfile.XCS -> unsignedLines + buildXcsSignatureLines(unsignedLines)
        }
    }

    fun signatureLines(
        lines: List<String>,
        profile: IgcSecuritySignatureProfile
    ): List<String> {
        val unsignedLines = stripExistingGRecords(lines)
        return when (profile) {
            IgcSecuritySignatureProfile.NONE -> emptyList()
            IgcSecuritySignatureProfile.XCS -> buildXcsSignatureLines(unsignedLines)
        }
    }

    private fun stripExistingGRecords(lines: List<String>): List<String> {
        return lines.filterNot { it.startsWith("G") }
    }

    private fun buildXcsSignatureLines(lines: List<String>): List<String> {
        val digests = XCS_KEYS.map { key -> XcsMd5Digest(key) }
        var ignoreComma = true

        lines.forEach { line ->
            if (!includeRecordInXcsDigest(line)) return@forEach
            if (shouldDisableLegacyCommaWorkaround(line)) {
                ignoreComma = false
            }
            line.forEach { ch ->
                if (ignoreComma && ch == ',') return@forEach
                if (!isValidIgcChar(ch)) return@forEach
                digests.forEach { digest -> digest.appendByte(ch.code) }
            }
        }

        digests.forEach(XcsMd5Digest::finalizeDigest)
        val hex = buildString {
            digests.forEach { append(it.digestHex()) }
        }
        return hex.chunked(XCS_SIGNATURE_CHARS_PER_LINE).map { chunk -> "G$chunk" }
    }

    private fun includeRecordInXcsDigest(line: String): Boolean {
        if (line.isEmpty()) return false
        return when (line.first()) {
            'L' -> line.drop(1).startsWith(XCS_L_RECORD_SOURCE)
            'G' -> false
            'H' -> !line.drop(1).startsWith(H_OP_PREFIX)
            else -> true
        }
    }

    private fun shouldDisableLegacyCommaWorkaround(line: String): Boolean {
        return line.startsWith(H_FTY_PREFIX) &&
            line.contains(',') &&
            line.contains(LEGACY_65_MARKER)
    }

    private fun isValidIgcChar(ch: Char): Boolean {
        return ch.code in MIN_IGC_CHAR_CODE..MAX_IGC_CHAR_CODE &&
            ch !in RESERVED_IGC_CHARS
    }

    private class XcsMd5Digest(
        key: IntArray
    ) {
        private val buffer = ByteArray(MD5_BLOCK_SIZE)
        private var a = key[0]
        private var b = key[1]
        private var c = key[2]
        private var d = key[3]
        private var messageLength: Long = 0L

        fun appendByte(value: Int) {
            val position = (messageLength % MD5_BLOCK_SIZE).toInt()
            buffer[position] = value.toByte()
            messageLength += 1L
            if (position == MD5_BLOCK_SIZE - 1) {
                processBlock()
            }
        }

        fun finalizeDigest() {
            val bufferLeftOver = (messageLength % MD5_BLOCK_SIZE).toInt()
            if (bufferLeftOver < MD5_LENGTH_APPEND_START) {
                buffer[bufferLeftOver] = PAD_START_BYTE
                zeroRange(bufferLeftOver + 1, MD5_BLOCK_SIZE)
            } else {
                buffer[bufferLeftOver] = PAD_START_BYTE
                zeroRange(bufferLeftOver + 1, MD5_BLOCK_SIZE)
                processBlock()
                zeroRange(0, MD5_BLOCK_SIZE)
            }

            val bitLength = messageLength * 8L
            val bitLengthBytes = bitLength.toLittleEndianBytes()
            for (index in bitLengthBytes.indices) {
                buffer[MD5_LENGTH_APPEND_START + index] = bitLengthBytes[index]
            }
            processBlock()
        }

        fun digestHex(): String {
            return buildString(MD5_DIGEST_HEX_LENGTH) {
                append(a.toLittleEndianHex())
                append(b.toLittleEndianHex())
                append(c.toLittleEndianHex())
                append(d.toLittleEndianHex())
            }
        }

        private fun processBlock() {
            val words = IntArray(MD5_WORD_COUNT)
            for (index in 0 until MD5_WORD_COUNT) {
                val offset = index * MD5_WORD_SIZE
                words[index] = (buffer[offset].toInt() and 0xFF) or
                    ((buffer[offset + 1].toInt() and 0xFF) shl 8) or
                    ((buffer[offset + 2].toInt() and 0xFF) shl 16) or
                    ((buffer[offset + 3].toInt() and 0xFF) shl 24)
            }

            var localA = a
            var localB = b
            var localC = c
            var localD = d

            for (index in 0 until MD5_ROUND_COUNT) {
                val f: Int
                val g: Int
                when {
                    index <= 15 -> {
                        f = (localB and localC) or (localB.inv() and localD)
                        g = index
                    }
                    index <= 31 -> {
                        f = (localD and localB) or (localD.inv() and localC)
                        g = (5 * index + 1) % MD5_WORD_COUNT
                    }
                    index <= 47 -> {
                        f = localB xor localC xor localD
                        g = (3 * index + 5) % MD5_WORD_COUNT
                    }
                    else -> {
                        f = localC xor (localB or localD.inv())
                        g = (7 * index) % MD5_WORD_COUNT
                    }
                }

                val temp = localD
                localD = localC
                localC = localB
                val sum = localA + f + MD5_K[index] + words[g]
                localB += rotateLeft(sum, MD5_R[index])
                localA = temp
            }

            a += localA
            b += localB
            c += localC
            d += localD
        }

        private fun zeroRange(startIndex: Int, endExclusive: Int) {
            for (index in startIndex until endExclusive) {
                buffer[index] = 0
            }
        }

        private fun rotateLeft(value: Int, shift: Int): Int {
            return (value shl shift) or (value ushr (INT_BITS - shift))
        }

        private fun Long.toLittleEndianBytes(): ByteArray {
            val bytes = ByteArray(LONG_BYTES)
            for (index in 0 until LONG_BYTES) {
                bytes[index] = ((this ushr (index * BYTE_BITS)) and 0xFF).toByte()
            }
            return bytes
        }

        private fun Int.toLittleEndianHex(): String {
            return buildString(INT_HEX_LENGTH) {
                repeat(INT_BYTES) { byteIndex ->
                    val component = (this@toLittleEndianHex ushr (byteIndex * BYTE_BITS)) and 0xFF
                    append(component.toString(HEX_RADIX).padStart(2, '0'))
                }
            }
        }
    }

    private companion object {
        private val XCS_KEYS = listOf(
            intArrayOf(0x1C80A301, 0x9EB30B89.toInt(), 0x39CB2AFE, 0x0D0FEA76),
            intArrayOf(0x48327203, 0x3948EBEA, 0x9A9B9C9E.toInt(), 0xB3BED89A.toInt()),
            intArrayOf(0x67452301, 0xEFCDAB89.toInt(), 0x98BADCFE.toInt(), 0x10325476),
            intArrayOf(0xC8E899E8.toInt(), 0x9321C28A.toInt(), 0x438EBA12, 0x8CBE0AEE.toInt())
        )
        private val RESERVED_IGC_CHARS = setOf('$', '*', '!', '\\', '^', '~')

        private const val XCS_L_RECORD_SOURCE = "XCS"
        private const val H_OP_PREFIX = "OP"
        private const val H_FTY_PREFIX = "HFFTYFRTYPE:"
        private const val LEGACY_65_MARKER = " 6.5 "
        private const val XCS_SIGNATURE_CHARS_PER_LINE = 16

        private const val MD5_BLOCK_SIZE = 64
        private const val MD5_WORD_COUNT = 16
        private const val MD5_WORD_SIZE = 4
        private const val MD5_ROUND_COUNT = 64
        private const val MD5_LENGTH_APPEND_START = 56
        private const val MD5_DIGEST_HEX_LENGTH = 32

        private const val PAD_START_BYTE: Byte = 0x80.toByte()
        private const val MIN_IGC_CHAR_CODE = 0x20
        private const val MAX_IGC_CHAR_CODE = 0x7E
        private const val INT_BITS = 32
        private const val BYTE_BITS = 8
        private const val INT_BYTES = 4
        private const val LONG_BYTES = 8
        private const val INT_HEX_LENGTH = 8
        private const val HEX_RADIX = 16

        private val MD5_K = intArrayOf(
            3614090360L.toInt(), 3905402710L.toInt(), 606105819, 3250441966L.toInt(),
            4118548399L.toInt(), 1200080426, 2821735955L.toInt(), 4249261313L.toInt(),
            1770035416, 2336552879L.toInt(), 4294925233L.toInt(), 2304563134L.toInt(),
            1804603682, 4254626195L.toInt(), 2792965006L.toInt(), 1236535329,
            4129170786L.toInt(), 3225465664L.toInt(), 643717713, 3921069994L.toInt(),
            3593408605L.toInt(), 38016083, 3634488961L.toInt(), 3889429448L.toInt(),
            568446438, 3275163606L.toInt(), 4107603335L.toInt(), 1163531501,
            2850285829L.toInt(), 4243563512L.toInt(), 1735328473, 2368359562L.toInt(),
            4294588738L.toInt(), 2272392833L.toInt(), 1839030562, 4259657740L.toInt(),
            2763975236L.toInt(), 1272893353, 4139469664L.toInt(), 3200236656L.toInt(),
            681279174, 3936430074L.toInt(), 3572445317L.toInt(), 76029189,
            3654602809L.toInt(), 3873151461L.toInt(), 530742520, 3299628645L.toInt(),
            4096336452L.toInt(), 1126891415, 2878612391L.toInt(), 4237533241L.toInt(),
            1700485571, 2399980690L.toInt(), 4293915773L.toInt(), 2240044497L.toInt(),
            1873313359, 4264355552L.toInt(), 2734768916L.toInt(), 1309151649,
            4149444226L.toInt(), 3174756917L.toInt(), 718787259, 3951481745L.toInt()
        )

        private val MD5_R = intArrayOf(
            7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
            5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
            4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
            6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21
        )
    }
}
