package com.trust3.xcpro.map.diagnostics

import android.content.Context
import com.trust3.xcpro.core.common.logging.AppLogger
import com.trust3.xcpro.map.BuildConfig
import java.io.File
import java.io.IOException

/**
 * Debug-only exporter for aggregate diagnostics status lines.
 *
 * The exporter writes to app-private storage so no storage permission is
 * needed. It must stay non-authoritative and must only persist compact,
 * coordinate-free diagnostics lines.
 */
internal class DebugDiagnosticsFileExporter private constructor(
    private val filesDirectoryProvider: () -> File,
    private val enabledProvider: () -> Boolean
) {
    constructor(context: Context) : this(
        filesDirectoryProvider = { context.applicationContext.filesDir },
        enabledProvider = { BuildConfig.DEBUG }
    )

    internal constructor(baseDirectory: File) : this(
        filesDirectoryProvider = { baseDirectory },
        enabledProvider = { true }
    )

    fun appendLine(statusLine: String): File? {
        if (!enabledProvider()) return null
        return try {
            val file = diagnosticsFile()
            val directory = file.parentFile
            if (directory != null && !directory.exists()) {
                directory.mkdirs()
            }
            file.appendText(statusLine + "\n", Charsets.UTF_8)
            file
        } catch (e: IOException) {
            AppLogger.w(TAG, "Failed to write diagnostics export: ${e.message}", e)
            null
        } catch (e: SecurityException) {
            AppLogger.w(TAG, "Diagnostics export blocked: ${e.message}", e)
            null
        }
    }

    fun diagnosticsFile(): File =
        File(File(filesDirectoryProvider(), DIRECTORY_NAME), FILE_NAME)

    companion object {
        const val DIRECTORY_NAME: String = "diagnostics"
        const val FILE_NAME: String = "xcpro-map-diagnostics-latest.txt"
        const val RUN_AS_RELATIVE_PATH: String = "files/diagnostics/xcpro-map-diagnostics-latest.txt"
        private const val TAG = "DebugDiagnosticsFileExporter"
    }
}
