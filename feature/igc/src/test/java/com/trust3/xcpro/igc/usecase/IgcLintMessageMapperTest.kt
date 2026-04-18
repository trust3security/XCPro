package com.trust3.xcpro.igc.usecase

import com.trust3.xcpro.igc.domain.IgcLintIssue
import com.trust3.xcpro.igc.domain.IgcLintIssueCode
import com.trust3.xcpro.igc.domain.IgcLintLocation
import org.junit.Assert.assertEquals
import org.junit.Test

class IgcLintMessageMapperTest {

    private val mapper = IgcLintMessageMapper()

    @Test
    fun messageFor_includesLineNumberWhenPresent() {
        val message = mapper.messageFor(
            IgcLintIssue(
                code = IgcLintIssueCode.A_RECORD_NOT_FIRST,
                location = IgcLintLocation(lineNumber = 2, recordType = 'B')
            )
        )

        assertEquals("IGC export failed: A record must be first (line 2)", message)
    }

    @Test
    fun summarize_returnsFallbackWhenNoIssues() {
        assertEquals(
            "IGC export failed: validation failed",
            mapper.summarize(emptyList())
        )
    }
}
