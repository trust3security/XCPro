package com.trust3.xcpro.igc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IgcExportDiagnosticsRepositoryTest {

    @Test
    fun publish_updatesLatestDiagnostic() {
        val repository = InMemoryIgcExportDiagnosticsRepository()
        val diagnostic = IgcExportDiagnostic(
            source = IgcExportDiagnosticSource.FINALIZE,
            code = IgcExportDiagnosticCode.LINT_VALIDATION_FAILED,
            message = "A record must be first",
            sessionId = 42L,
            fileName = "flight.igc"
        )

        repository.publish(diagnostic)

        assertEquals(diagnostic, repository.latest.value)
    }

    @Test
    fun clear_resetsLatestDiagnostic() {
        val repository = InMemoryIgcExportDiagnosticsRepository()
        repository.publish(
            IgcExportDiagnostic(
                source = IgcExportDiagnosticSource.COPY_TO,
                code = IgcExportDiagnosticCode.COPY_FAILED,
                message = "open failed"
            )
        )

        repository.clear()

        assertNull(repository.latest.value)
    }
}
