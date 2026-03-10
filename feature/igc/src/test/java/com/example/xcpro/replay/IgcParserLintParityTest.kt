package com.example.xcpro.replay

import com.example.xcpro.core.time.FakeClock
import com.example.xcpro.igc.domain.IgcLintIssueCode
import com.example.xcpro.igc.domain.IgcLintPayload
import com.example.xcpro.igc.domain.StrictIgcLintValidator
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IgcParserLintParityTest {

    private val parser = IgcParser(FakeClock(wallMs = 0L))
    private val validator = StrictIgcLintValidator()

    @Test
    fun invalidIRecord_isRejectedByLint_andIgnoredByParserExtensions() {
        val bytes = (
            "AXCP000111\r\n" +
                "HFDTE071225\r\n" +
                "I020838IAS3941TAS\r\n" +
                "B1200003745123N12230123EA0123401234123145\r\n"
            ).toByteArray()

        val issues = validator.validate(IgcLintPayload(bytes))
        val log = parser.parse(ByteArrayInputStream(bytes))

        assertTrue(issues.any { it.code == IgcLintIssueCode.I_RECORD_EXTENSION_RANGE_INVALID })
        assertEquals(1, log.points.size)
        assertEquals(null, log.points.first().indicatedAirspeedKmh)
    }

    @Test
    fun aRecordNotFirst_isRejectedByLint_evenWhenParserCanStillReadPoints() {
        val bytes = (
            "HFDTE071225\r\n" +
                "AXCP000111\r\n" +
                "B1200003745123N12230123EA0123401234\r\n"
            ).toByteArray()

        val issues = validator.validate(IgcLintPayload(bytes))
        val log = parser.parse(ByteArrayInputStream(bytes))

        assertTrue(issues.any { it.code == IgcLintIssueCode.A_RECORD_NOT_FIRST })
        assertEquals(1, log.points.size)
    }
}
