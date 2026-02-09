package com.example.xcpro.ogn

import android.content.Context
import com.example.xcpro.common.di.IoDispatcher
import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.core.time.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class OgnDdbRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val identitiesByDeviceId = ConcurrentHashMap<String, OgnTrafficIdentity>()

    @Volatile
    private var lastUpdateWallMs: Long = 0L

    suspend fun refreshIfNeeded(force: Boolean = false) {
        withContext(ioDispatcher) {
            loadFromDiskIfNeeded()
            if (!force && !isRefreshDue()) return@withContext
            val json = downloadDdbJson() ?: return@withContext
            val parsed = runCatching { OgnDdbJsonParser.parse(json) }
                .onFailure { AppLogger.w(TAG, "Failed to parse OGN DDB JSON: ${it.message}") }
                .getOrNull()
                ?: return@withContext

            if (parsed.isEmpty()) return@withContext
            identitiesByDeviceId.clear()
            identitiesByDeviceId.putAll(parsed)
            lastUpdateWallMs = clock.nowWallMs()
            writeCache(json, lastUpdateWallMs)
        }
    }

    fun lookup(deviceIdHex: String): OgnTrafficIdentity? {
        val normalized = deviceIdHex.trim().uppercase(Locale.US)
        if (normalized.length != 6) return null
        return identitiesByDeviceId[normalized]
    }

    fun lastUpdateWallMs(): Long = lastUpdateWallMs

    private fun loadFromDiskIfNeeded() {
        if (identitiesByDeviceId.isNotEmpty()) return
        val cacheFile = cacheFile()
        if (!cacheFile.exists()) return
        val json = runCatching { cacheFile.readText(StandardCharsets.UTF_8) }.getOrNull() ?: return
        val parsed = runCatching { OgnDdbJsonParser.parse(json) }.getOrNull() ?: return
        identitiesByDeviceId.putAll(parsed)
        lastUpdateWallMs = readTimestampFile()
    }

    private fun isRefreshDue(): Boolean {
        val nowWall = clock.nowWallMs()
        if (lastUpdateWallMs <= 0L) return true
        return nowWall - lastUpdateWallMs >= DDB_REFRESH_INTERVAL_MS
    }

    private fun downloadDdbJson(): String? {
        return runCatching {
            val connection = (URL(DDB_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = HTTP_TIMEOUT_MS
                readTimeout = HTTP_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
            }
            try {
                connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                    reader.readText()
                }
            } finally {
                connection.disconnect()
            }
        }.onFailure {
            AppLogger.w(TAG, "Failed to download OGN DDB: ${it.message}")
        }.getOrNull()
    }

    private fun writeCache(json: String, timestampMs: Long) {
        runCatching {
            val folder = cacheFolder()
            if (!folder.exists()) {
                folder.mkdirs()
            }
            cacheFile().writeText(json, StandardCharsets.UTF_8)
            timestampFile().writeText(timestampMs.toString(), StandardCharsets.UTF_8)
        }.onFailure {
            AppLogger.w(TAG, "Failed to persist OGN DDB cache: ${it.message}")
        }
    }

    private fun readTimestampFile(): Long {
        return runCatching {
            val file = timestampFile()
            if (!file.exists()) return@runCatching 0L
            file.readText(StandardCharsets.UTF_8).trim().toLongOrNull() ?: 0L
        }.getOrDefault(0L)
    }

    private fun cacheFolder(): File = File(context.filesDir, CACHE_FOLDER_NAME)

    private fun cacheFile(): File = File(cacheFolder(), CACHE_JSON_FILE_NAME)

    private fun timestampFile(): File = File(cacheFolder(), CACHE_TIMESTAMP_FILE_NAME)

    private companion object {
        private const val TAG = "OgnDdbRepository"
        private const val DDB_URL = "https://ddb.glidernet.org/download/?j=1&t=1"
        private const val USER_AGENT = "XCPro OGN DDB"
        private const val HTTP_TIMEOUT_MS = 20_000
        private const val DDB_REFRESH_INTERVAL_MS = 24L * 60L * 60L * 1000L
        private const val CACHE_FOLDER_NAME = "ogn-ddb"
        private const val CACHE_JSON_FILE_NAME = "ddb.json"
        private const val CACHE_TIMESTAMP_FILE_NAME = "ddb.timestamp"
    }
}
