package com.trust3.xcpro.igc.data

import com.trust3.xcpro.igc.domain.IgcLintIssue
import com.trust3.xcpro.igc.domain.IgcLintIssueCode
import com.trust3.xcpro.igc.domain.IgcLintPayload
import com.trust3.xcpro.igc.domain.IgcLintValidator
import com.trust3.xcpro.igc.usecase.IgcLintMessageMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IgcExportValidationAdapterTest {

    @Test
    fun validate_returnsInvalidWithMappedMessage() {
        val adapter = IgcExportValidationAdapter(
            lintValidator = object : IgcLintValidator {
                override fun validate(payload: IgcLintPayload, ruleSet: com.trust3.xcpro.igc.domain.IgcLintRuleSet): List<IgcLintIssue> {
                    return listOf(IgcLintIssue(code = IgcLintIssueCode.FILE_EMPTY))
                }
            },
            lintMessageMapper = IgcLintMessageMapper()
        )

        val result = adapter.validate(ByteArray(0))

        require(result is IgcExportValidationResult.Invalid)
        assertEquals("IGC export failed: file is empty", result.message)
        assertEquals(IgcLintIssueCode.FILE_EMPTY, result.issues.single().code)
    }

    @Test
    fun validate_returnsValidWhenNoIssues() {
        val adapter = IgcExportValidationAdapter(
            lintValidator = object : IgcLintValidator {
                override fun validate(payload: IgcLintPayload, ruleSet: com.trust3.xcpro.igc.domain.IgcLintRuleSet): List<IgcLintIssue> {
                    return emptyList()
                }
            },
            lintMessageMapper = IgcLintMessageMapper()
        )

        val result = adapter.validate("AXCP000111\r\n".toByteArray())

        assertTrue(result is IgcExportValidationResult.Valid)
    }
}
