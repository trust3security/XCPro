package com.trust3.xcpro.igc.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class IgcRecoveryStagingStore @Inject constructor(
    @ApplicationContext private val appContext: Context
) {

    fun write(sessionId: Long, payload: ByteArray): Boolean {
        val stagingDir = File(appContext.filesDir, STAGING_SUBDIR)
        if (!stagingDir.exists() && !stagingDir.mkdirs()) {
            return false
        }
        val stagingFile = fileFor(sessionId)
        return runCatching {
            FileOutputStream(stagingFile).use { stream ->
                stream.write(payload)
                stream.flush()
            }
        }.isSuccess
    }

    fun exists(sessionId: Long): Boolean = fileFor(sessionId).exists()

    fun readLines(sessionId: Long): List<String>? {
        val stagingFile = fileFor(sessionId)
        if (!stagingFile.exists()) return null
        return runCatching { stagingFile.readLines() }.getOrNull()
    }

    fun delete(sessionId: Long) {
        runCatching { fileFor(sessionId).delete() }
    }

    private fun fileFor(sessionId: Long): File =
        File(File(appContext.filesDir, STAGING_SUBDIR), "session_${sessionId}.igc.tmp")

    private companion object {
        private const val STAGING_SUBDIR = "igc/staging"
    }
}
