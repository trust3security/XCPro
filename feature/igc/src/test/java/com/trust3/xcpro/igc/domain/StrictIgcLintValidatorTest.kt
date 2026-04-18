package com.trust3.xcpro.igc.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictIgcLintValidatorTest {

    private val validator = StrictIgcLintValidator()

    @Test
    fun validate_acceptsCanonicalPayload() {
        val issues = validator.validate(
            IgcLintPayload(
                bytes = (
                    "AXCP000111\r\n" +
                        "HFDTEDATE:090326,01\r\n" +
                        "I023638IAS3941TAS\r\n" +
                        "B1200003746494N12225164WA0012300145123145\r\n" +
                        "B1201003746494N12225164WA0012300145124146\r\n"
                    ).toByteArray()
            )
        )

        assertTrue(issues.isEmpty())
    }

    @Test
    fun validate_rejectsNonCanonicalLineEndings() {
        val issues = validator.validate(
            IgcLintPayload(
                bytes = "AXCP000111\nB1200003746494N12225164WA0012300145\n".toByteArray()
            )
        )

        assertTrue(issues.any { it.code == IgcLintIssueCode.LINE_ENDING_NOT_CRLF })
    }

    @Test
    fun validate_rejectsARecordNotFirst() {
        val issues = validator.validate(
            IgcLintPayload(
                bytes = (
                    "HFDTEDATE:090326,01\r\n" +
                        "AXCP000111\r\n"
                    ).toByteArray()
            )
        )

        assertEquals(IgcLintIssueCode.A_RECORD_NOT_FIRST, issues.first().code)
    }

    @Test
    fun validate_rejectsNonMonotonicBRecordTimes() {
        val issues = validator.validate(
            IgcLintPayload(
                bytes = (
                    "AXCP000111\r\n" +
                        "B1201003746494N12225164WA0012300145\r\n" +
                        "B1200003746494N12225164WA0012300145\r\n"
                    ).toByteArray()
            )
        )

        assertTrue(issues.any { it.code == IgcLintIssueCode.B_RECORD_UTC_NON_MONOTONIC })
    }

    @Test
    fun validate_rejectsInvalidIRecordExtensionStartBelowWriterFloor() {
        val issues = validator.validate(
            IgcLintPayload(
                bytes = (
                    "AXCP000111\r\n" +
                        "I020838IAS3941TAS\r\n" +
                        "B1200003746494N12225164WA0012300145123145\r\n"
                    ).toByteArray()
            )
        )

        assertTrue(issues.any { it.code == IgcLintIssueCode.I_RECORD_EXTENSION_RANGE_INVALID })
    }
}
