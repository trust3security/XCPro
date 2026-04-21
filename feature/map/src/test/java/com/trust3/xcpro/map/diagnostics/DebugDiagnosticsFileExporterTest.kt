package com.trust3.xcpro.map.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DebugDiagnosticsFileExporterTest {
    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var exporter: DebugDiagnosticsFileExporter

    @Before
    fun setUp() {
        exporter = DebugDiagnosticsFileExporter(baseDirectory = temporaryFolder.root)
    }

    @Test
    fun appendLine_writesKnownPrivateDiagnosticsFile() {
        val file = exporter.appendLine("MAP_RENDER_SURFACE_DIAGNOSTICS reason=test")

        assertNotNull(file)
        assertEquals(DebugDiagnosticsFileExporter.FILE_NAME, file?.name)
        assertEquals(
            "MAP_RENDER_SURFACE_DIAGNOSTICS reason=test\n",
            exporter.diagnosticsFile().readText()
        )
    }

    @Test
    fun appendLine_appendsMultipleStatusLines() {
        exporter.appendLine("MAP_RENDER_SURFACE_DIAGNOSTICS reason=on_stop")
        exporter.appendLine("LIVE_GPS_CADENCE_DIAGNOSTICS reason=stop")

        assertEquals(
            "MAP_RENDER_SURFACE_DIAGNOSTICS reason=on_stop\n" +
                "LIVE_GPS_CADENCE_DIAGNOSTICS reason=stop\n",
            exporter.diagnosticsFile().readText()
        )
    }
}
