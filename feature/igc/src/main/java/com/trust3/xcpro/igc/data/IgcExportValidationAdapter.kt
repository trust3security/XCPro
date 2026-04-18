package com.trust3.xcpro.igc.data

import com.trust3.xcpro.igc.domain.IgcLintIssue
import com.trust3.xcpro.igc.domain.IgcLintPayload
import com.trust3.xcpro.igc.domain.IgcLintSource
import com.trust3.xcpro.igc.domain.IgcLintValidator
import com.trust3.xcpro.igc.usecase.IgcLintMessageMapper
import javax.inject.Inject

sealed interface IgcExportValidationResult {
    data object Valid : IgcExportValidationResult

    data class Invalid(
        val message: String,
        val issues: List<IgcLintIssue>
    ) : IgcExportValidationResult
}

class IgcExportValidationAdapter @Inject constructor(
    private val lintValidator: IgcLintValidator,
    private val lintMessageMapper: IgcLintMessageMapper
) {
    fun validate(payload: ByteArray): IgcExportValidationResult {
        val issues = lintValidator.validate(
            payload = IgcLintPayload(
                bytes = payload,
                source = IgcLintSource.FINALIZE_EXPORT
            )
        )
        if (issues.isEmpty()) return IgcExportValidationResult.Valid
        return IgcExportValidationResult.Invalid(
            message = lintMessageMapper.summarize(issues),
            issues = issues
        )
    }
}
