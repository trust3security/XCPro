package com.example.xcpro.igc.domain

import java.nio.charset.StandardCharsets
import javax.inject.Inject

class StrictIgcLintValidator @Inject constructor() : IgcLintValidator {

    override fun validate(
        payload: IgcLintPayload,
        ruleSet: IgcLintRuleSet
    ): List<IgcLintIssue> {
        val issues = mutableListOf<IgcLintIssue>()
        if (ruleSet.contains(IgcLintRule.FILE_NOT_EMPTY) && payload.bytes.isEmpty()) {
            return listOf(
                IgcLintIssue(code = IgcLintIssueCode.FILE_EMPTY)
            )
        }
        if (payload.bytes.isEmpty()) return emptyList()

        val text = String(payload.bytes, StandardCharsets.UTF_8)
        val lines = splitLines(text)

        if (ruleSet.contains(IgcLintRule.CANONICAL_CRLF_LINE_ENDINGS)) {
            issues += detectLineEndingIssues(payload.bytes)
        }
        if (lines.isEmpty()) return issues

        if (ruleSet.contains(IgcLintRule.A_RECORD_FIRST) && !lines.first().startsWith("A")) {
            issues += issue(
                code = IgcLintIssueCode.A_RECORD_NOT_FIRST,
                lineNumber = 1,
                recordType = lines.first().firstOrNull()
            )
        }

        lines.forEachIndexed { index, line ->
            val lineNumber = index + 1
            when {
                ruleSet.contains(IgcLintRule.B_RECORD_NO_SPACES) &&
                    line.startsWith("B") &&
                    line.contains(' ') -> {
                    issues += issue(
                        code = IgcLintIssueCode.B_RECORD_CONTAINS_SPACE,
                        lineNumber = lineNumber,
                        recordType = 'B'
                    )
                }

                ruleSet.contains(IgcLintRule.I_RECORD_NO_SPACES) &&
                    line.startsWith("I") &&
                    line.contains(' ') -> {
                    issues += issue(
                        code = IgcLintIssueCode.I_RECORD_CONTAINS_SPACE,
                        lineNumber = lineNumber,
                        recordType = 'I'
                    )
                }
            }
        }

        if (ruleSet.contains(IgcLintRule.B_RECORD_UTC_MONOTONIC)) {
            issues += detectNonMonotonicBTimes(lines)
        }

        if (
            ruleSet.contains(IgcLintRule.I_RECORD_EXTENSION_COUNT_MATCHES_DECLARATION) ||
            ruleSet.contains(IgcLintRule.I_RECORD_EXTENSION_BYTE_RANGE_VALID) ||
            ruleSet.contains(IgcLintRule.I_RECORD_EXTENSION_BYTE_RANGE_NON_OVERLAPPING)
        ) {
            lines.forEachIndexed { index, line ->
                if (!line.startsWith("I")) return@forEachIndexed
                issues += validateIRecord(
                    line = line,
                    lineNumber = index + 1,
                    ruleSet = ruleSet
                )
            }
        }

        return issues
    }

    private fun detectLineEndingIssues(bytes: ByteArray): List<IgcLintIssue> {
        val issues = mutableListOf<IgcLintIssue>()
        var lineNumber = 1
        var index = 0
        while (index < bytes.size) {
            when (val current = bytes[index]) {
                LF -> {
                    val previous = if (index > 0) bytes[index - 1] else null
                    if (previous != CR) {
                        issues += issue(
                            code = IgcLintIssueCode.LINE_ENDING_NOT_CRLF,
                            lineNumber = lineNumber
                        )
                    }
                    lineNumber += 1
                }

                CR -> {
                    val next = if (index + 1 < bytes.size) bytes[index + 1] else null
                    if (next != LF) {
                        issues += issue(
                            code = IgcLintIssueCode.LINE_ENDING_NOT_CRLF,
                            lineNumber = lineNumber
                        )
                        lineNumber += 1
                    }
                }
            }
            index += 1
        }
        if (!(bytes.size >= 2 && bytes[bytes.lastIndex - 1] == CR && bytes.last() == LF)) {
            issues += issue(
                code = IgcLintIssueCode.LINE_ENDING_NOT_CRLF,
                lineNumber = lineNumber.coerceAtLeast(1)
            )
        }
        return issues.distinct()
    }

    private fun detectNonMonotonicBTimes(lines: List<String>): List<IgcLintIssue> {
        val issues = mutableListOf<IgcLintIssue>()
        var previousSeconds: Int? = null
        lines.forEachIndexed { index, line ->
            if (!line.startsWith("B") || line.length < B_TIME_END_INDEX) return@forEachIndexed
            val currentSeconds = parseBTimeSeconds(line) ?: return@forEachIndexed
            val previous = previousSeconds
            if (previous != null && currentSeconds < previous) {
                issues += issue(
                    code = IgcLintIssueCode.B_RECORD_UTC_NON_MONOTONIC,
                    lineNumber = index + 1,
                    recordType = 'B'
                )
            }
            previousSeconds = currentSeconds
        }
        return issues
    }

    private fun validateIRecord(
        line: String,
        lineNumber: Int,
        ruleSet: IgcLintRuleSet
    ): List<IgcLintIssue> {
        val issues = mutableListOf<IgcLintIssue>()
        if (line.length < 3) {
            return listOf(
                issue(
                    code = IgcLintIssueCode.I_RECORD_EXTENSION_COUNT_INVALID,
                    lineNumber = lineNumber,
                    recordType = 'I'
                )
            )
        }

        val count = line.substring(1, 3).toIntOrNull()
        if (count == null) {
            return listOf(
                issue(
                    code = IgcLintIssueCode.I_RECORD_EXTENSION_COUNT_INVALID,
                    lineNumber = lineNumber,
                    recordType = 'I'
                )
            )
        }

        val expectedLength = 3 + (count * I_RECORD_EXTENSION_WIDTH)
        if (
            ruleSet.contains(IgcLintRule.I_RECORD_EXTENSION_COUNT_MATCHES_DECLARATION) &&
            line.length != expectedLength
        ) {
            issues += issue(
                code = IgcLintIssueCode.I_RECORD_EXTENSION_COUNT_INVALID,
                lineNumber = lineNumber,
                recordType = 'I'
            )
        }

        val parseCount = minOf(count, ((line.length - 3).coerceAtLeast(0)) / I_RECORD_EXTENSION_WIDTH)
        var previousEnd = FIRST_B_EXTENSION_START - 1
        repeat(parseCount) { position ->
            val base = 3 + (position * I_RECORD_EXTENSION_WIDTH)
            val start = line.substring(base, base + 2).toIntOrNull()
            val end = line.substring(base + 2, base + 4).toIntOrNull()
            if (start == null || end == null) {
                issues += issue(
                    code = IgcLintIssueCode.I_RECORD_EXTENSION_RANGE_INVALID,
                    lineNumber = lineNumber,
                    recordType = 'I'
                )
                return@repeat
            }

            if (ruleSet.contains(IgcLintRule.I_RECORD_EXTENSION_BYTE_RANGE_VALID)) {
                val expectedStart = if (position == 0) FIRST_B_EXTENSION_START else previousEnd + 1
                if (start < FIRST_B_EXTENSION_START || end < start || start != expectedStart) {
                    issues += issue(
                        code = IgcLintIssueCode.I_RECORD_EXTENSION_RANGE_INVALID,
                        lineNumber = lineNumber,
                        recordType = 'I',
                        byteStart = start,
                        byteEnd = end
                    )
                }
            }

            if (
                ruleSet.contains(IgcLintRule.I_RECORD_EXTENSION_BYTE_RANGE_NON_OVERLAPPING) &&
                start <= previousEnd
            ) {
                issues += issue(
                    code = IgcLintIssueCode.I_RECORD_EXTENSION_RANGE_OVERLAP,
                    lineNumber = lineNumber,
                    recordType = 'I',
                    byteStart = start,
                    byteEnd = end
                )
            }
            previousEnd = maxOf(previousEnd, end)
        }

        return issues.distinct()
    }

    private fun splitLines(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val normalized = text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        return normalized
            .split('\n')
            .let { parts ->
                if (parts.lastOrNull().isNullOrEmpty()) parts.dropLast(1) else parts
            }
    }

    private fun parseBTimeSeconds(line: String): Int? {
        val hour = line.substring(1, 3).toIntOrNull() ?: return null
        val minute = line.substring(3, 5).toIntOrNull() ?: return null
        val second = line.substring(5, 7).toIntOrNull() ?: return null
        return (hour * 3600) + (minute * 60) + second
    }

    private fun issue(
        code: IgcLintIssueCode,
        lineNumber: Int,
        recordType: Char? = null,
        byteStart: Int? = null,
        byteEnd: Int? = null
    ): IgcLintIssue {
        return IgcLintIssue(
            code = code,
            location = IgcLintLocation(
                lineNumber = lineNumber,
                recordType = recordType,
                byteStart = byteStart,
                byteEnd = byteEnd
            )
        )
    }

    private companion object {
        private const val FIRST_B_EXTENSION_START = 36
        private const val I_RECORD_EXTENSION_WIDTH = 7
        private const val B_TIME_END_INDEX = 7
        private const val CR: Byte = '\r'.code.toByte()
        private const val LF: Byte = '\n'.code.toByte()
    }
}
